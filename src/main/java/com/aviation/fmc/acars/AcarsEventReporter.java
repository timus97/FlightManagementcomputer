package com.aviation.fmc.acars;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automatic event reporter for ACARS.
 * Monitors aircraft systems and sends automatic reports based on thresholds and schedules.
 */
@Slf4j
public class AcarsEventReporter {

    private final AircraftAcarsUnit acarsUnit;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;

    // Thresholds for automatic reporting
    private double engineEgtThreshold = 650.0;  // degrees C
    private double fuelFlowVarianceThreshold = 10.0; // percent
    private double vibrationThreshold = 4.0;    // units

    // Last reported values for change detection
    private AircraftAcarsUnit.EngineData lastEngineData;
    private double lastFuelOnBoard;

    public AcarsEventReporter(AircraftAcarsUnit acarsUnit) {
        this.acarsUnit = acarsUnit;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.running = new AtomicBoolean(false);
    }

    /**
     * Start automatic event reporting.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            // Schedule periodic engine reports
            scheduler.scheduleAtFixedRate(
                    this::checkAndReportEngineData,
                    60, 300, TimeUnit.SECONDS); // Every 5 minutes

            log.info("ACARS event reporter started for {}",
                    acarsUnit.getIdentity().registration());
        }
    }

    /**
     * Stop automatic event reporting.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdownNow();
            log.info("ACARS event reporter stopped");
        }
    }

    /**
     * Report engine data if significant changes detected.
     */
    public void reportEngineData(AircraftAcarsUnit.EngineData data) {
        if (!running.get()) return;

        boolean shouldReport = false;

        if (lastEngineData != null) {
            // Check for significant changes
            if (Math.abs(data.egt() - lastEngineData.egt()) > 50) {
                shouldReport = true;
            }
            if (Math.abs(data.n1() - lastEngineData.n1()) > 5) {
                shouldReport = true;
            }
            if (data.vibration() > vibrationThreshold) {
                shouldReport = true;
                log.warn("High engine vibration detected: {}", data.vibration());
            }
        } else {
            // First report
            shouldReport = true;
        }

        if (shouldReport) {
            acarsUnit.sendEngineReport(data);
            lastEngineData = data;
        }
    }

    /**
     * Report abnormal condition.
     */
    public void reportAbnormal(String condition, String details) {
        if (!running.get()) return;

        String message = String.format("ABNORMAL: %s - %s", condition, details);
        acarsUnit.sendFreeText(message, "MAINT");
        log.warn("Abnormal condition reported: {}", condition);
    }

    /**
     * Report emergency condition (highest priority).
     */
    public void reportEmergency(String nature, String details) {
        // Emergency reports bypass normal queuing
        AcarsMessage emergencyMsg = AcarsMessage.builder()
                .direction(AcarsMessage.Direction.DOWNLINK)
                .label(AcarsMessage.Label.FREE_TEXT)
                .priority(AcarsMessage.Priority.EMERGENCY)
                .medium(AcarsMessage.Medium.SATCOM) // Use SATCOM for reliability
                .aircraftRegistration(acarsUnit.getIdentity().registration())
                .messageText(String.format("EMERGENCY: %s - %s", nature, details))
                .createdAt(Instant.now())
                .build();

        // TODO: Send with highest priority
        log.error("EMERGENCY reported: {} - {}", nature, details);
    }

    /**
     * Set thresholds for automatic reporting.
     */
    public void setThresholds(double egtThreshold, double vibrationThreshold) {
        this.engineEgtThreshold = egtThreshold;
        this.vibrationThreshold = vibrationThreshold;
    }

    // ==================== Private Methods ====================

    private void checkAndReportEngineData() {
        // In a real implementation, would read from aircraft systems
        // For simulation, we rely on explicit calls to reportEngineData
    }
}
