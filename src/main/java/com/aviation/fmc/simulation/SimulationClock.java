package com.aviation.fmc.simulation;

/**
 * Deterministic simulation clock - single source of truth for simulation time.
 * Decoupled from system time, fully controllable for testing.
 * 
 * Assumptions:
 * - Time advances ONLY via advance() method (no automatic progression)
 * - Time is stored in nanoseconds for precision but typically used in seconds
 * - Time acceleration affects how much simulation time passes per step,
 *   not the step rate itself
 */
public class SimulationClock {
    
    private long simulationTimeNanos;
    private double timeAcceleration;
    
    public SimulationClock() {
        this.simulationTimeNanos = 0;
        this.timeAcceleration = 1.0;
    }
    
    /**
     * Advance simulation time by a fixed amount.
     * This is the ONLY way time progresses - deterministic.
     * 
     * @param nanos Amount to advance in nanoseconds
     */
    public void advance(long nanos) {
        if (nanos < 0) {
            throw new IllegalArgumentException("Cannot advance time backwards");
        }
        this.simulationTimeNanos += nanos;
    }
    
    /**
     * Get current simulation time in nanoseconds.
     */
    public long getSimulationTimeNanos() {
        return simulationTimeNanos;
    }
    
    /**
     * Get current simulation time in seconds.
     */
    public double getSimulationTimeSeconds() {
        return simulationTimeNanos / 1_000_000_000.0;
    }
    
    /**
     * Get current simulation time in milliseconds.
     */
    public long getSimulationTimeMillis() {
        return simulationTimeNanos / 1_000_000;
    }
    
    /**
     * Set time acceleration factor.
     * 1.0 = real-time, 10.0 = 10x speed, 0.5 = half speed.
     * 
     * @param factor Acceleration factor (must be positive)
     */
    public void setTimeAcceleration(double factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("Time acceleration must be positive");
        }
        this.timeAcceleration = factor;
    }
    
    /**
     * Get current time acceleration factor.
     */
    public double getTimeAcceleration() {
        return timeAcceleration;
    }
    
    /**
     * Reset clock to initial state.
     */
    public void reset() {
        this.simulationTimeNanos = 0;
        this.timeAcceleration = 1.0;
    }
    
    /**
     * Create a snapshot for debugging/replay.
     */
    public Snapshot snapshot() {
        return new Snapshot(simulationTimeNanos, timeAcceleration);
    }
    
    /**
     * Restore from a snapshot.
     */
    public void restore(Snapshot snapshot) {
        this.simulationTimeNanos = snapshot.timeNanos();
        this.timeAcceleration = snapshot.timeAcceleration();
    }
    
    public record Snapshot(long timeNanos, double timeAcceleration) {}
}
