package com.aviation.fmc.simulation;

import com.aviation.fmc.contracts.FmcSystem.FlightPhase;

/**
 * Simulation events for tracking discrete occurrences.
 * 
 * Assumptions:
 * - Events are emitted at specific simulation times
 * - Events can be used for logging, debugging, and testing
 */
public sealed interface SimulationEvent {
    
    /** Simulation time when event occurred (nanoseconds) */
    long simulationTimeNanos();
    
    /**
     * Flight phase change event.
     */
    record PhaseChange(
        long simulationTimeNanos,
        FlightPhase fromPhase,
        FlightPhase toPhase
    ) implements SimulationEvent {}
    
    /**
     * Waypoint sequenced event.
     */
    record WaypointSequenced(
        long simulationTimeNanos,
        String waypointId,
        String nextWaypointId
    ) implements SimulationEvent {}
    
    /**
     * Altitude capture event.
     */
    record AltitudeCapture(
        long simulationTimeNanos,
        double capturedAltitude
    ) implements SimulationEvent {}
    
    /**
     * Simulation completed event.
     */
    record SimulationComplete(
        long simulationTimeNanos,
        String reason
    ) implements SimulationEvent {}
}
