package com.aviation.fmc.navdata;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Value;

/**
 * Represents a holding pattern at a fix.
 * 
 * Invariants:
 * - Inbound course must be 0-360 degrees
 * - Turn direction must be LEFT or RIGHT
 * - Leg length (time or distance) must be positive
 * - Minimum holding altitude must be below maximum
 */
@Value
@Builder
public class HoldingPattern {

    public enum TurnDirection {
        LEFT, RIGHT
    }

    public enum LegType {
        TIME,           // Leg defined by time (standard)
        DISTANCE        // Leg defined by distance
    }

    /**
     * Inbound magnetic course to the fix.
     */
    @DecimalMin(value = "0.0", message = "Inbound course must be >= 0")
    @DecimalMax(value = "360.0", message = "Inbound course must be <= 360")
    Double inboundCourse;

    /**
     * Turn direction in the hold.
     */
    @NotNull(message = "Turn direction is required")
    @Builder.Default
    TurnDirection turnDirection = TurnDirection.RIGHT;

    /**
     * Leg type (time or distance based).
     */
    @NotNull(message = "Leg type is required")
    @Builder.Default
    LegType legType = LegType.TIME;

    /**
     * Leg length in minutes (if TIME type).
     * Standard is 1 minute below FL140, 1.5 minutes above.
     */
    @DecimalMin(value = "0.5", message = "Leg time must be >= 0.5 min")
    @DecimalMax(value = "3.0", message = "Leg time must be <= 3 min")
    @Builder.Default
    Double legTimeMinutes = 1.0;

    /**
     * Leg length in nautical miles (if DISTANCE type).
     */
    @Positive(message = "Leg distance must be positive")
    Double legDistanceNm;

    /**
     * Minimum holding altitude in feet.
     */
    @Min(value = 0, message = "Minimum altitude must be >= 0")
    @Max(value = 60000, message = "Minimum altitude must be <= 60000")
    Integer minimumAltitude;

    /**
     * Maximum holding altitude in feet.
     */
    @Min(value = 0, message = "Maximum altitude must be >= 0")
    @Max(value = 60000, message = "Maximum altitude must be <= 60000")
    Integer maximumAltitude;

    /**
     * Published airspeed limit for holding (knots).
     */
    @Min(value = 80, message = "Speed limit must be >= 80 knots")
    @Max(value = 300, message = "Speed limit must be <= 300 knots")
    Integer speedLimitKnots;

    /**
     * Check if an altitude is valid for this holding pattern.
     */
    public boolean isAltitudeValid(int altitude) {
        if (minimumAltitude != null && altitude < minimumAltitude) {
            return false;
        }
        if (maximumAltitude != null && altitude > maximumAltitude) {
            return false;
        }
        return true;
    }

    /**
     * Get outbound course (opposite of inbound).
     */
    public double getOutboundCourse() {
        return (inboundCourse + 180) % 360;
    }

    /**
     * Get standard leg time based on altitude.
     * 1 minute below FL140, 1.5 minutes above.
     */
    public static double getStandardLegTime(int altitudeFt) {
        return altitudeFt < 14000 ? 1.0 : 1.5;
    }
}
