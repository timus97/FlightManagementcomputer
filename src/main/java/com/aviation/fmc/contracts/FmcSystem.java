package com.aviation.fmc.contracts;

import com.aviation.fmc.parser.FlightPlan;
import com.aviation.fmc.validation.ValidationResult;

import java.util.Optional;

/**
 * Core contract for the Flight Management Computer (FMC) system.
 * Defines the primary interface for flight management operations.
 * 
 * The FMC system manages flight plans, navigation, performance calculations,
 * and guidance commands for automatic flight control.
 */
public interface FmcSystem {

    /**
     * Initialize the FMC system.
     * Must be called before any other operations.
     * 
     * @param configuration System configuration parameters
     * @return Initialization result
     */
    InitializationResult initialize(FmcConfiguration configuration);

    /**
     * Load a flight plan into the FMC.
     * 
     * @param flightPlan The flight plan to load
     * @return Result of the load operation
     */
    LoadResult loadFlightPlan(FlightPlan flightPlan);

    /**
     * Get the currently active flight plan.
     * 
     * @return Optional containing the active flight plan
     */
    Optional<FlightPlan> getActiveFlightPlan();

    /**
     * Activate the loaded flight plan.
     * Validates the flight plan and prepares it for execution.
     * 
     * @return Activation result
     */
    ActivationResult activateFlightPlan();

    /**
     * Deactivate the current flight plan.
     */
    void deactivateFlightPlan();

    /**
     * Get current FMC status.
     * 
     * @return System status
     */
    FmcStatus getStatus();

    /**
     * Get the navigation computer interface.
     */
    NavigationComputer getNavigationComputer();

    /**
     * Get the performance computer interface.
     */
    PerformanceComputer getPerformanceComputer();

    /**
     * Get the guidance computer interface.
     */
    GuidanceComputer getGuidanceComputer();

    /**
     * Shutdown the FMC system.
     */
    void shutdown();

    /**
     * FMC System configuration.
     */
    record FmcConfiguration(
        String aircraftType,
        double maxTakeoffWeight,
        double maxLandingWeight,
        double fuelCapacity,
        NavigationDatabase navDatabase,
        boolean useMetricUnits
    ) {}

    /**
     * Navigation database reference.
     */
    record NavigationDatabase(
        String version,
        String effectiveDate,
        String airacCycle
    ) {}

    /**
     * Initialization result.
     */
    record InitializationResult(
        boolean success,
        String message,
        ValidationResult validationResult
    ) {
        public static InitializationResult success(String message) {
            return new InitializationResult(true, message, ValidationResult.success());
        }
        
        public static InitializationResult failure(String message, ValidationResult validation) {
            return new InitializationResult(false, message, validation);
        }
    }

    /**
     * Flight plan load result.
     */
    record LoadResult(
        boolean success,
        String message,
        ValidationResult validationResult
    ) {
        public static LoadResult success(String message) {
            return new LoadResult(true, message, ValidationResult.success());
        }
        
        public static LoadResult failure(String message, ValidationResult validation) {
            return new LoadResult(false, message, validation);
        }
    }

    /**
     * Flight plan activation result.
     */
    record ActivationResult(
        boolean success,
        String message,
        FlightPhase initialPhase
    ) {
        public static ActivationResult success(FlightPhase phase) {
            return new ActivationResult(true, "Flight plan activated", phase);
        }
        
        public static ActivationResult failure(String message) {
            return new ActivationResult(false, message, null);
        }
    }

    /**
     * Flight phases.
     */
    enum FlightPhase {
        PREFLIGHT,
        TAKEOFF,
        CLIMB,
        CRUISE,
        DESCENT,
        APPROACH,
        LANDING,
        TAXI,
        COMPLETED
    }

    /**
     * FMC System status.
     */
    record FmcStatus(
        SystemState state,
        FlightPhase currentPhase,
        boolean flightPlanActive,
        String activeWaypoint,
        double distanceToNextWaypoint,
        double estimatedFuelOnBoard,
        int messages
    ) {
        public enum SystemState {
            INITIALIZING,
            READY,
            ACTIVE,
            DEGRADED,
            FAILED
        }
    }
}
