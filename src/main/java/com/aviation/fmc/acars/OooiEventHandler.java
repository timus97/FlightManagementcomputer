package com.aviation.fmc.acars;

import com.aviation.fmc.common.GeoCoordinate;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OOOI Event Handler - Processes Out/Off/On/In events for flight tracking.
 * Calculates block times, flight times, and generates operational reports.
 */
@Slf4j
public class OooiEventHandler {

    // Flight tracking storage
    private final Map<String, FlightSession> activeFlights;
    private final Map<String, List<FlightSession>> completedFlights;
    private final List<OooiEventListener> listeners;

    public OooiEventHandler() {
        this.activeFlights = new ConcurrentHashMap<>();
        this.completedFlights = new ConcurrentHashMap<>();
        this.listeners = new ArrayList<>();
    }

    /**
     * Process an OOOI event from an aircraft.
     */
    public void processOooiEvent(String aircraftRegistration, OooiEventType type,
                                  GeoCoordinate position, double altitude, Instant timestamp) {
        log.info("Processing OOOI event: {} for aircraft {} at {}",
                type, aircraftRegistration, timestamp);

        FlightSession session = activeFlights.computeIfAbsent(
                aircraftRegistration,
                k -> new FlightSession(aircraftRegistration)
        );

        OooiEvent event = new OooiEvent(type, position, altitude, timestamp);
        session.addEvent(event);

        // Notify listeners
        listeners.forEach(listener -> {
            try {
                listener.onOooiEvent(aircraftRegistration, event, session);
            } catch (Exception e) {
                log.error("Error notifying OOOI listener", e);
            }
        });

        // Handle flight completion
        if (type == OooiEventType.IN) {
            completeFlight(aircraftRegistration, session);
        }
    }

    /**
     * Process OOOI event from ACARS message.
     */
    public void processAcarsMessage(AcarsMessage message) {
        if (message.getLabel() != AcarsMessage.Label.OOOI_EVENT) {
            return;
        }

        try {
            OooiEventData data = parseOooiMessage(message.getMessageText());
            processOooiEvent(
                    message.getAircraftRegistration(),
                    data.type(),
                    data.position(),
                    data.altitude(),
                    data.timestamp()
            );
        } catch (Exception e) {
            log.error("Failed to parse OOOI message: {}", message.getMessageText(), e);
        }
    }

    /**
     * Parse OOOI message text.
     * Format: "OUT 40.6413/-73.7781 ALT0 1430"
     */
    private OooiEventData parseOooiMessage(String text) {
        String[] parts = text.split(" ");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid OOOI message format");
        }

        OooiEventType type = OooiEventType.valueOf(parts[0]);

        // Parse coordinates
        String[] coords = parts[1].split("/");
        double latitude = Double.parseDouble(coords[0]);
        double longitude = Double.parseDouble(coords[1]);
        GeoCoordinate position = GeoCoordinate.builder()
                .latitude(latitude)
                .longitude(longitude)
                .build();

        // Parse altitude
        double altitude = Double.parseDouble(parts[2].replace("ALT", ""));

        // Parse timestamp
        String timeStr = parts[3];
        Instant timestamp = parseTime(timeStr);

        return new OooiEventData(type, position, altitude, timestamp);
    }

    private Instant parseTime(String timeStr) {
        // Parse HHmm format
        int hour = Integer.parseInt(timeStr.substring(0, 2));
        int minute = Integer.parseInt(timeStr.substring(2, 4));

        Instant now = Instant.now();
        return now.atZone(java.time.ZoneOffset.UTC)
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .toInstant();
    }

    /**
     * Get active flight session for an aircraft.
     */
    public Optional<FlightSession> getActiveFlight(String aircraftRegistration) {
        return Optional.ofNullable(activeFlights.get(aircraftRegistration));
    }

    /**
     * Get all active flights.
     */
    public List<FlightSession> getAllActiveFlights() {
        return new ArrayList<>(activeFlights.values());
    }

    /**
     * Get completed flights for an aircraft.
     */
    public List<FlightSession> getCompletedFlights(String aircraftRegistration) {
        return completedFlights.getOrDefault(aircraftRegistration, Collections.emptyList());
    }

    /**
     * Add an OOOI event listener.
     */
    public void addListener(OooiEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove an OOOI event listener.
     */
    public void removeListener(OooiEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Generate flight summary report.
     */
    public FlightSummary generateSummary(String aircraftRegistration) {
        FlightSession session = activeFlights.get(aircraftRegistration);
        if (session == null) {
            return null;
        }
        return session.generateSummary();
    }

    /**
     * Clear completed flight history.
     */
    public void clearHistory(String aircraftRegistration) {
        completedFlights.remove(aircraftRegistration);
    }

    // ==================== Private Methods ====================

    private void completeFlight(String aircraftRegistration, FlightSession session) {
        session.markComplete();
        activeFlights.remove(aircraftRegistration);

        completedFlights
                .computeIfAbsent(aircraftRegistration, k -> new ArrayList<>())
                .add(session);

        FlightSummary summary = session.generateSummary();
        log.info("Flight completed for {}: Block Time={}, Flight Time={}",
                aircraftRegistration,
                formatDuration(summary.getBlockTime()),
                formatDuration(summary.getFlightTime()));

        // Notify listeners
        listeners.forEach(listener -> {
            try {
                listener.onFlightComplete(aircraftRegistration, session, summary);
            } catch (Exception e) {
                log.error("Error notifying flight completion", e);
            }
        });
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return String.format("%02d:%02d", hours, minutes);
    }

    // ==================== Inner Classes ====================

    public enum OooiEventType {
        OUT, OFF, ON, IN
    }

    @Value
    @Builder
    public static class OooiEvent {
        OooiEventType type;
        GeoCoordinate position;
        double altitude;
        Instant timestamp;
    }

    private record OooiEventData(
            OooiEventType type,
            GeoCoordinate position,
            double altitude,
            Instant timestamp
    ) {}

    /**
     * Tracks a single flight session.
     */
    public static class FlightSession {
        private final String aircraftRegistration;
        private final List<OooiEvent> events;
        private volatile boolean complete;
        private Instant startTime;
        private Instant endTime;

        public FlightSession(String aircraftRegistration) {
            this.aircraftRegistration = aircraftRegistration;
            this.events = new ArrayList<>();
            this.complete = false;
        }

        public void addEvent(OooiEvent event) {
            events.add(event);

            if (event.getType() == OooiEventType.OUT) {
                startTime = event.getTimestamp();
            } else if (event.getType() == OooiEventType.IN) {
                endTime = event.getTimestamp();
            }
        }

        public void markComplete() {
            this.complete = true;
        }

        public FlightSummary generateSummary() {
            OooiEvent outEvent = findEvent(OooiEventType.OUT);
            OooiEvent offEvent = findEvent(OooiEventType.OFF);
            OooiEvent onEvent = findEvent(OooiEventType.ON);
            OooiEvent inEvent = findEvent(OooiEventType.IN);

            Duration blockTime = Duration.ZERO;
            Duration flightTime = Duration.ZERO;
            Duration taxiOutTime = Duration.ZERO;
            Duration taxiInTime = Duration.ZERO;

            if (outEvent != null && inEvent != null) {
                blockTime = Duration.between(outEvent.getTimestamp(), inEvent.getTimestamp());
            }

            if (offEvent != null && onEvent != null) {
                flightTime = Duration.between(offEvent.getTimestamp(), onEvent.getTimestamp());
            }

            if (outEvent != null && offEvent != null) {
                taxiOutTime = Duration.between(outEvent.getTimestamp(), offEvent.getTimestamp());
            }

            if (onEvent != null && inEvent != null) {
                taxiInTime = Duration.between(onEvent.getTimestamp(), inEvent.getTimestamp());
            }

            return FlightSummary.builder()
                    .aircraftRegistration(aircraftRegistration)
                    .startTime(startTime)
                    .endTime(endTime)
                    .blockTime(blockTime)
                    .flightTime(flightTime)
                    .taxiOutTime(taxiOutTime)
                    .taxiInTime(taxiInTime)
                    .departureLocation(outEvent != null ? formatPosition(outEvent.getPosition()) : null)
                    .arrivalLocation(inEvent != null ? formatPosition(inEvent.getPosition()) : null)
                    .complete(complete)
                    .build();
        }

        private OooiEvent findEvent(OooiEventType type) {
            return events.stream()
                    .filter(e -> e.getType() == type)
                    .findFirst()
                    .orElse(null);
        }

        private String formatPosition(GeoCoordinate coord) {
            return String.format("%.4f/%.4f", coord.getLatitude(), coord.getLongitude());
        }

        public String getAircraftRegistration() {
            return aircraftRegistration;
        }

        public List<OooiEvent> getEvents() {
            return new ArrayList<>(events);
        }

        public boolean isComplete() {
            return complete;
        }

        public Optional<OooiEvent> getLastEvent() {
            return events.isEmpty() ? Optional.empty() : Optional.of(events.get(events.size() - 1));
        }

        public OooiEventType getCurrentPhase() {
            return getLastEvent().map(OooiEvent::getType).orElse(null);
        }
    }

    /**
     * Summary of a completed flight.
     */
    @Value
    @Builder
    public static class FlightSummary {
        String aircraftRegistration;
        Instant startTime;
        Instant endTime;
        Duration blockTime;
        Duration flightTime;
        Duration taxiOutTime;
        Duration taxiInTime;
        String departureLocation;
        String arrivalLocation;
        boolean complete;

        public long getBlockTimeMinutes() {
            return blockTime.toMinutes();
        }

        public long getFlightTimeMinutes() {
            return flightTime.toMinutes();
        }
    }

    /**
     * Listener interface for OOOI events.
     */
    public interface OooiEventListener {
        void onOooiEvent(String aircraftRegistration, OooiEvent event, FlightSession session);
        void onFlightComplete(String aircraftRegistration, FlightSession session, FlightSummary summary);
    }
}
