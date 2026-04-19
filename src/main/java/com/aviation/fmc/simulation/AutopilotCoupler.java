package com.aviation.fmc.simulation;

import com.aviation.fmc.contracts.GuidanceComputer;
import com.aviation.fmc.contracts.GuidanceComputer.LateralCommand;
import com.aviation.fmc.contracts.GuidanceComputer.VerticalCommand;

/**
 * Converts FMC guidance commands into autopilot targets.
 * 
 * This class bridges the gap between the FMC's guidance computer
 * (which outputs LNAV/VNAV commands) and the physics engine
 * (which needs specific target values).
 * 
 * Assumptions:
 * - LNAV provides track guidance via cross-track error correction
 * - VNAV provides vertical speed or altitude targets
 * - Speed control is via VNAV or manual selection
 * - Bank angle is computed from track error
 */
public class AutopilotCoupler {
    
    /** Cross-track error gain (degrees correction per NM of error) */
    private static final double XTK_GAIN = 5.0;
    
    /** Track angle error gain */
    private static final double TAE_GAIN = 1.0;
    
    /** Maximum cross-track correction (degrees) */
    private static final double MAX_XTK_CORRECTION = 45.0;
    
    private final GuidanceComputer guidanceComputer;
    
    public AutopilotCoupler(GuidanceComputer guidanceComputer) {
        this.guidanceComputer = guidanceComputer;
    }
    
    /**
     * Process guidance commands and compute autopilot targets.
     * 
     * @param currentState Current aircraft state
     * @return Target values for autopilot
     */
    public AutopilotTargets process(AircraftState currentState) {
        LateralCommand latCmd = guidanceComputer.getLateralCommand();
        VerticalCommand vertCmd = guidanceComputer.getVerticalCommand();
        
        return new AutopilotTargets(
            calculateTargetTrack(latCmd, currentState),
            calculateTargetSpeed(vertCmd, currentState),
            calculateTargetVerticalSpeed(vertCmd, currentState),
            calculateTargetAltitude(vertCmd),
            calculateTargetBank(latCmd, currentState),
            shouldCaptureAltitude(vertCmd)
        );
    }
    
    /**
     * Calculate target track from lateral command.
     */
    private double calculateTargetTrack(LateralCommand cmd, AircraftState state) {
        return switch (cmd.type()) {
            case HEADING -> cmd.targetHeading();
            case TRACK -> cmd.targetTrack();
            case LNAV -> {
                // LNAV: Compute intercept heading from cross-track and track errors
                double xtkCorrection = Math.copySign(
                    Math.min(Math.abs(cmd.crossTrackError()) * XTK_GAIN, MAX_XTK_CORRECTION),
                    cmd.crossTrackError()
                );
                double taeCorrection = cmd.trackAngleError() * TAE_GAIN;
                yield normalizeHeading(cmd.targetTrack() - xtkCorrection + taeCorrection);
            }
            case LOC -> cmd.targetTrack();
            case ROLLOUT -> cmd.targetTrack();
        };
    }
    
    /**
     * Calculate target groundspeed from vertical command.
     */
    private double calculateTargetSpeed(VerticalCommand cmd, AircraftState state) {
        double targetSpeed = cmd.targetSpeed();
        
        // If speed is Mach, convert to knots
        if (cmd.isMach()) {
            // Mach to knots: M * speed of sound
            // Speed of sound ~ 661.5 knots at sea level
            // Decreases with altitude
            double speedOfSound = 661.5 * Math.sqrt(1.0 - state.getAltitude() / 145442.0);
            targetSpeed = targetSpeed / 100.0 * speedOfSound;
        }
        
        return targetSpeed > 0 ? targetSpeed : state.getGroundSpeed();
    }
    
    /**
     * Calculate target vertical speed from vertical command.
     */
    private double calculateTargetVerticalSpeed(VerticalCommand cmd, AircraftState state) {
        return switch (cmd.type()) {
            case ALTITUDE -> calculateVsToAltitude(cmd.targetAltitude(), state.getAltitude());
            case VERTICAL_SPEED -> cmd.targetVerticalSpeed();
            case VNAV_PATH -> cmd.targetVerticalSpeed();
            case VNAV_SPEED -> calculateVsForSpeed(cmd.targetSpeed(), state);
            case GLIDEPATH -> -700.0; // Standard 3-degree glide ~ 700 fpm
            case FLARE -> -100.0;
            case GO_AROUND -> 1500.0;
            case FLIGHT_PATH -> cmd.targetVerticalSpeed();
        };
    }
    
    /**
     * Calculate target altitude from vertical command.
     */
    private double calculateTargetAltitude(VerticalCommand cmd) {
        return cmd.targetAltitude();
    }
    
    /**
     * Calculate target bank angle.
     */
    private double calculateTargetBank(LateralCommand cmd, AircraftState state) {
        return cmd.desiredBankAngle();
    }
    
    /**
     * Determine if altitude capture mode is active.
     */
    private boolean shouldCaptureAltitude(VerticalCommand cmd) {
        return cmd.type() == VerticalCommand.CommandType.ALTITUDE ||
               cmd.type() == VerticalCommand.CommandType.VNAV_PATH;
    }
    
    /**
     * Calculate vertical speed needed to reach target altitude.
     */
    private double calculateVsToAltitude(double targetAltitude, double currentAltitude) {
        double diff = targetAltitude - currentAltitude;
        
        if (Math.abs(diff) < 100) {
            return 0.0; // Close enough, level off
        }
        
        // Standard climb/descent rate
        double vs = Math.copySign(2000.0, diff);
        
        // Reduce VS when close to target
        if (Math.abs(diff) < 1000) {
            vs = Math.copySign(Math.abs(diff) * 2.0, diff);
        }
        
        return vs;
    }
    
    /**
     * Calculate vertical speed for speed-on-descent.
     */
    private double calculateVsForSpeed(double targetSpeed, AircraftState state) {
        // Simplified: if speed is high, descend to accelerate
        // In reality, this is more complex
        return 0.0;
    }
    
    /**
     * Normalize heading to [0, 360) range.
     */
    private double normalizeHeading(double heading) {
        while (heading < 0) heading += 360.0;
        while (heading >= 360) heading -= 360.0;
        return heading;
    }
}
