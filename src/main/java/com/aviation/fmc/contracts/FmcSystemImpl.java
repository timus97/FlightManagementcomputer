package com.aviation.fmc.contracts;

import com.aviation.fmc.parser.FlightPlan;
import com.aviation.fmc.validation.ValidationResult;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of the Flight Management Computer (FMC) System.
 * 
 * This is the central coordinator that integrates all three computer subsystems:
 * - NavigationComputer: Position, waypoint sequencing, route management
 * - PerformanceComputer: Fuel, speed, altitude calculations
 * - GuidanceComputer: LNAV/VNAV steering commands
 * 
 * The FmcSystemImpl manages:
 * - System initialization and configuration
 * - Flight plan loading and activation
 * - Flight phase state machine (PREFLIGHT → TAKEOFF → CLIMB → CRUISE → DESCENT → APPROACH → LANDING)
 * - Cross-computer coordination
 * - System status reporting
 */
public class FmcSystemImpl implements FmcSystem {

    // Subsystem computers
    private NavigationComputer navigationComputer;
    private PerformanceComputer performanceComputer;
    private GuidanceComputer guidanceComputer;

    // System state
    private final AtomicReference<FmcSystem.FmcStatus.SystemState> systemState;
    private final AtomicReference<FlightPhase> currentPhase;
    private FlightPlan activeFlightPlan;
    private FmcConfiguration configuration;
    private boolean initialized = false;

    // Flight plan load state
    private boolean flightPlanLoaded = false;
    private boolean flightPlanActive = false;

    // Message counter (simulated ACARS/datalink messages)
    private int messageCount = 0;

    /**
     * Default constructor - creates a new FMC system (not initialized).
     */
    public FmcSystemImpl() {
        this.systemState = new AtomicReference<>(FmcSystem.FmcStatus.SystemState.INITIALIZING);
        this.currentPhase = new AtomicReference<>(FlightPhase.PREFLIGHT);
    }

    @Override
    public InitializationResult initialize(FmcConfiguration configuration) {
        if (configuration == null) {
            return InitializationResult.failure("Configuration cannot be null",
                ValidationResult.failure(ValidationResult.ValidationError.of(
                    "FMC_CONFIG_NULL", "Configuration is required")));
        }

        // Store configuration
        this.configuration = configuration;

        // Create the three computer subsystems
        this.navigationComputer = new NavigationComputerImpl();
        this.performanceComputer = new PerformanceComputerImpl();
        this.guidanceComputer = new GuidanceComputerImpl(navigationComputer);

        // Initialize the performance computer with aircraft weights
        double zeroFuelWeight = calculateZeroFuelWeight(configuration);
        double initialFuelOnBoard = calculateInitialFuelOnBoard(configuration);
        double centerOfGravity = 25.0; // Default CG at 25% MAC
        
        performanceComputer.initialize(zeroFuelWeight, initialFuelOnBoard, centerOfGravity);

        // Set aircraft limits on performance computer
        if (performanceComputer instanceof PerformanceComputerImpl) {
            ((PerformanceComputerImpl) performanceComputer).setAircraftLimits(
                configuration.maxTakeoffWeight(),
                configuration.maxLandingWeight(),
                zeroFuelWeight,
                configuration.fuelCapacity()
            );
        }

        // Mark as initialized and ready
        this.initialized = true;
        this.systemState.set(FmcSystem.FmcStatus.SystemState.READY);
        this.currentPhase.set(FlightPhase.PREFLIGHT);

        return InitializationResult.success(
            String.format("FMC initialized for aircraft %s (MTOW: %.0f kg, Fuel: %.0f kg)",
                configuration.aircraftType(),
                configuration.maxTakeoffWeight(),
                configuration.fuelCapacity()));
    }

    @Override
    public LoadResult loadFlightPlan(FlightPlan flightPlan) {
        if (!initialized) {
            return LoadResult.failure("FMC not initialized. Call initialize() first.",
                ValidationResult.failure(ValidationResult.ValidationError.of(
                    "FMC_NOT_INITIALIZED", "System must be initialized before loading flight plan")));
        }

        if (flightPlan == null) {
            return LoadResult.failure("Flight plan cannot be null",
                ValidationResult.failure(ValidationResult.ValidationError.of(
                    "FLIGHTPLAN_NULL", "Flight plan is required")));
        }

        // Validate the flight plan
        ValidationResult validation = validateFlightPlan(flightPlan);
        if (!validation.isValid()) {
            return LoadResult.failure("Flight plan validation failed", validation);
        }

        // Store the flight plan
        this.activeFlightPlan = flightPlan;
        this.flightPlanLoaded = true;

        // Load into navigation computer
        if (navigationComputer instanceof NavigationComputerImpl) {
            ((NavigationComputerImpl) navigationComputer).loadFlightPlan(flightPlan);
        }

        // Update performance computer with flight plan data
        updatePerformanceFromFlightPlan(flightPlan);

        // Transition to active state
        this.systemState.set(FmcSystem.FmcStatus.SystemState.ACTIVE);

        return LoadResult.success(
            String.format("Flight plan loaded: %s (%s → %s, %.1f NM)",
                flightPlan.getFlightNumber(),
                flightPlan.getDeparture().getIcaoCode(),
                flightPlan.getDestination().getIcaoCode(),
                flightPlan.getTotalDistance()));
    }

    @Override
    public Optional<FlightPlan> getActiveFlightPlan() {
        return Optional.ofNullable(activeFlightPlan);
    }

    @Override
    public ActivationResult activateFlightPlan() {
        if (!flightPlanLoaded || activeFlightPlan == null) {
            return ActivationResult.failure("No flight plan loaded");
        }

        // Validate flight plan is ready for activation
        if (!activeFlightPlan.isValidForActivation()) {
            return ActivationResult.failure("Flight plan is not valid for activation");
        }

        // Set flight plan status to active
        flightPlanActive = true;

        // Initialize guidance for LNAV/VNAV
        guidanceComputer.setLnavEnabled(true);
        guidanceComputer.setVnavEnabled(true);

        // Set initial phase
        currentPhase.set(FlightPhase.PREFLIGHT);

        // Update system state
        systemState.set(FmcSystem.FmcStatus.SystemState.ACTIVE);

        return ActivationResult.success(FlightPhase.PREFLIGHT);
    }

    @Override
    public void deactivateFlightPlan() {
        flightPlanActive = false;
        activeFlightPlan = null;
        flightPlanLoaded = false;

        // Reset computers
        if (navigationComputer instanceof NavigationComputerImpl) {
            // NavigationComputerImpl will handle reset on next load
        }

        // Disable guidance modes
        guidanceComputer.setLnavEnabled(false);
        guidanceComputer.setVnavEnabled(false);

        currentPhase.set(FlightPhase.PREFLIGHT);
        systemState.set(FmcSystem.FmcStatus.SystemState.READY);
    }

    @Override
    public FmcStatus getStatus() {
        String activeWaypoint = "NONE";
        double distanceToNext = 0.0;

        if (navigationComputer != null) {
            var activeWp = navigationComputer.getActiveWaypoint();
            if (activeWp.isPresent()) {
                activeWaypoint = activeWp.get().getIdentifier();
            }
            distanceToNext = navigationComputer.distanceToActiveWaypoint();
        }

        double fuelOnBoard = 0.0;
        if (performanceComputer != null) {
            fuelOnBoard = performanceComputer.getFuelOnBoard();
        }

        return new FmcStatus(
            systemState.get(),
            currentPhase.get(),
            flightPlanActive,
            activeWaypoint,
            distanceToNext,
            fuelOnBoard,
            messageCount
        );
    }

    @Override
    public NavigationComputer getNavigationComputer() {
        return navigationComputer;
    }

    @Override
    public PerformanceComputer getPerformanceComputer() {
        return performanceComputer;
    }

    @Override
    public GuidanceComputer getGuidanceComputer() {
        return guidanceComputer;
    }

    @Override
    public void shutdown() {
        // Disable all guidance modes
        if (guidanceComputer != null) {
            guidanceComputer.setLnavEnabled(false);
            guidanceComputer.setVnavEnabled(false);
            guidanceComputer.disengageApproach();
        }

        // Reset state
        systemState.set(FmcSystem.FmcStatus.SystemState.FAILED);
        currentPhase.set(FlightPhase.COMPLETED);
        flightPlanActive = false;
        initialized = false;
    }

    // ==================== Additional Public Methods ====================

    /**
     * Update the current aircraft position.
     * This is called by the simulation engine or external systems.
     * 
     * @param position Current position
     * @param altitude Current altitude in feet
     * @param heading Current heading in degrees
     * @param groundspeed Current groundspeed in knots
     */
    public void updatePosition(com.aviation.fmc.common.GeoCoordinate position,
                               double altitude, double heading, double groundspeed) {
        if (navigationComputer != null) {
            navigationComputer.updatePosition(position, altitude, heading, groundspeed);
        }

        // Update guidance computer state
        if (guidanceComputer instanceof GuidanceComputerImpl) {
            ((GuidanceComputerImpl) guidanceComputer).updateState(altitude, groundspeed, 0.0, heading);
        }

        // Update flight phase based on altitude and position
        updateFlightPhase(altitude);
    }

    /**
     * Transition to the next flight phase.
     * Called by simulation or manually for testing.
     */
    public void advanceFlightPhase() {
        FlightPhase current = currentPhase.get();
        FlightPhase next = getNextPhase(current);
        currentPhase.set(next);

        // Handle phase-specific actions
        onPhaseTransition(current, next);
    }

    /**
     * Set the current flight phase directly.
     */
    public void setFlightPhase(FlightPhase phase) {
        FlightPhase previous = currentPhase.get();
        currentPhase.set(phase);
        onPhaseTransition(previous, phase);
    }

    /**
     * Get current flight phase.
     */
    public FlightPhase getCurrentPhase() {
        return currentPhase.get();
    }

    /**
     * Get system state.
     */
    public FmcSystem.FmcStatus.SystemState getSystemState() {
        return systemState.get();
    }

    /**
     * Increment message count (simulating ACARS/datalink activity).
     */
    public void incrementMessageCount() {
        messageCount++;
    }

    /**
     * Check if system is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    // ==================== Private Helper Methods ====================

    private double calculateZeroFuelWeight(FmcConfiguration config) {
        // ZFW = MTOW - fuel capacity (simplified)
        // In reality, ZFW = MTOW - fuel - payload
        return config.maxTakeoffWeight() - config.fuelCapacity() * 0.8; // Assume 80% fuel used
    }

    private double calculateInitialFuelOnBoard(FmcConfiguration config) {
        // Start with full fuel for simulation
        return config.fuelCapacity();
    }

    private ValidationResult validateFlightPlan(FlightPlan flightPlan) {
        // Basic validation - in production would use NavdataValidator
        if (flightPlan.getDeparture() == null) {
            return ValidationResult.failure(
                ValidationResult.ValidationError.of("FPL_NO_DEPARTURE", "Departure airport required"));
        }
        if (flightPlan.getDestination() == null) {
            return ValidationResult.failure(
                ValidationResult.ValidationError.of("FPL_NO_DESTINATION", "Destination airport required"));
        }
        if (flightPlan.getRoute() == null || flightPlan.getRoute().isEmpty()) {
            return ValidationResult.failure(
                ValidationResult.ValidationError.of("FPL_NO_ROUTE", "Route must have at least one element"));
        }
        return ValidationResult.success();
    }

    private void updatePerformanceFromFlightPlan(FlightPlan flightPlan) {
        if (performanceComputer == null || flightPlan == null) {
            return;
        }

        // Update fuel requirements based on flight plan distance
        double totalDistance = flightPlan.getTotalDistance();
        double cruiseAltitude = flightPlan.getCruiseAltitude() != null 
            ? flightPlan.getCruiseAltitude().getValue() 
            : 35000.0;
        double mach = 0.78; // Default cruise Mach

        // Calculate required fuel
        double fuelRequired = performanceComputer.calculateFuelRequired(totalDistance, cruiseAltitude, mach);

        // If we have less fuel than required, adjust (for demo purposes just log)
        // In real system this would trigger alerts
    }

    private void updateFlightPhase(double altitude) {
        FlightPhase current = currentPhase.get();

        // Simple phase transition logic based on altitude
        // In real system this would be more sophisticated (position-based, etc.)
        FlightPhase newPhase = current;

        if (altitude < 1000 && current == FlightPhase.CLIMB) {
            newPhase = FlightPhase.TAKEOFF;
        } else if (altitude >= 1000 && altitude < 10000 && current == FlightPhase.TAKEOFF) {
            newPhase = FlightPhase.CLIMB;
        } else if (altitude >= 30000 && (current == FlightPhase.CLIMB || current == FlightPhase.TAKEOFF)) {
            newPhase = FlightPhase.CRUISE;
        } else if (altitude < 30000 && current == FlightPhase.CRUISE) {
            newPhase = FlightPhase.DESCENT;
        } else if (altitude < 10000 && current == FlightPhase.DESCENT) {
            newPhase = FlightPhase.APPROACH;
        } else if (altitude < 1000 && current == FlightPhase.APPROACH) {
            newPhase = FlightPhase.LANDING;
        } else if (altitude < 100 && current == FlightPhase.LANDING) {
            newPhase = FlightPhase.COMPLETED;
        }

        if (newPhase != current) {
            currentPhase.set(newPhase);
        }
    }

    private FlightPhase getNextPhase(FlightPhase current) {
        return switch (current) {
            case PREFLIGHT -> FlightPhase.TAKEOFF;
            case TAKEOFF -> FlightPhase.CLIMB;
            case CLIMB -> FlightPhase.CRUISE;
            case CRUISE -> FlightPhase.DESCENT;
            case DESCENT -> FlightPhase.APPROACH;
            case APPROACH -> FlightPhase.LANDING;
            case LANDING -> FlightPhase.COMPLETED;
            case TAXI -> FlightPhase.TAKEOFF;
            case COMPLETED -> FlightPhase.PREFLIGHT;
        };
    }

    private void onPhaseTransition(FlightPhase from, FlightPhase to) {
        // Phase-specific actions
        switch (to) {
            case TAKEOFF -> {
                // Set flaps for takeoff
                guidanceComputer.setFlapsPosition(5);
            }
            case CRUISE -> {
                // Optimize cruise settings
                guidanceComputer.setTargetAltitude(35000);
            }
            case APPROACH -> {
                // Arm approach mode
                guidanceComputer.armApproach("ILS");
            }
            case LANDING -> {
                // Configure for landing
                guidanceComputer.setFlapsPosition(30);
                guidanceComputer.setGearDown(true);
            }
            case COMPLETED -> {
                // Flight complete
                flightPlanActive = false;
            }
            default -> {}
        }
    }
}
