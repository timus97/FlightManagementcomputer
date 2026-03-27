package com.aviation.fmc.contracts;

import com.aviation.fmc.common.GeoCoordinate;

/**
 * Implementation of the Guidance Computer.
 * Generates steering commands for the autopilot and flight director.
 * Calculates lateral (LNAV) and vertical (VNAV) guidance.
 */
public class GuidanceComputerImpl implements GuidanceComputer {

    // Control constants
    private static final double LNAV_GAIN = 2.0; // Cross-track error gain
    private static final double MAX_BANK_ANGLE = 25.0; // degrees
    private static final double MAX_PITCH_ANGLE = 10.0; // degrees
    private static final double ALTITUDE_CAPTURE_BAND = 1000.0; // feet
    private static final double TRACK_ANGLE_GAIN = 1.5;

    // Mode flags
    private boolean lnavEnabled = false;
    private boolean vnavEnabled = false;
    private boolean approachArmed = false;
    private boolean approachActive = false;

    // Target values
    private double targetAltitude = 10000.0;
    private double targetSpeed = 250.0;
    private boolean targetIsMach = false;
    private double targetVerticalSpeed = 0.0;
    private String approachType = null;

    // Aircraft configuration
    private int flapsPosition = 0;
    private boolean gearDown = false;

    // Current state
    private double currentAltitude = 10000.0;
    private double currentSpeed = 250.0;
    private double currentVerticalSpeed = 0.0;
    private double currentHeading = 0.0;

    // Navigation computer reference for position data
    private NavigationComputer navigationComputer;

    public GuidanceComputerImpl() {
    }

    public GuidanceComputerImpl(NavigationComputer navigationComputer) {
        this.navigationComputer = navigationComputer;
    }

    @Override
    public void setLnavEnabled(boolean enabled) {
        this.lnavEnabled = enabled;
    }

    @Override
    public void setVnavEnabled(boolean enabled) {
        this.vnavEnabled = enabled;
    }

    @Override
    public boolean isLnavActive() {
        return lnavEnabled && navigationComputer != null && 
               navigationComputer.getActiveWaypoint().isPresent();
    }

    @Override
    public boolean isVnavActive() {
        return vnavEnabled;
    }

    @Override
    public LateralCommand getLateralCommand() {
        if (!lnavEnabled || navigationComputer == null) {
            // Return heading hold command
            return new LateralCommand(
                LateralCommand.CommandType.HEADING,
                currentHeading,
                currentHeading,
                0.0,
                0.0,
                0.0,
                0.0,
                false
            );
        }

        // LNAV active - calculate track steering
        double crossTrackError = navigationComputer.getCrossTrackError();
        double requiredTrack = navigationComputer.getRequiredTrack();
        double trackAngleError = calculateTrackAngleError(requiredTrack);

        // Calculate roll command using cross-track and track angle error
        double rollCommand = calculateRollCommand(crossTrackError, trackAngleError);

        // Calculate desired bank angle
        double desiredBankAngle = Math.min(Math.abs(rollCommand), MAX_BANK_ANGLE);
        if (rollCommand < 0) {
            desiredBankAngle = -desiredBankAngle;
        }

        return new LateralCommand(
            LateralCommand.CommandType.LNAV,
            currentHeading,
            requiredTrack,
            crossTrackError,
            trackAngleError,
            rollCommand,
            desiredBankAngle,
            Math.abs(crossTrackError) > 2.0 // Intercept required if far off track
        );
    }

    @Override
    public VerticalCommand getVerticalCommand() {
        if (!vnavEnabled) {
            // Altitude hold mode
            double altError = targetAltitude - currentAltitude;
            double pitchCommand = calculatePitchForAltitudeHold(altError);

            return new VerticalCommand(
                VerticalCommand.CommandType.ALTITUDE,
                targetAltitude,
                targetSpeed,
                0.0,
                altError,
                0.0,
                pitchCommand,
                50.0, // Neutral throttle
                targetIsMach
            );
        }

        // VNAV active - determine vertical mode
        double altitudeError = targetAltitude - currentAltitude;
        double speedError = targetSpeed - currentSpeed;

        VerticalCommand.CommandType verticalMode;
        double targetVs;
        double pitchCommand;
        double throttleCommand;

        if (Math.abs(altitudeError) < 100) {
            // Within capture band - altitude hold
            verticalMode = VerticalCommand.CommandType.ALTITUDE;
            targetVs = 0.0;
            pitchCommand = calculatePitchForAltitudeHold(altitudeError);
            throttleCommand = calculateThrottleForSpeedHold(speedError);
        } else if (Math.abs(altitudeError) < ALTITUDE_CAPTURE_BAND) {
            // Altitude capture mode
            verticalMode = VerticalCommand.CommandType.VNAV_PATH;
            targetVs = calculateCaptureVerticalSpeed(altitudeError);
            pitchCommand = calculatePitchForVerticalSpeed(targetVs);
            throttleCommand = calculateThrottleForSpeedHold(speedError);
        } else {
            // VNAV speed or path mode
            if (targetVerticalSpeed != 0.0) {
                verticalMode = VerticalCommand.CommandType.VERTICAL_SPEED;
                targetVs = targetVerticalSpeed;
                pitchCommand = calculatePitchForVerticalSpeed(targetVs);
                throttleCommand = calculateThrottleForSpeedHold(speedError);
            } else {
                verticalMode = VerticalCommand.CommandType.VNAV_SPEED;
                targetVs = (altitudeError > 0) ? 1500.0 : -1500.0; // Default climb/descent rate
                pitchCommand = calculatePitchForVerticalSpeed(targetVs);
                throttleCommand = (altitudeError > 0) ? 85.0 : 30.0; // Climb/descent thrust
            }
        }

        return new VerticalCommand(
            verticalMode,
            targetAltitude,
            targetSpeed,
            targetVs,
            altitudeError,
            speedError,
            pitchCommand,
            throttleCommand,
            targetIsMach
        );
    }

    @Override
    public void setTargetAltitude(double altitude) {
        this.targetAltitude = altitude;
    }

    @Override
    public void setTargetSpeed(double speed, boolean isMach) {
        this.targetSpeed = speed;
        this.targetIsMach = isMach;
    }

    @Override
    public void setVerticalSpeed(double verticalSpeed) {
        this.targetVerticalSpeed = verticalSpeed;
    }

    @Override
    public boolean armApproach(String approachType) {
        if (approachType == null || approachType.isEmpty()) {
            return false;
        }

        this.approachType = approachType;
        this.approachArmed = true;
        return true;
    }

    @Override
    public void disengageApproach() {
        this.approachArmed = false;
        this.approachActive = false;
        this.approachType = null;
    }

    @Override
    public boolean isApproachActive() {
        return approachActive;
    }

    @Override
    public void setFlapsPosition(int flapsPosition) {
        this.flapsPosition = flapsPosition;
    }

    @Override
    public void setGearDown(boolean down) {
        this.gearDown = down;
    }

    @Override
    public double getFlightPathAngle() {
        // Calculate actual flight path angle from vertical speed and groundspeed
        if (navigationComputer == null) {
            return 0.0;
        }

        return navigationComputer.getCurrentPosition()
            .map(pos -> {
                double gs = pos.groundspeed();
                if (gs <= 0) return 0.0;
                // FPA = arctan(VS / (GS * 101.268)) where 101.268 converts kts to fpm
                return Math.toDegrees(Math.atan(currentVerticalSpeed / (gs * 101.268)));
            })
            .orElse(0.0);
    }

    @Override
    public double getRequiredFlightPathAngle() {
        // Calculate required FPA to meet altitude constraints
        // Simplified: assume 3 degree descent path
        if (targetAltitude < currentAltitude) {
            return -3.0;
        } else if (targetAltitude > currentAltitude) {
            return 3.0;
        }
        return 0.0;
    }

    // ==================== Helper Methods ====================

    /**
     * Calculate track angle error (difference between current track and required track).
     */
    private double calculateTrackAngleError(double requiredTrack) {
        if (navigationComputer == null) {
            return 0.0;
        }

        return navigationComputer.getCurrentPosition()
            .map(pos -> {
                double currentTrack = pos.heading(); // Simplified: using heading as track
                double error = requiredTrack - currentTrack;
                // Normalize to -180 to +180
                while (error > 180) error -= 360;
                while (error < -180) error += 360;
                return error;
            })
            .orElse(0.0);
    }

    /**
     * Calculate roll command from cross-track and track angle errors.
     */
    private double calculateRollCommand(double crossTrackError, double trackAngleError) {
        // Combined control law: bank angle proportional to XTE and track error
        double xteComponent = crossTrackError * LNAV_GAIN;
        double trackComponent = trackAngleError * TRACK_ANGLE_GAIN;

        double rollCommand = xteComponent + trackComponent;

        // Limit to maximum bank angle
        return Math.max(-MAX_BANK_ANGLE, Math.min(MAX_BANK_ANGLE, rollCommand));
    }

    /**
     * Calculate pitch command for altitude hold.
     */
    private double calculatePitchForAltitudeHold(double altitudeError) {
        // Simple proportional control
        double pitchGain = 0.05; // degrees per foot of error
        double pitchCommand = altitudeError * pitchGain;

        // Limit pitch
        return Math.max(-MAX_PITCH_ANGLE, Math.min(MAX_PITCH_ANGLE, pitchCommand));
    }

    /**
     * Calculate pitch command for vertical speed hold.
     */
    private double calculatePitchForVerticalSpeed(double targetVs) {
        // Pitch required to achieve target vertical speed
        // Simplified: 1 degree pitch ≈ 500 fpm at typical speeds
        double pitchCommand = targetVs / 500.0;

        // Limit pitch
        return Math.max(-MAX_PITCH_ANGLE, Math.min(MAX_PITCH_ANGLE, pitchCommand));
    }

    /**
     * Calculate capture vertical speed based on altitude error.
     */
    private double calculateCaptureVerticalSpeed(double altitudeError) {
        // S-curve capture profile
        double captureRate = Math.abs(altitudeError) * 2.0; // fpm proportional to error
        captureRate = Math.min(captureRate, 1500.0); // Max 1500 fpm

        return altitudeError > 0 ? captureRate : -captureRate;
    }

    /**
     * Calculate throttle command for speed hold.
     */
    private double calculateThrottleForSpeedHold(double speedError) {
        // Base throttle
        double baseThrottle = 60.0; // %

        // Speed error correction
        double throttleGain = 2.0; // % per knot
        double throttleCommand = baseThrottle + (speedError * throttleGain);

        // Limit throttle
        return Math.max(20.0, Math.min(95.0, throttleCommand));
    }

    /**
     * Update current aircraft state.
     */
    public void updateState(double altitude, double speed, double verticalSpeed, double heading) {
        this.currentAltitude = altitude;
        this.currentSpeed = speed;
        this.currentVerticalSpeed = verticalSpeed;
        this.currentHeading = heading;
    }

    /**
     * Set navigation computer reference.
     */
    public void setNavigationComputer(NavigationComputer navigationComputer) {
        this.navigationComputer = navigationComputer;
    }

    /**
     * Get current guidance status.
     */
    public GuidanceStatus getGuidanceStatus() {
        return new GuidanceStatus(
            lnavEnabled && !isLnavActive(), // Armed but not active
            isLnavActive(),
            vnavEnabled && !isVnavActive(), // Armed but not active
            isVnavActive(),
            approachArmed && !approachActive,
            approachActive,
            !vnavEnabled, // Alt hold when VNAV off
            targetVerticalSpeed != 0.0,
            true, // Always trying to hold speed
            isLnavActive() ? LateralCommand.CommandType.LNAV : LateralCommand.CommandType.HEADING,
            isVnavActive() ? VerticalCommand.CommandType.VNAV_SPEED : VerticalCommand.CommandType.ALTITUDE
        );
    }
}
