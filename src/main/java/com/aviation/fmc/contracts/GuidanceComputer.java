package com.aviation.fmc.contracts;

import com.aviation.fmc.common.GeoCoordinate;

/**
 * Contract for the FMC Guidance Computer.
 * Generates steering commands for the autopilot and flight director.
 * Calculates lateral (LNAV) and vertical (VNAV) guidance.
 */
public interface GuidanceComputer {

    /**
     * Enable/disable lateral navigation (LNAV).
     * 
     * @param enabled true to enable LNAV
     */
    void setLnavEnabled(boolean enabled);

    /**
     * Enable/disable vertical navigation (VNAV).
     * 
     * @param enabled true to enable VNAV
     */
    void setVnavEnabled(boolean enabled);

    /**
     * Check if LNAV is active.
     * 
     * @return true if LNAV is engaged
     */
    boolean isLnavActive();

    /**
     * Check if VNAV is active.
     * 
     * @return true if VNAV is engaged
     */
    boolean isVnavActive();

    /**
     * Get lateral steering command.
     * 
     * @return Lateral guidance command
     */
    LateralCommand getLateralCommand();

    /**
     * Get vertical steering command.
     * 
     * @return Vertical guidance command
     */
    VerticalCommand getVerticalCommand();

    /**
     * Set target altitude for VNAV.
     * 
     * @param altitude Target altitude in feet
     */
    void setTargetAltitude(double altitude);

    /**
     * Set target speed/mach for VNAV.
     * 
     * @param speed Speed in knots or Mach * 100
     * @param isMach true if speed is Mach * 100
     */
    void setTargetSpeed(double speed, boolean isMach);

    /**
     * Set vertical speed target.
     * 
     * @param verticalSpeed Vertical speed in feet per minute
     */
    void setVerticalSpeed(double verticalSpeed);

    /**
     * Arm or engage approach mode.
     * 
     * @param approachType Type of approach
     * @return true if approach mode was armed/engaged
     */
    boolean armApproach(String approachType);

    /**
     * Disengage approach mode.
     */
    void disengageApproach();

    /**
     * Check if approach mode is active.
     * 
     * @return true if approach is active
     */
    boolean isApproachActive();

    /**
     * Set flaps position for speed calculation.
     * 
     * @param flapsPosition Flaps setting (0, 1, 5, 10, 15, etc.)
     */
    void setFlapsPosition(int flapsPosition);

    /**
     * Set landing gear position.
     * 
     * @param down true if gear is down
     */
    void setGearDown(boolean down);

    /**
     * Get current flight path angle.
     * 
     * @return Flight path angle in degrees (positive = climb)
     */
    double getFlightPathAngle();

    /**
     * Get required flight path angle for descent.
     * 
     * @return Required FPA in degrees (typically negative for descent)
     */
    double getRequiredFlightPathAngle();

    /**
     * Lateral guidance command.
     */
    record LateralCommand(
        CommandType type,
        double targetHeading,       // Magnetic heading in degrees
        double targetTrack,         // Magnetic track in degrees
        double crossTrackError,     // NM, positive = right of course
        double trackAngleError,     // Degrees
        double rollCommand,         // Degrees, positive = right roll
        double desiredBankAngle,    // Degrees
        boolean interceptRequired
    ) {
        public enum CommandType {
            HEADING,        // Fly heading
            TRACK,          // Fly track
            LNAV,           // Lateral navigation
            LOC,            // Localizer tracking
            ROLLOUT         // Runway rollout
        }
    }

    /**
     * Vertical guidance command.
     */
    record VerticalCommand(
        CommandType type,
        double targetAltitude,      // Feet
        double targetSpeed,         // Knots or Mach
        double targetVerticalSpeed, // FPM
        double altitudeError,       // Feet
        double speedError,          // Knots
        double pitchCommand,        // Degrees
        double throttleCommand,     // % N1 or throttle position
        boolean isMach
    ) {
        public enum CommandType {
            ALTITUDE,       // Maintain altitude
            VERTICAL_SPEED, // Maintain vertical speed
            FLIGHT_PATH,    // Maintain flight path angle
            VNAV_PATH,      // VNAV path descent
            VNAV_SPEED,     // VNAV speed
            GLIDEPATH,      // Glide path (ILS)
            FLARE,          // Landing flare
            GO_AROUND       // Go-around
        }
    }

    /**
     * Guidance mode status.
     */
    record GuidanceStatus(
        boolean lnavArmed,
        boolean lnavActive,
        boolean vnavArmed,
        boolean vnavActive,
        boolean approachArmed,
        boolean approachActive,
        boolean altHold,
        boolean vsHold,
        boolean speedHold,
        LateralCommand.CommandType lateralMode,
        VerticalCommand.CommandType verticalMode
    ) {}
}
