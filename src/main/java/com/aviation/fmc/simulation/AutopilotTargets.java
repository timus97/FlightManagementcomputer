package com.aviation.fmc.simulation;

/**
 * Target values computed by the autopilot from FMC guidance commands.
 * 
 * Assumptions:
 * - Targets are computed each step from current guidance commands
 * - Values are in the same units as AircraftState
 * - Negative vertical speed means descent
 */
public record AutopilotTargets(
    /** Target track in degrees true */
    double targetTrack,
    
    /** Target ground speed in knots */
    double targetGroundSpeed,
    
    /** Target vertical speed in feet per minute */
    double targetVerticalSpeed,
    
    /** Target altitude in feet MSL */
    double targetAltitude,
    
    /** Target bank angle in degrees (for turning) */
    double targetBank,
    
    /** Whether altitude capture is active */
    boolean altitudeCapture
) {
    /**
     * Create default targets (maintain current state).
     */
    public static AutopilotTargets holdCurrent(AircraftState state) {
        return new AutopilotTargets(
            state.getTrack(),
            state.getGroundSpeed(),
            0.0,
            state.getAltitude(),
            0.0,
            true
        );
    }
}
