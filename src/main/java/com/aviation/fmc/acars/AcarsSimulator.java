package com.aviation.fmc.acars;

import com.aviation.fmc.common.GeoCoordinate;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * ACARS Simulator demonstration.
 * Shows how aircraft communicate with ground stations via ACARS.
 */
@Slf4j
public class AcarsSimulator {

    public static void main(String[] args) throws InterruptedException {
        log.info("=== ACARS Simulator Demo ===");

        // Create ground stations (simulating ARINC/SITA network)
        AcarsGroundStation jfkStation = new AcarsGroundStation(
                "JFK-GS",
                "JFK Ground Station",
                new AcarsGroundStation.GeoCoverage(40.6413, -73.7781, 200, "JFK Area")
        );

        AcarsGroundStation ordStation = new AcarsGroundStation(
                "ORD-GS",
                "ORD Ground Station",
                new AcarsGroundStation.GeoCoverage(41.9742, -87.9073, 200, "ORD Area")
        );

        // Start ground stations
        jfkStation.start();
        ordStation.start();

        // Subscribe to messages at ground stations
        jfkStation.subscribeToAll(msg -> log.info("[JFK] Received: {} from {}",
                msg.getLabel(), msg.getAircraftRegistration()));

        ordStation.subscribeToLabel(AcarsMessage.Label.POSITION_REPORT, msg ->
                log.info("[ORD] Position report from {}: {}",
                        msg.getAircraftRegistration(), msg.getMessageText()));

        // Create aircraft ACARS units
        AircraftAcarsUnit aircraft1 = new AircraftAcarsUnit(
                AircraftAcarsUnit.AircraftIdentity.builder()
                        .registration("N123AA")
                        .icao24("A12345")
                        .airlineCode("AAL")
                        .aircraftType("B738")
                        .build()
        );

        AircraftAcarsUnit aircraft2 = new AircraftAcarsUnit(
                AircraftAcarsUnit.AircraftIdentity.builder()
                        .registration("N456UA")
                        .icao24("A67890")
                        .airlineCode("UAL")
                        .aircraftType("A320")
                        .build()
        );

        // Register ground stations with aircraft
        aircraft1.registerGroundStation(jfkStation);
        aircraft1.registerGroundStation(ordStation);
        aircraft2.registerGroundStation(jfkStation);
        aircraft2.registerGroundStation(ordStation);

        // Start aircraft ACARS units
        aircraft1.start();
        aircraft2.start();

        // Simulate flight for aircraft 1 (JFK to ORD)
        log.info("\n--- Simulating flight N123AA JFK->ORD ---");
        simulateFlightN123AA(aircraft1);

        // Simulate flight for aircraft 2 (ORD to JFK)
        log.info("\n--- Simulating flight N456UA ORD->JFK ---");
        simulateFlightN456UA(aircraft2);

        // Wait for messages to be processed
        Thread.sleep(3000);

        // Print statistics
        log.info("\n=== Statistics ===");
        log.info("JFK Station: received={}, sent={}",
                jfkStation.getStatistics().messagesReceived,
                jfkStation.getStatistics().messagesSent);
        log.info("ORD Station: received={}, sent={}",
                ordStation.getStatistics().messagesReceived,
                ordStation.getStatistics().messagesSent);
        log.info("N123AA: transmitted={}, received={}",
                aircraft1.getStatistics().messagesTransmitted,
                aircraft1.getStatistics().messagesReceived);
        log.info("N456UA: transmitted={}, received={}",
                aircraft2.getStatistics().messagesTransmitted,
                aircraft2.getStatistics().messagesReceived);

        // Cleanup
        aircraft1.stop();
        aircraft2.stop();
        jfkStation.stop();
        ordStation.stop();

        log.info("\n=== ACARS Simulator Demo Complete ===");
    }

    private static void simulateFlightN123AA(AircraftAcarsUnit acars) throws InterruptedException {
        // Initial position at JFK
        GeoCoordinate jfkPos = GeoCoordinate.builder()
                .latitude(40.6413)
                .longitude(-73.7781)
                .build();

        // Update position
        acars.updatePosition(jfkPos, 0, 0, 0);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.PREFLIGHT);

        // Request weather for destination
        acars.requestWeather("KORD", msg ->
                log.info("[N123AA] Received weather: {}", msg.getMessageText()));

        Thread.sleep(500);

        // Request route
        acars.requestRouteUpload("KJFK", "KORD", msg ->
                log.info("[N123AA] Received route upload"));

        Thread.sleep(500);

        // Taxi and takeoff
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.TAXI);
        Thread.sleep(200);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.TAKEOFF);

        Thread.sleep(500);

        // In flight - climbing
        acars.updatePosition(
                GeoCoordinate.builder().latitude(41.0).longitude(-76.0).build(),
                15000, 90, 280);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.CLIMB);

        // Send engine report
        acars.sendEngineReport(new AircraftAcarsUnit.EngineData(
                1, 85.5, 92.0, 650, 2500, 45, 80, 1.2));

        Thread.sleep(500);

        // Cruise
        acars.updatePosition(
                GeoCoordinate.builder().latitude(42.0).longitude(-82.0).build(),
                35000, 270, 450);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.CRUISE);

        // Send free text to operations
        acars.sendFreeText("EST ARR 1830Z, FUEL 8500KG", "OPS");

        Thread.sleep(500);

        // Descent
        acars.updatePosition(
                GeoCoordinate.builder().latitude(41.8).longitude(-87.0).build(),
                18000, 270, 320);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.DESCENT);

        Thread.sleep(500);

        // Approach and landing
        acars.updatePosition(
                GeoCoordinate.builder().latitude(41.9742).longitude(-87.9073).build(),
                3000, 270, 180);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.APPROACH);

        Thread.sleep(200);

        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.LANDING);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.PARKED);
    }

    private static void simulateFlightN456UA(AircraftAcarsUnit acars) throws InterruptedException {
        // Initial position at ORD
        GeoCoordinate ordPos = GeoCoordinate.builder()
                .latitude(41.9742)
                .longitude(-87.9073)
                .build();

        acars.updatePosition(ordPos, 0, 0, 0);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.PREFLIGHT);

        // Request clearance
        AcarsMessage clearanceReq = AcarsMessage.builder()
                .direction(AcarsMessage.Direction.DOWNLINK)
                .label(AcarsMessage.Label.CLEARANCE_REQUEST)
                .priority(AcarsMessage.Priority.HIGH)
                .medium(AcarsMessage.Medium.VHF)
                .aircraftRegistration(acars.getIdentity().registration())
                .messageText("REQ CLR KORD-KJFK")
                .build();

        // Direct send through registered station
        AcarsGroundStation station = findStationForAircraft(acars);
        if (station != null) {
            station.receiveFromAircraft(clearanceReq);
        }

        Thread.sleep(500);

        // Taxi and takeoff
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.TAXI);
        Thread.sleep(200);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.TAKEOFF);

        Thread.sleep(500);

        // Climb
        acars.updatePosition(
                GeoCoordinate.builder().latitude(41.5).longitude(-85.0).build(),
                20000, 90, 320);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.CLIMB);

        // Send engine report
        acars.sendEngineReport(new AircraftAcarsUnit.EngineData(
                1, 88.0, 94.0, 680, 2800, 48, 85, 0.8));

        Thread.sleep(500);

        // Cruise
        acars.updatePosition(
                GeoCoordinate.builder().latitude(41.0).longitude(-80.0).build(),
                37000, 90, 460);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.CRUISE);

        Thread.sleep(500);

        // Descent
        acars.updatePosition(
                GeoCoordinate.builder().latitude(40.8).longitude(-76.0).build(),
                15000, 90, 300);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.DESCENT);

        Thread.sleep(500);

        // Landing
        acars.updatePosition(
                GeoCoordinate.builder().latitude(40.6413).longitude(-73.7781).build(),
                2000, 90, 160);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.APPROACH);
        Thread.sleep(200);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.LANDING);
        acars.updateFlightPhase(AircraftAcarsUnit.FlightPhase.PARKED);
    }

    private static AcarsGroundStation findStationForAircraft(AircraftAcarsUnit acars) {
        // In real implementation, would find based on position
        // For demo, return null (message will be queued)
        return null;
    }
}
