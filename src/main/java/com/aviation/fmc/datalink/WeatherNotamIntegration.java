package com.aviation.fmc.datalink;

import com.aviation.fmc.contracts.FmcSystem;
import com.aviation.fmc.contracts.GuidanceComputer;
import com.aviation.fmc.contracts.NavigationComputer;
import com.aviation.fmc.parser.FlightPlan;

import java.util.List;

/**
 * Demonstrates how Weather and NOTAM data integrates with FMC.
 * Shows real-world data flow from ground systems to cockpit.
 */
public class WeatherNotamIntegration {

    private final FmcSystem fmc;
    private final DatalinkManager datalink;
    private final WeatherService weatherService;
    private final NotamService notamService;

    public WeatherNotamIntegration(
            FmcSystem fmc,
            DatalinkManager datalink,
            WeatherService weatherService,
            NotamService notamService) {
        this.fmc = fmc;
        this.datalink = datalink;
        this.weatherService = weatherService;
        this.notamService = notamService;
    }

    /**
     * Pre-flight briefing: Collect all weather and NOTAMs for flight.
     * This is what dispatchers do before sending to aircraft.
     */
    public void performPreflightBriefing() {
        FlightPlan flightPlan = fmc.getActiveFlightPlan().orElse(null);
        if (flightPlan == null) return;

        String dep = flightPlan.getDeparture().getIcaoCode();
        String dest = flightPlan.getDestination().getIcaoCode();

        System.out.println("=== Pre-Flight Briefing ===\n");

        // 1. Get METAR/TAF for departure and destination
        System.out.println("1. Weather Data:");
        collectAirportWeather(dep);
        collectAirportWeather(dest);

        // 2. Get NOTAMs for all relevant airports
        System.out.println("\n2. NOTAMs:");
        NotamService.RouteNotams routeNotams = notamService.getNotamsForRoute(
            dep,
            dest,
            flightPlan.getAlternates().stream().map(a -> a.getIcaoCode()).toList(),
            flightPlan.getRoute().stream()
                .map(r -> r.getWaypoint() != null ? r.getWaypoint().getIdentifier() : null)
                .filter(w -> w != null)
                .toList()
        );

        displayNotams(routeNotams);

        // 3. Check for critical issues
        System.out.println("\n3. Critical Issues Check:");
        checkCriticalNotams(routeNotams, flightPlan);

        // 4. Get winds aloft for performance
        System.out.println("\n4. Winds Aloft:");
        collectWindsAloft(flightPlan);

        // 5. Get SIGMETs along route
        System.out.println("\n5. SIGMETs:");
        collectSigmets(flightPlan);
    }

    private void collectAirportWeather(String icao) {
        // METAR
        weatherService.getMetar(icao).ifPresent(metar -> {
            System.out.printf("  %s METAR: %s%n", icao, metar.rawText());
            System.out.printf("    Wind: %d°/%dkt, Vis: %dSM, Clouds: %dft%n",
                metar.windDirection(), metar.windSpeed(),
                metar.visibility(), metar.cloudBase());
            System.out.printf("    Flight Category: %s%n", metar.getFlightCategory());
        });

        // TAF
        weatherService.getTaf(icao).ifPresent(taf -> {
            System.out.printf("  %s TAF: Valid %s to %s%n",
                icao, taf.validFrom(), taf.validTo());
        });
    }

    private void displayNotams(NotamService.RouteNotams notams) {
        System.out.printf("  Departure (%s): %d NOTAMs%n",
            notams.departure(), notams.departureNotams().size());
        System.out.printf("  Destination (%s): %d NOTAMs%n",
            notams.destination(), notams.destinationNotams().size());
        System.out.printf("  Enroute: %d NOTAMs%n",
            notams.enrouteNotams().size());
        System.out.printf("  FMC Relevant: %d NOTAMs%n",
            notams.fmcRelevantNotams().size());

        // Show FMC-relevant NOTAMs
        for (NotamService.NotamReport notam : notams.fmcRelevantNotams()) {
            System.out.printf("    [%s] %s: %s%n",
                notam.getSeverity(),
                notam.keyword(),
                notam.condition());
        }
    }

    private void checkCriticalNotams(NotamService.RouteNotams notams, FlightPlan flightPlan) {
        List<NotamService.NotamReport> critical = notams.getCriticalNotams();

        if (critical.isEmpty()) {
            System.out.println("  No critical NOTAMs affecting flight.");
            return;
        }

        System.out.println("  ⚠️  CRITICAL NOTAMs DETECTED:");

        for (NotamService.NotamReport notam : critical) {
            System.out.printf("    - %s: %s%n", notam.keyword(), notam.condition());

            // Check runway closure
            if (notam.keyword().equals("RWY") && flightPlan.getDepartureRunway() != null) {
                if (notams.isRunwayClosed(flightPlan.getDepartureRunway())) {
                    System.out.printf("      ⚠️  DEPARTURE RUNWAY %s IS CLOSED!%n",
                        flightPlan.getDepartureRunway());
                    // FMC would alert pilot, suggest alternate runway
                }
            }

            // Check nav aid outage
            if (notam.keyword().equals("NAV")) {
                System.out.println("      ⚠️  Navigation aid outage - check approach availability");
                // FMC would flag affected approaches
            }

            // Check SID/STAR changes
            if (notam.keyword().equals("SID") && flightPlan.getSid() != null) {
                if (notam.condition().contains(flightPlan.getSid())) {
                    System.out.printf("      ⚠️  SID %s has changes!%n", flightPlan.getSid());
                }
            }
        }
    }

    private void collectWindsAloft(FlightPlan flightPlan) {
        // Request winds for common cruise levels
        List<Integer> flightLevels = List.of(300, 320, 340, 360, 380, 400);

        WeatherService.WindsAloft winds = weatherService.getWindsAloft(
            flightPlan.getRoute().stream()
                .map(r -> r.getWaypoint() != null ? r.getWaypoint().getIdentifier() : null)
                .filter(w -> w != null)
                .toList(),
            flightLevels
        );

        System.out.printf("  Winds valid at: %s%n", winds.validTime());
        for (WeatherService.WindsAloft.WindLevel level : winds.windLevels()) {
            System.out.printf("    FL%d: %d°/%dkt, Temp: %d°C%n",
                level.flightLevel(),
                level.windDirection(),
                level.windSpeed(),
                level.temperature());
        }

        // Send to FMC for performance calculations
        // This would update TOD, fuel predictions, etc.
    }

    private void collectSigmets(FlightPlan flightPlan) {
        // Get FIR codes along route (simplified)
        String firCode = "KZBW"; // Example: Boston FIR

        List<WeatherService.SigmetReport> sigmets = weatherService.getSigmets(firCode);

        if (sigmets.isEmpty()) {
            System.out.println("  No SIGMETs in effect.");
            return;
        }

        for (WeatherService.SigmetReport sigmet : sigmets) {
            System.out.printf("  ⚠️  SIGMET %s: %s - %s%n",
                sigmet.type(),
                sigmet.phenomenon(),
                sigmet.rawText().substring(0, Math.min(50, sigmet.rawText().length())));

            // FMC would check if route intersects SIGMET area
            // If so, suggest route deviation
        }
    }

    /**
     * Enroute weather updates.
     * Aircraft automatically requests updates during flight.
     */
    public void performEnrouteUpdate() {
        System.out.println("\n=== Enroute Weather Update ===\n");

        FlightPlan flightPlan = fmc.getActiveFlightPlan().orElse(null);
        if (flightPlan == null) return;

        // Request weather for destination (most critical)
        datalink.requestWeather(flightPlan.getDestination().getIcaoCode());

        // Check for new SIGMETs along remaining route
        NavigationComputer nav = fmc.getNavigationComputer();
        String nextWaypoint = nav.getActiveWaypoint()
            .map(w -> w.getIdentifier())
            .orElse("");

        System.out.printf("Requesting weather for destination and waypoint %s%n", nextWaypoint);

        // Request NOTAM update for destination
        datalink.requestNotamUpdate(flightPlan.getDestination().getIcaoCode());
    }

    /**
     * Handle weather uplink received via datalink.
     */
    public void handleWeatherUplink(DatalinkManager.WeatherReport report) {
        System.out.printf("Weather uplink received for %s%n", report.airport());
        System.out.printf("  METAR: %s%n", report.metar());

        // Parse and display on FMC
        // If conditions deteriorated, alert pilot
    }

    /**
     * Handle NOTAM uplink received via datalink.
     */
    public void handleNotamUplink(List<NotamService.NotamReport> notams) {
        System.out.printf("NOTAM uplink received: %d NOTAMs%n", notams.size());

        for (NotamService.NotamReport notam : notams) {
            if (notam.fmcRelevant()) {
                System.out.printf("  FMC Alert: [%s] %s%n",
                    notam.keyword(), notam.condition());

                // Display on FMC CDU
                // If critical, require acknowledgment
            }
        }
    }

    /**
     * Example: Wind data impact on FMC calculations.
     */
    public void demonstrateWindImpact() {
        System.out.println("\n=== Wind Data Impact on FMC ===\n");

        // Scenario: Strong headwinds at cruise altitude
        System.out.println("Scenario: Headwinds 270°/120kt at FL350");

        // FMC recalculations:
        System.out.println("FMC Actions:");
        System.out.println("  1. Recalculate Top of Descent (TOD)");
        System.out.println("  2. Update fuel predictions (+500 kg)");
        System.out.println("  3. Adjust ETA (+8 minutes)");
        System.out.println("  4. Check alternate fuel requirements");

        // If winds are favorable at different altitude:
        System.out.println("\n  5. Suggest optimum altitude: FL380 (tailwind component)");
        System.out.println("  6. Request climb clearance via CPDLC");
    }
}
