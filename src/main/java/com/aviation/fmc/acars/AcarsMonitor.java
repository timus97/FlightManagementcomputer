package com.aviation.fmc.acars;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ACARS Monitor - Real-time monitoring and debugging tool for ACARS messages.
 * Provides visibility into message flow between aircraft and ground stations.
 */
@Slf4j
public class AcarsMonitor {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    // Message tracking
    private final List<MessageLogEntry> messageLog;
    private final Map<String, AircraftStatus> aircraftStatus;
    private final Map<String, GroundStationStatus> stationStatus;
    private final Map<String, AtomicLong> messageCounters;

    // Configuration
    private final boolean verboseLogging;
    private final Set<AcarsMessage.Label> filteredLabels;

    public AcarsMonitor() {
        this(false, Collections.emptySet());
    }

    public AcarsMonitor(boolean verboseLogging, Set<AcarsMessage.Label> filteredLabels) {
        this.verboseLogging = verboseLogging;
        this.filteredLabels = new HashSet<>(filteredLabels);
        this.messageLog = Collections.synchronizedList(new ArrayList<>());
        this.aircraftStatus = new ConcurrentHashMap<>();
        this.stationStatus = new ConcurrentHashMap<>();
        this.messageCounters = new ConcurrentHashMap<>();
    }

    /**
     * Attach monitor to a ground station to observe all messages.
     */
    public void attachToGroundStation(AcarsGroundStation station) {
        String stationId = getStationId(station);

        station.subscribeToAll(message -> {
            logMessage(message, stationId, MessageDirection.RECEIVED);
        });

        stationStatus.put(stationId, new GroundStationStatus(stationId, true));
        log.info("[MONITOR] Attached to ground station: {}", stationId);
    }

    /**
     * Attach monitor to an aircraft to observe all transmissions.
     */
    public void attachToAircraft(AircraftAcarsUnit aircraft) {
        String aircraftReg = aircraft.getIdentity().registration();

        // We can't directly subscribe to aircraft transmissions, but we can
        // monitor via the ground stations it's registered with

        aircraftStatus.put(aircraftReg, new AircraftStatus(
                aircraftReg,
                aircraft.getIdentity().aircraftType(),
                "UNKNOWN"
        ));

        log.info("[MONITOR] Monitoring aircraft: {} ({})",
                aircraftReg, aircraft.getIdentity().aircraftType());
    }

    /**
     * Log a message event.
     */
    public void logMessage(AcarsMessage message, String source, MessageDirection direction) {
        // Apply label filter
        if (!filteredLabels.isEmpty() && !filteredLabels.contains(message.getLabel())) {
            return;
        }

        String timestamp = TIME_FORMATTER.format(Instant.now());
        String aircraftReg = message.getAircraftRegistration();

        // Update counters
        messageCounters.computeIfAbsent(message.getLabel().name(), k -> new AtomicLong(0)).incrementAndGet();

        // Update aircraft status
        if (aircraftReg != null) {
            aircraftStatus.computeIfAbsent(aircraftReg,
                    k -> new AircraftStatus(aircraftReg, "UNKNOWN", "UNKNOWN"));
            aircraftStatus.get(aircraftReg).updateLastSeen();
        }

        // Create log entry
        MessageLogEntry entry = new MessageLogEntry(
                timestamp,
                message.getLabel(),
                aircraftReg,
                source,
                direction,
                message.getMessageText(),
                message.getMedium()
        );
        messageLog.add(entry);

        // Console output
        String directionArrow = direction == MessageDirection.RECEIVED ? "<-" : "->";
        String medium = message.getMedium() != null ? message.getMedium().name() : "UNK";

        if (verboseLogging) {
            log.info("[ACARS] {} {} [{}] {} {} {}: {}",
                    timestamp,
                    directionArrow,
                    medium,
                    message.getLabel(),
                    aircraftReg != null ? aircraftReg : "N/A",
                    source,
                    truncate(message.getMessageText(), 60));
        } else {
            // Compact format
            log.info("[ACARS] {} {} {} {}",
                    directionArrow,
                    message.getLabel(),
                    aircraftReg != null ? aircraftReg : "N/A",
                    truncate(message.getMessageText(), 40));
        }
    }

    /**
     * Print current statistics summary.
     */
    public void printStatistics() {
        log.info("========== ACARS Statistics ==========");
        log.info("Total messages logged: {}", messageLog.size());
        log.info("Active aircraft: {}", aircraftStatus.size());
        log.info("Active ground stations: {}", stationStatus.size());

        if (!messageCounters.isEmpty()) {
            log.info("Message counts by label:");
            messageCounters.entrySet().stream()
                    .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                    .forEach(entry ->
                            log.info("  {}: {}", entry.getKey(), entry.getValue().get()));
        }

        if (!aircraftStatus.isEmpty()) {
            log.info("Aircraft status:");
            aircraftStatus.values().forEach(status ->
                    log.info("  {} [{}] - Last seen: {}s ago",
                            status.registration,
                            status.aircraftType,
                            status.secondsSinceLastSeen()));
        }
        log.info("======================================");
    }

    /**
     * Print message history for a specific aircraft.
     */
    public void printAircraftHistory(String aircraftReg) {
        log.info("========== Message History for {} ==========", aircraftReg);

        messageLog.stream()
                .filter(entry -> aircraftReg.equals(entry.aircraftReg()))
                .forEach(entry -> log.info("{} {} [{}] {}: {}",
                        entry.timestamp(),
                        entry.direction() == MessageDirection.RECEIVED ? "<-" : "->",
                        entry.medium(),
                        entry.label(),
                        entry.messageText()));

        log.info("==============================================");
    }

    /**
     * Print message history for a specific message label.
     */
    public void printLabelHistory(AcarsMessage.Label label) {
        log.info("========== Message History for {} ==========", label);

        messageLog.stream()
                .filter(entry -> entry.label() == label)
                .forEach(entry -> log.info("{} {} {}: {}",
                        entry.timestamp(),
                        entry.direction() == MessageDirection.RECEIVED ? "<-" : "->",
                        entry.aircraftReg(),
                        entry.messageText()));

        log.info("==============================================");
    }

    /**
     * Get recent messages (last N).
     */
    public List<MessageLogEntry> getRecentMessages(int count) {
        int start = Math.max(0, messageLog.size() - count);
        return new ArrayList<>(messageLog.subList(start, messageLog.size()));
    }

    /**
     * Clear message history.
     */
    public void clearHistory() {
        messageLog.clear();
        log.info("[MONITOR] Message history cleared");
    }

    /**
     * Get message count by label.
     */
    public long getMessageCount(AcarsMessage.Label label) {
        AtomicLong counter = messageCounters.get(label.name());
        return counter != null ? counter.get() : 0;
    }

    /**
     * Get total message count.
     */
    public int getTotalMessageCount() {
        return messageLog.size();
    }

    // ==================== Private Methods ====================

    private String getStationId(AcarsGroundStation station) {
        // Use reflection or toString to get station ID
        return station.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    // ==================== Inner Classes ====================

    public enum MessageDirection {
        RECEIVED,   // Ground station received from aircraft
        SENT        // Ground station sent to aircraft
    }

    public record MessageLogEntry(
            String timestamp,
            AcarsMessage.Label label,
            String aircraftReg,
            String source,
            MessageDirection direction,
            String messageText,
            AcarsMessage.Medium medium
    ) {}

    private static class AircraftStatus {
        final String registration;
        final String aircraftType;
        String currentPhase;
        volatile long lastSeenTimestamp;

        AircraftStatus(String registration, String aircraftType, String currentPhase) {
            this.registration = registration;
            this.aircraftType = aircraftType;
            this.currentPhase = currentPhase;
            this.lastSeenTimestamp = System.currentTimeMillis();
        }

        void updateLastSeen() {
            this.lastSeenTimestamp = System.currentTimeMillis();
        }

        long secondsSinceLastSeen() {
            return (System.currentTimeMillis() - lastSeenTimestamp) / 1000;
        }
    }

    private static class GroundStationStatus {
        final String stationId;
        volatile boolean active;

        GroundStationStatus(String stationId, boolean active) {
            this.stationId = stationId;
            this.active = active;
        }
    }

    // ==================== Static Utility Methods ====================

    /**
     * Quick check - run the ACARS simulator with monitoring.
     */
    public static void main(String[] args) throws InterruptedException {
        log.info("Starting ACARS Monitor Demo...\n");

        // Create monitor with verbose logging
        AcarsMonitor monitor = new AcarsMonitor(true, Collections.emptySet());

        // Create ground stations
        AcarsGroundStation jfkStation = new AcarsGroundStation(
                "JFK-GS", "JFK Ground",
                new AcarsGroundStation.GeoCoverage(40.6413, -73.7781, 200, "JFK Area")
        );

        AcarsGroundStation ordStation = new AcarsGroundStation(
                "ORD-GS", "ORD Ground",
                new AcarsGroundStation.GeoCoverage(41.9742, -87.9073, 200, "ORD Area")
        );

        // Start stations
        jfkStation.start();
        ordStation.start();

        // Attach monitor
        monitor.attachToGroundStation(jfkStation);
        monitor.attachToGroundStation(ordStation);

        // Create aircraft
        AircraftAcarsUnit aircraft1 = new AircraftAcarsUnit(
                AircraftAcarsUnit.AircraftIdentity.builder()
                        .registration("N123AA")
                        .icao24("A12345")
                        .airlineCode("AAL")
                        .aircraftType("B738")
                        .build()
        );

        aircraft1.registerGroundStation(jfkStation);
        aircraft1.registerGroundStation(ordStation);
        aircraft1.start();

        monitor.attachToAircraft(aircraft1);

        // Send some test messages
        log.info("\n--- Sending Test Messages ---\n");

        aircraft1.sendFreeText("TEST MESSAGE 1", "OPS");
        Thread.sleep(500);

        aircraft1.requestWeather("KJFK", msg -> log.info("Weather response received"));
        Thread.sleep(500);

        aircraft1.sendEngineReport(new AircraftAcarsUnit.EngineData(
                1, 85.5, 92.0, 650, 2500, 45, 80, 1.2));
        Thread.sleep(500);

        // Print statistics
        Thread.sleep(1000);
        log.info("");
        monitor.printStatistics();

        // Print history for aircraft
        log.info("");
        monitor.printAircraftHistory("N123AA");

        // Cleanup
        aircraft1.stop();
        jfkStation.stop();
        ordStation.stop();

        log.info("\nACARS Monitor Demo Complete");
    }
}
