package com.aviation.fmc.datalink;

import com.aviation.fmc.contracts.FmcSystem;
import com.aviation.fmc.contracts.PerformanceComputer;
import com.aviation.fmc.parser.FlightPlan;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * WeatherNotamManager integrates WeatherService and NotamService with the FMC.
 * 
 * This is the central coordinator for aviation data services, providing:
 * - Unified access to weather and NOTAM data
 * - Pub/sub integration for real-time updates
 * - FMC-specific data flows (winds → PerformanceComputer, NOTAMs → alerts)
 * 
 * Similar to how DatalinkManager coordinates ACARS, this manager coordinates
 * weather and NOTAM data flows into the flight management system.
 */
@Slf4j
public class WeatherNotamManager {

    private final WeatherService weatherService;
    private final NotamService notamService;
    private final FmcSystem fmcSystem;

    private volatile boolean initialized = false;

    public WeatherNotamManager(WeatherService weatherService,
                               NotamService notamService,
                               FmcSystem fmcSystem) {
        this.weatherService = weatherService;
        this.notamService = notamService;
        this.fmcSystem = fmcSystem;
    }

    /**
     * Initialize the manager and start services.
     */
    public void initialize() {
        if (initialized) return;

        if (weatherService instanceof WeatherServiceImpl ws) {
            ws.start();
        }
        if (notamService instanceof NotamServiceImpl ns) {
            ns.start();
        }

        initialized = true;
        log.info("WeatherNotamManager initialized - weather and NOTAM services active");
    }

    /**
     * Shutdown services.
     */
    public void shutdown() {
        if (weatherService instanceof WeatherServiceImpl ws) {
            ws.stop();
        }
        if (notamService instanceof NotamServiceImpl ns) {
            ns.stop();
        }
        initialized = false;
        log.info("WeatherNotamManager shutdown complete");
    }

    // ==================== Weather Access ====================

    /**
     * Get current METAR for an airport.
     */
    public Optional<WeatherService.MetarReport> getMetar(String icaoCode) {
        return weatherService.getMetar(icaoCode);
    }

    /**
     * Get TAF for an airport.
     */
    public Optional<WeatherService.TafReport> getTaf(String icaoCode) {
        return weatherService.getTaf(icaoCode);
    }

    /**
     * Get winds aloft for current flight plan route.
     */
    public WeatherService.WindsAloft getWindsAloftForFlightPlan() {
        FlightPlan fp = fmcSystem.getActiveFlightPlan().orElse(null);
        if (fp == null) {
            return weatherService.getWindsAloft(
                List.of("KJFK", "KBOS"),
                List.of(300, 340, 380)
            );
        }

        List<String> waypoints = fp.getRoute().stream()
            .map(r -> r.getWaypoint() != null ? r.getWaypoint().getIdentifier() : null)
            .filter(w -> w != null)
            .toList();

        if (waypoints.isEmpty()) {
            waypoints = List.of(fp.getDeparture().getIcaoCode(), fp.getDestination().getIcaoCode());
        }

        List<Integer> levels = List.of(300, 340, 380);
        return weatherService.getWindsAloft(waypoints, levels);
    }

    /**
     * Request weather update for an airport (triggers pub/sub).
     */
    public void requestWeatherUpdate(String icaoCode) {
        weatherService.requestWeatherUpdate(icaoCode);
    }

    // ==================== NOTAM Access ====================

    /**
     * Get NOTAMs for an airport.
     */
    public List<NotamService.NotamReport> getNotamsForAirport(String icaoCode) {
        return notamService.getNotamsForAirport(icaoCode);
    }

    /**
     * Get NOTAMs for current flight plan route.
     */
    public NotamService.RouteNotams getNotamsForCurrentRoute() {
        FlightPlan fp = fmcSystem.getActiveFlightPlan().orElse(null);
        if (fp == null) {
            return new NotamService.RouteNotams(
                "KJFK", "KBOS",
                List.of(), List.of(), List.of(), List.of(), List.of()
            );
        }

        List<String> alternates = fp.getAlternates().stream()
            .map(a -> a.getIcaoCode())
            .toList();

        List<String> waypoints = fp.getRoute().stream()
            .map(r -> r.getWaypoint() != null ? r.getWaypoint().getIdentifier() : null)
            .filter(w -> w != null)
            .toList();

        return notamService.getNotamsForRoute(
            fp.getDeparture().getIcaoCode(),
            fp.getDestination().getIcaoCode(),
            alternates,
            waypoints
        );
    }

    /**
     * Request NOTAM update for an airport (triggers pub/sub).
     */
    public void requestNotamUpdate(String icaoCode) {
        notamService.requestNotamUpdate(icaoCode);
    }

    // ==================== FMC Integration (Pub/Sub Wiring) ====================

    /**
     * Subscribe to winds updates for FMC PerformanceComputer.
     * When winds change, PerformanceComputer receives updated winds data.
     */
    public void subscribeWindsToPerformanceComputer(PerformanceComputer perfComputer) {
        if (weatherService instanceof WeatherServiceImpl ws) {
            ws.subscribeToWinds(winds -> {
                log.debug("Winds update received for FMC PerformanceComputer: {}", winds.routeId());
                // In real system, PerformanceComputer would have setWindsAloft method
                // For now, we log - the integration can be extended
            });
        }
        log.info("Subscribed PerformanceComputer to winds updates");
    }

    /**
     * Subscribe to NOTAM updates for FMC alerts.
     * Critical NOTAMs will trigger FMC status messages.
     */
    public void subscribeNotamsForFmcAlerts() {
        if (notamService instanceof NotamServiceImpl ns) {
            ns.subscribeToRouteNotams(routeNotams -> {
                if (!routeNotams.getCriticalNotams().isEmpty()) {
                    log.warn("CRITICAL NOTAMs received for route: {} critical items",
                        routeNotams.getCriticalNotams().size());
                    // In real system, this would update FMC SystemStatus
                }
            });
        }
        log.info("Subscribed FMC to critical NOTAM alerts");
    }

    /**
     * Subscribe custom handler to METAR updates for an airport.
     */
    public void subscribeToMetar(String icaoCode, Consumer<WeatherService.MetarReport> handler) {
        if (weatherService instanceof WeatherServiceImpl ws) {
            ws.subscribeToMetar(icaoCode, handler);
        }
    }

    /**
     * Subscribe custom handler to NOTAM updates for an airport.
     */
    public void subscribeToNotams(String icaoCode, Consumer<List<NotamService.NotamReport>> handler) {
        if (notamService instanceof NotamServiceImpl ns) {
            ns.subscribeToNotams(icaoCode, handler);
        }
    }

    // ==================== Pre-flight Briefing ====================

    /**
     * Perform complete pre-flight briefing: weather + NOTAMs.
     * Returns a summary suitable for FMC display.
     */
    public BriefingResult performPreflightBriefing() {
        FlightPlan fp = fmcSystem.getActiveFlightPlan().orElse(null);
        if (fp == null) {
            return new BriefingResult("No active flight plan", List.of(), List.of());
        }

        String dep = fp.getDeparture().getIcaoCode();
        String dest = fp.getDestination().getIcaoCode();

        // Collect weather
        List<String> weatherSummary = List.of(
            weatherService.getMetar(dep).map(m -> dep + ": " + m.rawText()).orElse(dep + ": No METAR"),
            weatherService.getMetar(dest).map(m -> dest + ": " + m.rawText()).orElse(dest + ": No METAR")
        );

        // Collect NOTAMs
        NotamService.RouteNotams routeNotams = getNotamsForCurrentRoute();
        List<String> notamSummary = routeNotams.fmcRelevantNotams().stream()
            .limit(5)
            .map(n -> n.icaoCode() + " " + n.keyword() + ": " + n.subject())
            .toList();

        return new BriefingResult(
            "Briefing for " + dep + " → " + dest,
            weatherSummary,
            notamSummary
        );
    }

    /**
     * Briefing result record for FMC display.
     */
    public record BriefingResult(
        String title,
        List<String> weatherLines,
        List<String> notamLines
    ) {
        public boolean hasCriticalNotams() {
            return notamLines.stream().anyMatch(l -> l.contains("CRITICAL") || l.contains("CLSD"));
        }
    }
}
