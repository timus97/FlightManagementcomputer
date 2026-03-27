package com.aviation.fmc.acars;

import com.aviation.fmc.common.GeoCoordinate;
import com.aviation.fmc.contracts.NavigationComputer;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Aircraft ACARS Management Unit (AMU).
 * Simulates the airborne ACARS equipment that communicates with ground stations.
 *
 * The AMU:
 * - Manages the aircraft's ACARS identity
 * - Handles message composition and transmission
 * - Receives and processes uplink messages
 * - Interfaces with aircraft systems (FMC, Engines, etc.)
 * - Automatically selects the best communication medium
 */
@Slf4j
public class AircraftAcarsUnit {

    @Getter
    private final AircraftIdentity identity;

    // Communication
    private final List<AcarsGroundStation> groundStations;
    private AcarsGroundStation currentStation;

    // Message handling
    private final BlockingQueue<AcarsMessage> transmitQueue;
    private final BlockingQueue<AcarsMessage> receiveQueue;
    private final Map<String, Consumer<AcarsMessage>> responseHandlers;

    // Event reporting
    private final AcarsEventReporter eventReporter;

    // State
    private volatile boolean running = false;
    private volatile FlightPhase currentPhase = FlightPhase.PREFLIGHT;
    private GeoCoordinate currentPosition;
    private double currentAltitude;
    private double currentHeading;
    private double currentSpeed;

    // Processing
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;

    // Statistics
    private final Statistics statistics;

    public AircraftAcarsUnit(AircraftIdentity identity) {
        this.identity = identity;
        this.groundStations = new CopyOnWriteArrayList<>();
        this.transmitQueue = new LinkedBlockingQueue<>();
        this.receiveQueue = new LinkedBlockingQueue<>();
        this.responseHandlers = new ConcurrentHashMap<>();
        this.eventReporter = new AcarsEventReporter(this);
        this.statistics = new Statistics();
    }

    /**
     * Start the ACARS unit.
     */
    public void start() {
        if (running) return;
        running = true;

        // Create executors if needed
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(3);
        }
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(2);
        }

        log.info("Starting ACARS unit for aircraft {}", identity.registration());

        // Start message processors
        executor.submit(this::processTransmitQueue);
        executor.submit(this::processReceiveQueue);

        // Start periodic reporting
        scheduler.scheduleAtFixedRate(this::sendPositionReport, 60, 300, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkGroundStationHandoff, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Stop the ACARS unit.
     */
    public void stop() {
        running = false;
        executor.shutdownNow();
        scheduler.shutdownNow();
        log.info("Stopped ACARS unit for aircraft {}", identity.registration());
    }

    /**
     * Register a ground station for communication.
     */
    public void registerGroundStation(AcarsGroundStation station) {
        groundStations.add(station);

        // Subscribe to messages addressed to this aircraft
        station.subscribeToAll(msg -> {
            if (msg.getDirection() == AcarsMessage.Direction.UPLINK &&
                identity.registration().equals(msg.getAircraftRegistration())) {
                receiveQueue.offer(msg);
            }
        });
    }

    /**
     * Send a free text message to ground.
     */
    public void sendFreeText(String text, String destination) {
        AcarsMessage message = AcarsMessage.builder()
                .direction(AcarsMessage.Direction.DOWNLINK)
                .label(AcarsMessage.Label.FREE_TEXT)
                .priority(AcarsMessage.Priority.NORMAL)
                .medium(selectBestMedium())
                .aircraftRegistration(identity.registration())
                .aircraftIcao24(identity.icao24())
                .destinationAddress(destination)
                .messageText(text)
                .createdAt(Instant.now())
                .blockId(generateBlockId())
                .build();

        queueMessage(message);
    }

    /**
     * Request weather information.
     */
    public void requestWeather(String airportCode, Consumer<AcarsMessage> responseHandler) {
        String requestId = UUID.randomUUID().toString();

        AcarsMessage message = AcarsMessage.builder()
                .direction(AcarsMessage.Direction.DOWNLINK)
                .label(AcarsMessage.Label.WEATHER_REQUEST)
                .priority(AcarsMessage.Priority.NORMAL)
                .medium(selectBestMedium())
                .aircraftRegistration(identity.registration())
                .messageText("REQ WX " + airportCode + " ID=" + requestId)
                .structuredData(Map.of("airport", airportCode, "requestId", requestId))
                .createdAt(Instant.now())
                .blockId(generateBlockId())
                .build();

        responseHandlers.put(requestId, responseHandler);
        queueMessage(message);
    }

    /**
     * Request flight plan route from ground.
     */
    public void requestRouteUpload(String departure, String destination,
                                   Consumer<AcarsMessage> responseHandler) {
        String requestId = UUID.randomUUID().toString();

        AcarsMessage message = AcarsMessage.builder()
                .direction(AcarsMessage.Direction.DOWNLINK)
                .label(AcarsMessage.Label.ROUTE_REQUEST)
                .priority(AcarsMessage.Priority.HIGH)
                .medium(selectBestMedium())
                .aircraftRegistration(identity.registration())
                .messageText(String.format("REQ RTE %s-%s ID=%s", departure, destination, requestId))
                .structuredData(Map.of(
                        "departure", departure,
                        "destination", destination,
                        "requestId", requestId
                ))
                .createdAt(Instant.now())
                .blockId(generateBlockId())
                .build();

        responseHandlers.put(requestId, responseHandler);
        queueMessage(message);
    }

    /**
     * Send OOOI event (Out/Off/On/In).
     */
    public void sendOooiEvent(OooiEvent event) {
        AcarsMessage message = AcarsMessage.builder()
                .direction(AcarsMessage.Direction.DOWNLINK)
                .label(AcarsMessage.Label.OOOI_EVENT)
                .priority(AcarsMessage.Priority.HIGH)
                .medium(selectBestMedium())
                .aircraftRegistration(identity.registration())
                .messageText(event.toAcarsString())
                .structuredData(event.toMap())
                .createdAt(Instant.now())
                .blockId(generateBlockId())
                .build();

        queueMessage(message);
        log.info("Sent OOOI event: {} for aircraft {}", event.type(), identity.registration());
    }

    /**
     * Send engine report.
     */
    public void sendEngineReport(EngineData data) {
        AcarsMessage message = AcarsMessage.builder()
                .direction(AcarsMessage.Direction.DOWNLINK)
                .label(AcarsMessage.Label.ENGINE_REPORT)
                .priority(AcarsMessage.Priority.NORMAL)
                .medium(selectBestMedium())
                .aircraftRegistration(identity.registration())
                .messageText(data.toAcarsString())
                .structuredData(data.toMap())
                .createdAt(Instant.now())
                .blockId(generateBlockId())
                .build();

        queueMessage(message);
    }

    /**
     * Update aircraft position for automatic reports.
     */
    public void updatePosition(GeoCoordinate position, double altitude,
                               double heading, double speed) {
        this.currentPosition = position;
        this.currentAltitude = altitude;
        this.currentHeading = heading;
        this.currentSpeed = speed;
    }

    /**
     * Update flight phase.
     */
    public void updateFlightPhase(FlightPhase phase) {
        FlightPhase oldPhase = this.currentPhase;
        this.currentPhase = phase;

        // Send appropriate OOOI events
        Instant now = Instant.now();
        switch (phase) {
            case TAKEOFF -> {
                if (oldPhase == FlightPhase.PREFLIGHT || oldPhase == FlightPhase.TAXI) {
                    sendOooiEvent(new OooiEvent(OooiEvent.Type.OUT, currentPosition, currentAltitude, now));
                    sendOooiEvent(new OooiEvent(OooiEvent.Type.OFF, currentPosition, currentAltitude, now));
                }
            }
            case LANDING -> {
                if (oldPhase == FlightPhase.APPROACH) {
                    sendOooiEvent(new OooiEvent(OooiEvent.Type.ON, currentPosition, currentAltitude, now));
                    sendOooiEvent(new OooiEvent(OooiEvent.Type.IN, currentPosition, currentAltitude, now));
                }
            }
            default -> {}
        }
    }

    /**
     * Get the event reporter for automatic reporting.
     */
    public AcarsEventReporter getEventReporter() {
        return eventReporter;
    }

    /**
     * Get statistics.
     */
    public Statistics getStatistics() {
        return statistics;
    }

    // ==================== Private Methods ====================

    private void queueMessage(AcarsMessage message) {
        if (!running) {
            log.warn("ACARS unit not running, message dropped");
            return;
        }

        boolean queued = transmitQueue.offer(message);
        if (queued) {
            statistics.messagesQueued++;
        } else {
            statistics.messagesDropped++;
            log.warn("Transmit queue full, message dropped");
        }
    }

    private void processTransmitQueue() {
        while (running) {
            try {
                AcarsMessage message = transmitQueue.poll(1, TimeUnit.SECONDS);
                if (message != null) {
                    transmitMessage(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void transmitMessage(AcarsMessage message) {
        // Find best ground station
        AcarsGroundStation station = findBestGroundStation();

        if (station == null) {
            log.warn("No ground station available for transmission");
            statistics.transmissionFailures++;
            return;
        }

        // Simulate transmission
        boolean success = simulateTransmission(message);

        if (success) {
            station.receiveFromAircraft(message);
            statistics.messagesTransmitted++;
            currentStation = station;
            log.debug("Transmitted {} message to {}", message.getLabel(), station);
        } else {
            statistics.transmissionFailures++;
            log.warn("Transmission failed for {} message", message.getLabel());
        }
    }

    private boolean simulateTransmission(AcarsMessage message) {
        // Simulate success rate based on medium
        double successRate = switch (message.getMedium()) {
            case VHF -> 0.98;
            case HF -> 0.85;
            case SATCOM -> 0.99;
            case VDL2 -> 0.995;
        };

        // Add some randomness
        return Math.random() < successRate;
    }

    private void processReceiveQueue() {
        while (running) {
            try {
                AcarsMessage message = receiveQueue.poll(1, TimeUnit.SECONDS);
                if (message != null) {
                    handleReceivedMessage(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleReceivedMessage(AcarsMessage message) {
        log.info("Received uplink {} message: {}", message.getLabel(),
                message.getMessageText().substring(0, Math.min(50, message.getMessageText().length())));

        statistics.messagesReceived++;

        // Check for response handlers
        String requestId = message.getStructuredData() != null ?
                message.getStructuredData().get("requestId") : null;

        if (requestId != null && responseHandlers.containsKey(requestId)) {
            Consumer<AcarsMessage> handler = responseHandlers.remove(requestId);
            handler.accept(message);
        }

        // Handle specific message types
        switch (message.getLabel()) {
            case ROUTE_UPLOAD -> handleRouteUpload(message);
            case WEATHER_REPORT -> handleWeatherReport(message);
            case CLEARANCE_DELIVERY -> handleClearance(message);
            default -> log.debug("Unhandled message type: {}", message.getLabel());
        }
    }

    private void handleRouteUpload(AcarsMessage message) {
        log.info("Received route upload from ground");
        // Would integrate with FMC here
    }

    private void handleWeatherReport(AcarsMessage message) {
        log.info("Received weather report from ground");
        // Would integrate with FMC/ND here
    }

    private void handleClearance(AcarsMessage message) {
        log.info("Received clearance from ground: {}", message.getMessageText());
        // Would display on MCDU here
    }

    private void sendPositionReport() {
        if (currentPosition == null) return;

        // Only send position reports during certain phases
        if (currentPhase != FlightPhase.CLIMB &&
            currentPhase != FlightPhase.CRUISE &&
            currentPhase != FlightPhase.DESCENT) {
            return;
        }

        String report = String.format("POS %s/%s/%s ALT%s SPD%s HDG%s",
                formatCoordinate(currentPosition),
                formatTime(Instant.now()),
                currentPhase,
                (int) currentAltitude,
                (int) currentSpeed,
                (int) currentHeading);

        AcarsMessage message = AcarsMessage.builder()
                .direction(AcarsMessage.Direction.DOWNLINK)
                .label(AcarsMessage.Label.POSITION_REPORT)
                .priority(AcarsMessage.Priority.NORMAL)
                .medium(selectBestMedium())
                .aircraftRegistration(identity.registration())
                .messageText(report)
                .createdAt(Instant.now())
                .blockId(generateBlockId())
                .build();

        queueMessage(message);
    }

    private void checkGroundStationHandoff() {
        // In real implementation, would check signal strength
        // and handoff to better station if available
    }

    private AcarsGroundStation findBestGroundStation() {
        // Simple implementation: return first available
        // Real implementation would consider signal strength, coverage, etc.
        return groundStations.isEmpty() ? null : groundStations.get(0);
    }

    private AcarsMessage.Medium selectBestMedium() {
        // Select based on altitude and location
        if (currentAltitude > 25000) {
            return AcarsMessage.Medium.SATCOM; // SATCOM available at altitude
        } else if (currentAltitude > 5000) {
            return AcarsMessage.Medium.VDL2; // VDL2 preferred at altitude
        } else {
            return AcarsMessage.Medium.VHF; // VHF near ground
        }
    }

    private String generateBlockId() {
        // Generate sequential block ID (A-Z, 0-9)
        int blockNum = (int) (statistics.messagesTransmitted % 36);
        return blockNum < 10 ? String.valueOf(blockNum) : String.valueOf((char) ('A' + blockNum - 10));
    }

    private String formatCoordinate(GeoCoordinate coord) {
        return String.format("%.4f/%.4f", coord.getLatitude(), coord.getLongitude());
    }

    private String formatTime(Instant instant) {
        return java.time.format.DateTimeFormatter.ofPattern("HHmm")
                .withZone(java.time.ZoneOffset.UTC)
                .format(instant);
    }

    // ==================== Inner Classes ====================

    @Builder
    public record AircraftIdentity(
            String registration,      // Tail number (e.g., "N123AA")
            String icao24,           // ICAO 24-bit address (hex)
            String airlineCode,      // 3-letter ICAO airline code
            String aircraftType,     // Aircraft type (e.g., "B738")
            String serialNumber      // Aircraft serial number
    ) {}

    public enum FlightPhase {
        PREFLIGHT, TAXI, TAKEOFF, CLIMB, CRUISE, DESCENT, APPROACH, LANDING, PARKED
    }

    public record OooiEvent(
            Type type,
            GeoCoordinate position,
            double altitude,
            Instant timestamp
    ) {
        public enum Type { OUT, OFF, ON, IN }

        public String toAcarsString() {
            return String.format("%s %s ALT%d %s",
                    type,
                    String.format("%.4f/%.4f", position.getLatitude(), position.getLongitude()),
                    (int) altitude,
                    java.time.format.DateTimeFormatter.ofPattern("HHmm")
                            .withZone(java.time.ZoneOffset.UTC)
                            .format(timestamp));
        }

        public Map<String, String> toMap() {
            return Map.of(
                    "eventType", type.name(),
                    "latitude", String.valueOf(position.getLatitude()),
                    "longitude", String.valueOf(position.getLongitude()),
                    "altitude", String.valueOf(altitude),
                    "timestamp", timestamp.toString()
            );
        }
    }

    public record EngineData(
            int engineNumber,
            double n1,
            double n2,
            double egt,
            double fuelFlow,
            double oilPressure,
            double oilTemp,
            double vibration
    ) {
        public String toAcarsString() {
            return String.format("ENG%d N1%.1f N2%.1f EGT%.0f FF%.0f",
                    engineNumber, n1, n2, egt, fuelFlow);
        }

        public Map<String, String> toMap() {
            return Map.of(
                    "engine", String.valueOf(engineNumber),
                    "n1", String.valueOf(n1),
                    "n2", String.valueOf(n2),
                    "egt", String.valueOf(egt),
                    "fuelFlow", String.valueOf(fuelFlow)
            );
        }
    }

    public static class Statistics {
        public long messagesQueued = 0;
        public long messagesTransmitted = 0;
        public long messagesReceived = 0;
        public long messagesDropped = 0;
        public long transmissionFailures = 0;
    }
}
