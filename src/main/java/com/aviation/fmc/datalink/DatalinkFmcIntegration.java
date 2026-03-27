package com.aviation.fmc.datalink;

import com.aviation.fmc.contracts.FmcSystem;
import com.aviation.fmc.contracts.NavigationComputer;
import com.aviation.fmc.contracts.PerformanceComputer;
import com.aviation.fmc.parser.FlightPlan;

import java.util.Optional;

/**
 * Demonstrates how Datalink integrates with the FMC system.
 * This shows real-world datalink-FMC interaction patterns.
 */
public class DatalinkFmcIntegration {

    private final FmcSystem fmc;
    private final DatalinkManager datalink;

    public DatalinkFmcIntegration(FmcSystem fmc, DatalinkManager datalink) {
        this.fmc = fmc;
        this.datalink = datalink;
    }

    /**
     * Example: Automatic flight plan uplink from airline.
     * This is how airlines push routes to aircraft before departure.
     */
    public void handleFlightPlanUplink() {
        // Check for uplinked flight plan
        Optional<FlightPlan> uplink = datalink.getUplinkedFlightPlan();
        
        if (uplink.isPresent()) {
            FlightPlan plan = uplink.get();
            
            // Validate the uplink
            System.out.println("Flight Plan Uplink Received: " + plan.getFlightNumber());
            System.out.println("Route: " + plan.getTotalDistance() + " NM");
            
            // Pilot would review on CDU, then accept
            // In auto-accept scenarios (airline policy):
            FmcSystem.LoadResult result = fmc.loadFlightPlan(plan);
            
            if (result.success()) {
                datalink.acceptFlightPlanUplink();
                System.out.println("Flight plan accepted and loaded");
            } else {
                datalink.rejectFlightPlanUplink("Validation failed: " + result.message());
            }
        }
    }

    /**
     * Example: Wind data uplink and VNAV recalculation.
     * Critical for accurate fuel predictions and descent planning.
     */
    public void handleWindUplink() {
        Optional<DatalinkManager.WindUplink> windUplink = datalink.getWindUplink();
        
        if (windUplink.isPresent()) {
            DatalinkManager.WindUplink wind = windUplink.get();
            
            System.out.println("Wind Uplink Received - Route: " + wind.routeId());
            
            // Update FMC wind model
            for (DatalinkManager.WindUplink.WindLevel level : wind.windLevels()) {
                System.out.printf("FL%d: Wind %d°/%dkt, Temp: %d°C%n",
                    level.flightLevel(),
                    level.windDirection(),
                    level.windSpeed(),
                    level.temperature()
                );
            }
            
            // FMC recalculates:
            // 1. Top of Descent (TOD) point
            PerformanceComputer perf = fmc.getPerformanceComputer();
            double newTod = perf.calculateTopOfDescent(3000, 3.0);
            System.out.println("Recalculated TOD: " + newTod + " NM from destination");
            
            // 2. Fuel predictions
            double fuelRequired = perf.calculateFuelRequired(
                fmc.getActiveFlightPlan().map(FlightPlan::getTotalDistance).orElse(0.0),
                35000,  // FL350
                0.78    // Mach 0.78
            );
            System.out.println("Updated fuel required: " + fuelRequired + " kg");
            
            // 3. ETA updates
            // 4. Cruise Mach optimization
        }
    }

    /**
     * Example: CPDLC clearance processing.
     * Demonstrates ATC-FMC interaction.
     */
    public void processCpdlcClearances() {
        for (DatalinkManager.CpdlcMessage msg : datalink.getPendingMessages()) {
            System.out.println("CPDLC Message from " + msg.from() + ": " + msg.content());
            
            switch (msg.type()) {
                case CLEARANCE -> {
                    // Example: "CLEARED TO CLIMB FL350"
                    if (msg.content().contains("CLIMB")) {
                        int newAlt = extractAltitude(msg.content());
                        System.out.println("Altitude clearance to: " + newAlt + " ft");
                        
                        // Pilot accepts, FMC updates target
                        datalink.acceptMessage(msg.messageId());
                        
                        // Update VNAV target
                        fmc.getGuidanceComputer().setTargetAltitude(newAlt);
                    }
                    
                    // Example: "CLEARED DIRECT WAYPOINT"
                    if (msg.content().contains("DIRECT")) {
                        String waypoint = extractWaypoint(msg.content());
                        System.out.println("Direct to: " + waypoint);
                        
                        datalink.acceptMessage(msg.messageId());
                        fmc.getNavigationComputer().directTo(waypoint);
                    }
                }
                
                case INSTRUCTION -> {
                    // Example: "TURN LEFT HEADING 270"
                    // FMC would update LNAV target
                    datalink.acceptMessage(msg.messageId());
                }
                
                case INFORMATION -> {
                    // Traffic, weather - display only
                    System.out.println("INFO: " + msg.content());
                    // No response required
                }
            }
        }
    }

    /**
     * Example: ADS-C position reporting.
     * Automatic in oceanic/remote airspace.
     */
    public void sendAdsCReport() {
        NavigationComputer nav = fmc.getNavigationComputer();
        
        if (nav.getCurrentPosition().isPresent()) {
            NavigationComputer.Position pos = nav.getCurrentPosition().get();
            
            DatalinkManager.PositionReport report = new DatalinkManager.PositionReport(
                pos.coordinate().getLatitude(),
                pos.coordinate().getLongitude(),
                (int) pos.altitude(),
                (int) pos.groundspeed(),
                nav.getActiveWaypoint().map(w -> w.getIdentifier()).orElse("UNKNOWN"),
                (int) nav.distanceToActiveWaypoint(),
                java.time.Instant.now().plusSeconds(600), // ETA
                java.time.Instant.now()
            );
            
            datalink.sendPositionReport(report);
            System.out.println("ADS-C Position Report sent");
        }
    }

    /**
     * Example: Departure clearance request (DCL).
     * Used at major airports instead of voice.
     */
    public void requestDepartureClearance(String airport, String stand) {
        System.out.println("Requesting DCL for " + airport + " at stand " + stand);
        datalink.requestDepartureClearance(airport, stand);
        
        // Response typically includes:
        // - Departure runway
        // - SID
        // - Initial altitude
        // - Squawk code
        // - Departure frequency
    }

    // Helper methods
    private int extractAltitude(String message) {
        // Parse FL350 or 35000 from message
        return 35000; // Simplified
    }
    
    private String extractWaypoint(String message) {
        // Parse waypoint from "CLEARED DIRECT BOSOX"
        return "BOSOX"; // Simplified
    }

    /**
     * Main integration flow showing datalink-FMC interaction.
     */
    public void runIntegrationExample() {
        System.out.println("=== Datalink-FMC Integration Example ===\n");
        
        // 1. Pre-flight: Get flight plan from airline
        System.out.println("1. Flight Plan Uplink:");
        handleFlightPlanUplink();
        
        // 2. Pre-departure: Get clearance
        System.out.println("\n2. Departure Clearance:");
        requestDepartureClearance("KJFK", "A12");
        
        // 3. In-flight: Process ATC clearances
        System.out.println("\n3. CPDLC Clearances:");
        processCpdlcClearances();
        
        // 4. In-flight: Wind updates for VNAV
        System.out.println("\n4. Wind Data Uplink:");
        handleWindUplink();
        
        // 5. Oceanic: Position reporting
        System.out.println("\n5. ADS-C Reporting:");
        sendAdsCReport();
        
        System.out.println("\n=== End Example ===");
    }
}
