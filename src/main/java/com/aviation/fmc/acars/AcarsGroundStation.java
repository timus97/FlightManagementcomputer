package com.aviation.fmc.acars;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Simulates an ACARS ground station (e.g., ARINC, SITA, or airline operations center).
 * Receives downlink messages from aircraft and sends uplink messages.
 *
 * Ground stations typically:
 * - Maintain radio coverage over a geographic area
 * - Route messages to/from airline operations centers
 * - Store and forward messages when aircraft is out of coverage
 * - Handle multiple aircraft simultaneously
 */
@Slf4j
public class AcarsGroundStation {

    private final String stationId;
    private final String stationName;
    private final GeoCoverage coverage;

    // Message routing
    private final BlockingQueue<AcarsMessage> inboundQueue;   // From aircraft
    private final BlockingQueue<AcarsMessage> outboundQueue;  // To aircraft
    private final Map<String, List<AcarsMessage>> messageStore; // Store-and-forward

    // Subscribers for different message types
    private final Map<AcarsMessage.Label, List<Consumer<AcarsMessage>>> labelSubscribers;
    private final List<Consumer<AcarsMessage>> allMessageSubscribers;

    // Aircraft registry (aircraft currently in contact)
    private final Map<String, AircraftContact> activeAircraft;

    // Processing threads
    private ExecutorService processorExecutor;
    private volatile boolean running = false;

    // Statistics
    private final AcarsStatistics statistics;

    // OOOI Event Handler
    private OooiEventHandler oooiEventHandler;
    private boolean oooiProcessingEnabled = true;

    public AcarsGroundStation(String stationId, String stationName, GeoCoverage coverage) {
        this.stationId = stationId;
        this.stationName = stationName;
        this.coverage = coverage;

        this.inboundQueue = new LinkedBlockingQueue<>();
        this.outboundQueue = new LinkedBlockingQueue<>();
        this.messageStore = new ConcurrentHashMap<>();
        this.labelSubscribers = new ConcurrentHashMap<>();
        this.allMessageSubscribers = new CopyOnWriteArrayList<>();
        this.activeAircraft = new ConcurrentHashMap<>();
        this.statistics = new AcarsStatistics();
        this.oooiEventHandler = new OooiEventHandler();
    }

    /**
     * Start the ground station processing.
     */
    public void start() {
        if (running) return;
        running = true;

        // Create new executor if needed
        if (processorExecutor == null || processorExecutor.isShutdown()) {
            processorExecutor = Executors.newFixedThreadPool(4);
        }

        log.info("Starting ACARS ground station: {} ({})", stationName, stationId);

        // Start inbound message processor
        processorExecutor.submit(this::processInboundMessages);

        // Start outbound message processor
        processorExecutor.submit(this::processOutboundMessages);

        // Start periodic tasks
        processorExecutor.submit(this::runPeriodicTasks);
    }

    /**
     * Stop the ground station.
     */
    public void stop() {
        running = false;
        processorExecutor.shutdownNow();
        log.info("Stopped ACARS ground station: {}", stationId);
    }

    /**
     * Receive a message from an aircraft (simulates radio reception).
     */
    public boolean receiveFromAircraft(AcarsMessage message) {
        if (!running) {
            log.warn("Station {} not running, cannot receive message", stationId);
            return false;
        }

        // Update aircraft contact
        updateAircraftContact(message.getAircraftRegistration(), message.getMedium());

        // Add to inbound queue
        boolean accepted = inboundQueue.offer(message);
        if (accepted) {
            statistics.messagesReceived++;
            log.debug("Station {} received {} message from {}",
                    stationId, message.getLabel(), message.getAircraftRegistration());
        }
        return accepted;
    }

    /**
     * Send a message to an aircraft (queues for transmission).
     */
    public boolean sendToAircraft(AcarsMessage message) {
        if (!running) {
            log.warn("Station {} not running, cannot send message", stationId);
            return false;
        }

        // Set ground station as sender
        AcarsMessage updatedMessage = message.toBuilder()
                .groundStationId(stationId)
                .direction(AcarsMessage.Direction.UPLINK)
                .createdAt(Instant.now())
                .build();

        // Check if aircraft is in coverage
        String aircraftReg = message.getAircraftRegistration();
        if (!isAircraftInCoverage(aircraftReg)) {
            // Store for later delivery
            storeMessage(aircraftReg, updatedMessage);
            log.info("Aircraft {} not in coverage, message stored for later delivery", aircraftReg);
            return true;
        }

        boolean queued = outboundQueue.offer(updatedMessage);
        if (queued) {
            statistics.messagesSent++;
        }
        return queued;
    }

    /**
     * Subscribe to messages with a specific label.
     */
    public void subscribeToLabel(AcarsMessage.Label label, Consumer<AcarsMessage> handler) {
        labelSubscribers.computeIfAbsent(label, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /**
     * Subscribe to all messages.
     */
    public void subscribeToAll(Consumer<AcarsMessage> handler) {
        allMessageSubscribers.add(handler);
    }

    /**
     * Unsubscribe a handler.
     */
    public void unsubscribe(Consumer<AcarsMessage> handler) {
        allMessageSubscribers.remove(handler);
        labelSubscribers.values().forEach(list -> list.remove(handler));
    }

    /**
     * Get list of currently active aircraft.
     */
    public List<String> getActiveAircraft() {
        cleanupInactiveAircraft();
        return new ArrayList<>(activeAircraft.keySet());
    }

    /**
     * Check if an aircraft is currently in coverage.
     */
    public boolean isAircraftInCoverage(String aircraftRegistration) {
        cleanupInactiveAircraft();
        return activeAircraft.containsKey(aircraftRegistration);
    }

    /**
     * Get pending messages for an aircraft (store-and-forward).
     */
    public List<AcarsMessage> getPendingMessages(String aircraftRegistration) {
        return messageStore.getOrDefault(aircraftRegistration, Collections.emptyList());
    }

    /**
     * Get station statistics.
     */
    public AcarsStatistics getStatistics() {
        return statistics;
    }

    /**
     * Get the OOOI event handler for this station.
     */
    public OooiEventHandler getOooiEventHandler() {
        return oooiEventHandler;
    }

    /**
     * Enable or disable automatic OOOI event processing.
     */
    public void setOooiProcessingEnabled(boolean enabled) {
        this.oooiProcessingEnabled = enabled;
        log.info("OOOI processing {} at station {}",
                enabled ? "enabled" : "disabled", stationId);
    }

    /**
     * Check if OOOI processing is enabled.
     */
    public boolean isOooiProcessingEnabled() {
        return oooiProcessingEnabled;
    }

    /**
     * Get active flights being tracked by OOOI handler.
     */
    public List<OooiEventHandler.FlightSession> getActiveFlights() {
        return oooiEventHandler.getAllActiveFlights();
    }

    /**
     * Get flight summary for a specific aircraft.
     */
    public OooiEventHandler.FlightSummary getFlightSummary(String aircraftRegistration) {
        return oooiEventHandler.generateSummary(aircraftRegistration);
    }

    // ==================== Private Methods ====================

    private void processInboundMessages() {
        while (running) {
            try {
                AcarsMessage message = inboundQueue.poll(1, TimeUnit.SECONDS);
                if (message != null) {
                    handleInboundMessage(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleInboundMessage(AcarsMessage message) {
        log.info("Processing inbound {} message from {} at station {}",
                message.getLabel(), message.getAircraftRegistration(), stationId);

        // Process OOOI events if enabled
        if (oooiProcessingEnabled && message.getLabel() == AcarsMessage.Label.OOOI_EVENT) {
            processOooiMessage(message);
        }

        // Notify label-specific subscribers
        List<Consumer<AcarsMessage>> labelHandlers = labelSubscribers.get(message.getLabel());
        if (labelHandlers != null) {
            labelHandlers.forEach(handler -> notifyHandler(handler, message));
        }

        // Notify all-message subscribers
        allMessageSubscribers.forEach(handler -> notifyHandler(handler, message));

        // Send acknowledgment if required
        if (message.requiresAck()) {
            sendAcknowledgment(message);
        }
    }

    private void processOooiMessage(AcarsMessage message) {
        try {
            oooiEventHandler.processAcarsMessage(message);
            log.debug("Processed OOOI event from {} at station {}",
                    message.getAircraftRegistration(), stationId);
        } catch (Exception e) {
            log.error("Failed to process OOOI message from {}: {}",
                    message.getAircraftRegistration(), message.getMessageText(), e);
        }
    }

    private void notifyHandler(Consumer<AcarsMessage> handler, AcarsMessage message) {
        try {
            handler.accept(message);
        } catch (Exception e) {
            log.error("Error notifying message handler", e);
        }
    }

    private void processOutboundMessages() {
        while (running) {
            try {
                AcarsMessage message = outboundQueue.poll(1, TimeUnit.SECONDS);
                if (message != null) {
                    transmitToAircraft(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void transmitToAircraft(AcarsMessage message) {
        // Simulate transmission delay based on medium
        int delayMs = switch (message.getMedium()) {
            case VHF -> 200;
            case HF -> 1000;
            case SATCOM -> 500;
            case VDL2 -> 100;
        };

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        log.info("Transmitted {} message to {} via {} from station {}",
                message.getLabel(),
                message.getAircraftRegistration(),
                message.getMedium(),
                stationId);
    }

    private void sendAcknowledgment(AcarsMessage originalMessage) {
        AcarsMessage ack = AcarsMessage.builder()
                .direction(AcarsMessage.Direction.UPLINK)
                .label(AcarsMessage.Label.ACK)
                .priority(AcarsMessage.Priority.HIGH)
                .medium(originalMessage.getMedium())
                .aircraftRegistration(originalMessage.getAircraftRegistration())
                .groundStationId(stationId)
                .messageText("ACK " + originalMessage.getBlockId())
                .createdAt(Instant.now())
                .build();

        outboundQueue.offer(ack);
    }

    private void updateAircraftContact(String aircraftReg, AcarsMessage.Medium medium) {
        activeAircraft.put(aircraftReg, new AircraftContact(Instant.now(), medium));

        // Check for stored messages
        List<AcarsMessage> stored = messageStore.remove(aircraftReg);
        if (stored != null && !stored.isEmpty()) {
            log.info("Delivering {} stored messages to {}", stored.size(), aircraftReg);
            stored.forEach(outboundQueue::offer);
        }
    }

    private void cleanupInactiveAircraft() {
        Instant cutoff = Instant.now().minusSeconds(300); // 5 minutes
        activeAircraft.entrySet().removeIf(
                entry -> entry.getValue().lastContact().isBefore(cutoff));
    }

    private void storeMessage(String aircraftReg, AcarsMessage message) {
        messageStore.computeIfAbsent(aircraftReg, k -> new ArrayList<>()).add(message);
    }

    private void runPeriodicTasks() {
        while (running) {
            try {
                Thread.sleep(30000); // Run every 30 seconds
                cleanupInactiveAircraft();

                // Log statistics
                log.debug("Station {} statistics: received={}, sent={}, active aircraft={}",
                        stationId, statistics.messagesReceived, statistics.messagesSent,
                        activeAircraft.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ==================== Inner Classes ====================

    public record GeoCoverage(
            double centerLatitude,
            double centerLongitude,
            double radiusNm,
            String description
    ) {}

    private record AircraftContact(
            Instant lastContact,
            AcarsMessage.Medium lastMedium
    ) {}

    public static class AcarsStatistics {
        public long messagesReceived = 0;
        public long messagesSent = 0;
        public long messagesStored = 0;
        public long errors = 0;

        public void reset() {
            messagesReceived = 0;
            messagesSent = 0;
            messagesStored = 0;
            errors = 0;
        }
    }
}
