package com.aviation.fmc.simulation;

/**
 * Configuration for the flight simulation.
 * 
 * Assumptions:
 * - Default tick rate is 10Hz (100ms) - typical for FMC simulations
 * - Time acceleration is used for fast-forward scenarios
 * - Wind and turbulence models are optional enhancements
 */
public record SimulationConfig(
    /** Fixed simulation step in nanoseconds (default 100ms = 10Hz) */
    long tickNanos,
    
    /** Time acceleration factor (1.0 = real-time, 10.0 = 10x speed) */
    double timeAcceleration,
    
    /** Enable wind model for groundspeed calculations */
    boolean enableWindModel,
    
    /** Enable turbulence effects */
    boolean enableTurbulence,
    
    /** Aircraft type ICAO code */
    String aircraftType
) {
    /** Default tick: 100ms = 10Hz */
    public static final long DEFAULT_TICK_NANOS = 100_000_000L;
    
    /**
     * Create default configuration for real-time simulation.
     */
    public static SimulationConfig defaultConfig() {
        return new SimulationConfig(DEFAULT_TICK_NANOS, 1.0, false, false, "B738");
    }
    
    /**
     * Create configuration for fast-forward simulation.
     */
    public static SimulationConfig fastForward(double acceleration) {
        return new SimulationConfig(DEFAULT_TICK_NANOS, acceleration, false, false, "B738");
    }
    
    /**
     * Create configuration for testing (no wind/turbulence).
     */
    public static SimulationConfig testConfig() {
        return new SimulationConfig(DEFAULT_TICK_NANOS, 1.0, false, false, "B738");
    }
    
    /**
     * Get tick duration in seconds.
     */
    public double getTickSeconds() {
        return tickNanos / 1_000_000_000.0;
    }
    
    /**
     * Get effective tick duration accounting for time acceleration.
     */
    public double getEffectiveTickSeconds() {
        return getTickSeconds() * timeAcceleration;
    }
}
