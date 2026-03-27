package com.aviation.fmc.acars;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OOOI Dashboard - Real-time display of flight status and OOOI events.
 * Provides airline operations center style visibility into flight progress.
 */
@Slf4j
public class OooiDashboard implements OooiEventHandler.OooiEventListener {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(java.time.ZoneOffset.UTC);

    private final OooiEventHandler handler;
    private final Map<String, FlightDisplay> flightDisplays;

    public OooiDashboard(OooiEventHandler handler) {
        this.handler = handler;
        this.flightDisplays = new LinkedHashMap<>();
        this.handler.addListener(this);
    }

    @Override
    public void onOooiEvent(String aircraftRegistration,
                            OooiEventHandler.OooiEvent event,
                            OooiEventHandler.FlightSession session) {
        FlightDisplay display = flightDisplays.computeIfAbsent(
                aircraftRegistration,
                k -> new FlightDisplay(aircraftRegistration)
        );

        display.updateWithEvent(event);
        logOooiEvent(aircraftRegistration, event);
    }

    @Override
    public void onFlightComplete(String aircraftRegistration,
                                  OooiEventHandler.FlightSession session,
                                  OooiEventHandler.FlightSummary summary) {
        FlightDisplay display = flightDisplays.get(aircraftRegistration);
        if (display != null) {
            display.markComplete(summary);
        }

        logFlightComplete(aircraftRegistration, summary);
    }

    /**
     * Display current active flights table.
     */
    public void displayActiveFlights() {
        List<FlightDisplay> active = flightDisplays.values().stream()
                .filter(d -> !d.isComplete())
                .collect(Collectors.toList());

        if (active.isEmpty()) {
            log.info("No active flights");
            return;
        }

        log.info("================================================================================");
        log.info("                           ACTIVE FLIGHTS - OOOI STATUS                         ");
        log.info("================================================================================");
        log.info(String.format("%-10s %-8s %-8s %-8s %-8s %-12s %-12s",
                "Aircraft", "OUT", "OFF", "ON", "IN", "Status", "Duration"));
        log.info("--------------------------------------------------------------------------------");

        for (FlightDisplay flight : active) {
            log.info(String.format("%-10s %-8s %-8s %-8s %-8s %-12s %-12s",
                    flight.getAircraftRegistration(),
                    flight.getOutTime() != null ? TIME_FORMAT.format(flight.getOutTime()) : "--:--:--",
                    flight.getOffTime() != null ? TIME_FORMAT.format(flight.getOffTime()) : "--:--:--",
                    flight.getOnTime() != null ? TIME_FORMAT.format(flight.getOnTime()) : "--:--:--",
                    flight.getInTime() != null ? TIME_FORMAT.format(flight.getInTime()) : "--:--:--",
                    flight.getCurrentStatus(),
                    flight.getElapsedTime()));
        }

        log.info("================================================================================");
    }

    /**
     * Display flight summary statistics.
     */
    public void displayStatistics() {
        List<OooiEventHandler.FlightSession> active = handler.getAllActiveFlights();
        long totalFlights = flightDisplays.size();
        long activeCount = active.size();
        long completedCount = totalFlights - activeCount;

        log.info("");
        log.info("============================= OOOI STATISTICS ==================================");
        log.info("Total Flights Tracked: {}", totalFlights);
        log.info("Active Flights: {}", activeCount);
        log.info("Completed Flights: {}", completedCount);

        List<OooiEventHandler.FlightSummary> summaries = flightDisplays.values().stream()
                .filter(FlightDisplay::isComplete)
                .map(FlightDisplay::getSummary)
                .filter(Objects::nonNull)
                .toList();

        if (!summaries.isEmpty()) {
            Duration avgBlockTime = summaries.stream()
                    .map(OooiEventHandler.FlightSummary::getBlockTime)
                    .reduce(Duration.ZERO, Duration::plus)
                    .dividedBy(summaries.size());

            Duration avgFlightTime = summaries.stream()
                    .map(OooiEventHandler.FlightSummary::getFlightTime)
                    .reduce(Duration.ZERO, Duration::plus)
                    .dividedBy(summaries.size());

            log.info("Average Block Time: {}", formatDuration(avgBlockTime));
            log.info("Average Flight Time: {}", formatDuration(avgFlightTime));
        }

        log.info("================================================================================");
    }

    /**
     * Display detailed flight information.
     */
    public void displayFlightDetails(String aircraftRegistration) {
        FlightDisplay display = flightDisplays.get(aircraftRegistration);
        if (display == null) {
            log.info("No flight data for aircraft {}", aircraftRegistration);
            return;
        }

        log.info("");
        log.info("============================ FLIGHT DETAILS ====================================");
        log.info("Aircraft: {}", aircraftRegistration);
        log.info("Current Status: {}", display.getCurrentStatus());
        log.info("");

        if (display.getOutTime() != null) {
            log.info("OUT Time:  {} UTC", TIME_FORMAT.format(display.getOutTime()));
        }
        if (display.getOffTime() != null) {
            log.info("OFF Time:  {} UTC", TIME_FORMAT.format(display.getOffTime()));
        }
        if (display.getOnTime() != null) {
            log.info("ON Time:   {} UTC", TIME_FORMAT.format(display.getOnTime()));
        }
        if (display.getInTime() != null) {
            log.info("IN Time:   {} UTC", TIME_FORMAT.format(display.getInTime()));
        }

        if (display.isComplete() && display.getSummary() != null) {
            OooiEventHandler.FlightSummary summary = display.getSummary();
            log.info("");
            log.info("BLOCK TIME:  {} ({} minutes)",
                    formatDuration(summary.getBlockTime()),
                    summary.getBlockTimeMinutes());
            log.info("FLIGHT TIME: {} ({} minutes)",
                    formatDuration(summary.getFlightTime()),
                    summary.getFlightTimeMinutes());
            log.info("TAXI OUT:    {}", formatDuration(summary.getTaxiOutTime()));
            log.info("TAXI IN:     {}", formatDuration(summary.getTaxiInTime()));
        }

        log.info("================================================================================");
    }

    private void logOooiEvent(String aircraftRegistration,
                               OooiEventHandler.OooiEvent event) {
        String timeStr = TIME_FORMAT.format(event.getTimestamp());
        String position = String.format("%.4f/%.4f",
                event.getPosition().getLatitude(),
                event.getPosition().getLongitude());

        log.info("[OOOI] {} {} {} at {} ALT{}",
                aircraftRegistration,
                event.getType(),
                timeStr,
                position,
                (int) event.getAltitude());
    }

    private void logFlightComplete(String aircraftRegistration,
                                    OooiEventHandler.FlightSummary summary) {
        log.info("");
        log.info("[FLIGHT COMPLETE] {}", aircraftRegistration);
        log.info("  Block Time:  {} ({} min)",
                formatDuration(summary.getBlockTime()),
                summary.getBlockTimeMinutes());
        log.info("  Flight Time: {} ({} min)",
                formatDuration(summary.getFlightTime()),
                summary.getFlightTimeMinutes());
        log.info("  From: {} To: {}",
                summary.getDepartureLocation(),
                summary.getArrivalLocation());
    }

    private String formatDuration(Duration duration) {
        if (duration == null || duration.isZero()) {
            return "00:00:00";
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static class FlightDisplay {
        private final String aircraftRegistration;
        private java.time.Instant outTime;
        private java.time.Instant offTime;
        private java.time.Instant onTime;
        private java.time.Instant inTime;
        private OooiEventHandler.OooiEventType currentPhase;
        private boolean complete;
        private OooiEventHandler.FlightSummary summary;

        FlightDisplay(String aircraftRegistration) {
            this.aircraftRegistration = aircraftRegistration;
        }

        void updateWithEvent(OooiEventHandler.OooiEvent event) {
            this.currentPhase = event.getType();
            switch (event.getType()) {
                case OUT -> outTime = event.getTimestamp();
                case OFF -> offTime = event.getTimestamp();
                case ON -> onTime = event.getTimestamp();
                case IN -> inTime = event.getTimestamp();
            }
        }

        void markComplete(OooiEventHandler.FlightSummary summary) {
            this.complete = true;
            this.summary = summary;
        }

        String getAircraftRegistration() {
            return aircraftRegistration;
        }

        java.time.Instant getOutTime() {
            return outTime;
        }

        java.time.Instant getOffTime() {
            return offTime;
        }

        java.time.Instant getOnTime() {
            return onTime;
        }

        java.time.Instant getInTime() {
            return inTime;
        }

        String getCurrentStatus() {
            if (currentPhase == null) return "UNKNOWN";
            return switch (currentPhase) {
                case OUT -> "TAXI OUT";
                case OFF -> "IN FLIGHT";
                case ON -> "TAXI IN";
                case IN -> "ARRIVED";
            };
        }

        String getElapsedTime() {
            java.time.Instant start = outTime;
            java.time.Instant end = complete ? inTime : java.time.Instant.now();

            if (start == null) return "00:00:00";
            if (end == null) end = java.time.Instant.now();

            Duration elapsed = Duration.between(start, end);
            long hours = elapsed.toHours();
            long minutes = elapsed.toMinutesPart();
            long seconds = elapsed.toSecondsPart();
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }

        boolean isComplete() {
            return complete;
        }

        OooiEventHandler.FlightSummary getSummary() {
            return summary;
        }
    }
}
