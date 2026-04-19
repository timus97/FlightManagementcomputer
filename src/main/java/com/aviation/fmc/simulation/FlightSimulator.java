package com.aviation.fmc.simulation;

import com.aviation.fmc.common.GeoCoordinate;
import com.aviation.fmc.contracts.FmcSystem;
import com.aviation.fmc.contracts.FmcSystem.FlightPhase;
import com.aviation.fmc.contracts.GuidanceComputer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Deterministic flight simulator with step-based execution.
 * 
 * This is the main coordinator that:
 * - Manages simulation time via SimulationClock
 * - Integrates FMC guidance with physics propagation
 * - Provides deterministic step-by-step execution
 * - Supports event subscribers for state updates
 * 
 * DETERMINISM GUARANTEE:
 * Given the same initial state and same number of steps,
 * the simulation will always produce the same result.
 * No threads, no timers, no randomness.
 * 
 * Assumptions:
 * - FMC is already initialized and has a loaded flight plan
 * - Caller controls execution pace (step, runSteps, runUntil)
 * - Time acceleration affects physics, not execution rate
 * - FMC position updates happen after each step
 */
public class FlightSimulator {
    
    private final FmcSystem fmc;
    private final PhysicsEngine physics;
    private final AutopilotCoupler autopilot;
    private final SimulationClock clock;
    private final SimulationConfig config;
    
    private AircraftState currentState;
    private boolean initialized = false;
    
    private final List<Consumer<AircraftState>> stateListeners;
    private final List<Consumer<SimulationEvent>> eventListeners;
    
    public FlightSimulator(FmcSystem fmc, SimulationConfig config) {
        this.fmc = fmc;
        this.config = config;
        this.physics = new PhysicsEngine();
        this.autopilot = new AutopilotCoupler(fmc.getGuidanceComputer());
        this.clock = new SimulationClock();
        this.clock.setTimeAcceleration(config.timeAcceleration());
        this.stateListeners = new ArrayList<>();
        this.eventListeners = new ArrayList<>();
    }
    
    /**
     * Initialize the simulator with starting state.
     * Must be called before any step() calls.
     */
    public void initialize(AircraftState initialState) {
        this.currentState = initialState;
        this.clock.reset();
        this.clock.setTimeAcceleration(config.timeAcceleration());
        this.physics.reset();
        this.initialized = true;
        
        // Update FMC with initial position
        updateFmcPosition(initialState);
        
        // Notify listeners
        notifyStateListeners(initialState);
    }
    
    /**
     * Execute exactly one simulation step.
     * Deterministic: same inputs always produce same outputs.
     */
    public void step() {
        step(config.tickNanos());
    }
    
    /**
     * Execute one step with specified delta time.
     * Useful for variable-step scenarios.
     */
    public void step(long deltaNanos) {
        ensureInitialized();
        
        // Calculate effective delta time accounting for time acceleration
        double effectiveDeltaSeconds = (deltaNanos / 1_000_000_000.0) * config.timeAcceleration();
        long effectiveDeltaNanos = (long)(deltaNanos * config.timeAcceleration());
        
        // 1. Advance clock by effective time (deterministic)
        clock.advance(effectiveDeltaNanos);
        
        // 2. Get autopilot targets from FMC guidance
        AutopilotTargets targets = autopilot.process(currentState);
        
        // 3. Propagate physics (pure function)
        currentState = physics.propagate(currentState, targets, effectiveDeltaSeconds);
        
        // 4. Update FMC with new position
        updateFmcPosition(currentState);
        
        // 5. Check for waypoint sequencing
        checkWaypointSequencing();
        
        // 6. Update FMC flight phase
        updateFmcPhase();
        
        // 7. Notify listeners
        notifyStateListeners(currentState);
    }
    
    /**
     * Run multiple steps - useful for batch testing.
     */
    public void runSteps(int count) {
        for (int i = 0; i < count; i++) {
            step();
        }
    }
    
    /**
     * Run until a condition is met or max steps reached.
     */
    public void runUntil(java.util.function.Predicate<AircraftState> condition, 
                         long maxSteps) {
        long steps = 0;
        while (!condition.test(currentState) && steps < maxSteps) {
            step();
            steps++;
        }
    }
    
    /**
     * Run until simulation time reaches target.
     */
    public void runUntilTime(double targetSeconds) {
        long targetNanos = (long)(targetSeconds * 1_000_000_000L);
        while (clock.getSimulationTimeNanos() < targetNanos) {
            step();
        }
    }
    
    /**
     * Subscribe to state updates.
     */
    public void subscribeToState(Consumer<AircraftState> listener) {
        stateListeners.add(listener);
    }
    
    /**
     * Subscribe to simulation events.
     */
    public void subscribeToEvents(Consumer<SimulationEvent> listener) {
        eventListeners.add(listener);
    }
    
    // ==================== Getters ====================
    
    public AircraftState getCurrentState() {
        return currentState;
    }
    
    public SimulationClock getClock() {
        return clock;
    }
    
    public FmcSystem getFmc() {
        return fmc;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public SimulationConfig getConfig() {
        return config;
    }
    
    // ==================== Private Methods ====================
    
    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                "Simulator not initialized. Call initialize() first.");
        }
    }
    
    private void updateFmcPosition(AircraftState state) {
        if (fmc instanceof com.aviation.fmc.contracts.FmcSystemImpl fmcImpl) {
            fmcImpl.updatePosition(
                state.getPosition(),
                state.getAltitude(),
                state.getHeading(),
                state.getGroundSpeed()
            );
        }
    }
    
    private void updateFmcPhase() {
        if (fmc instanceof com.aviation.fmc.contracts.FmcSystemImpl fmcImpl) {
            FlightPhase currentFmcPhase = fmcImpl.getCurrentPhase();
            FlightPhase simPhase = currentState.getPhase();
            
            if (simPhase != currentFmcPhase) {
                fmcImpl.setFlightPhase(simPhase);
                notifyEventListeners(new SimulationEvent.PhaseChange(
                    clock.getSimulationTimeNanos(),
                    currentFmcPhase,
                    simPhase
                ));
            }
        }
    }
    
    private void checkWaypointSequencing() {
        // FMC handles waypoint sequencing internally via NavigationComputer
        // We just need to ensure position is updated (done above)
    }
    
    private void notifyStateListeners(AircraftState state) {
        for (Consumer<AircraftState> listener : stateListeners) {
            listener.accept(state);
        }
    }
    
    private void notifyEventListeners(SimulationEvent event) {
        for (Consumer<SimulationEvent> listener : eventListeners) {
            listener.accept(event);
        }
    }
    
    // ==================== Builder for convenient construction ====================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private FmcSystem fmc;
        private SimulationConfig config = SimulationConfig.defaultConfig();
        private AircraftState initialState;
        
        public Builder fmc(FmcSystem fmc) {
            this.fmc = fmc;
            return this;
        }
        
        public Builder config(SimulationConfig config) {
            this.config = config;
            return this;
        }
        
        public Builder initialState(AircraftState state) {
            this.initialState = state;
            return this;
        }
        
        public FlightSimulator build() {
            if (fmc == null) {
                throw new IllegalArgumentException("FMC is required");
            }
            FlightSimulator sim = new FlightSimulator(fmc, config);
            if (initialState != null) {
                sim.initialize(initialState);
            }
            return sim;
        }
    }
}
