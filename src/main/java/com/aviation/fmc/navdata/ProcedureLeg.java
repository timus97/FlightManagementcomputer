package com.aviation.fmc.navdata;

import com.aviation.fmc.common.Altitude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Value;

/**
 * Represents a single leg of a terminal procedure.
 * A leg defines the path between waypoints and associated altitude/speed constraints.
 * 
 * Invariants:
 * - Fix must be specified
 * - Altitude constraints must be consistent (ceiling >= floor)
 * - Speed limit must be reasonable (< 500 knots)
 */
@Value
@Builder
public class ProcedureLeg {

    public enum LegType {
        TF,             // Track to Fix
        CF,             // Course to Fix
        DF,             // Direct to Fix
        FA,             // Fix to Altitude
        FC,             // Track from Fix from Distance
        FD,             // Track from Fix to DME Distance
        FM,             // From Fix to Manual Termination
        CA,             // Course to Altitude
        CD,             // Course to DME Distance
        CI,             // Course to Intercept
        CR,             // Course to Radial
        RF,             // Constant Radius Arc
        AF,             // Arc to Fix
        HA,             // Holding Pattern with Altitude Termination
        HF,             // Holding Pattern with Single Course Fix
        HM,             // Holding Pattern with Manual Termination
        PI,             // Procedure Turn
        VI,             // Heading to Intercept
        VM,             // Heading to Manual Termination
        VR,             // Heading to VOR Radial
        IF,             // Initial Fix
        TF_DIRECT,      // Direct track between fixes
        VECTOR          // Radar vectors
    }

    /**
     * Type of procedure leg per ARINC 424 path terminator.
     */
    @NotNull(message = "Leg type is required")
    LegType type;

    /**
     * Fix/waypoint for this leg.
     */
    @NotNull(message = "Fix is required")
    @Valid
    Waypoint fix;

    /**
     * Altitude constraint at this fix.
     */
    @Valid
    AltitudeConstraint altitudeConstraint;

    /**
     * Speed constraint at this fix (knots).
     */
    @Min(value = 80, message = "Speed must be >= 80 knots")
    @Max(value = 500, message = "Speed must be <= 500 knots")
    Integer speedKnots;

    /**
     * Course/track for this leg (magnetic).
     */
    @DecimalMin(value = "0.0", message = "Course must be >= 0")
    @DecimalMax(value = "360.0", message = "Course must be <= 360")
    Double course;

    /**
     * True course (for GPS operations).
     */
    @DecimalMin(value = "0.0", message = "True course must be >= 0")
    @DecimalMax(value = "360.0", message = "True course must be <= 360")
    Double trueCourse;

    /**
     * Turn direction at this fix.
     */
    TurnDirection turnDirection;

    /**
     * Distance for this leg in nautical miles.
     */
    @Positive(message = "Distance must be positive")
    Double pathDistance;

    /**
     * Time for this leg in minutes.
     */
    @Positive(message = "Time must be positive")
    Double timeMinutes;

    /**
     * Vertical angle for descent (degrees, negative for descent).
     */
    @DecimalMin(value = "-6.0", message = "Vertical angle must be >= -6")
    @DecimalMax(value = "6.0", message = "Vertical angle must be <= 6")
    Double verticalAngle;

    /**
     * Center fix for arc or radial legs.
     */
    Waypoint centerFix;

    /**
     * Arc radius in nautical miles.
     */
    @Positive(message = "Arc radius must be positive")
    Double arcRadius;

    /**
     * Overfly the fix (vs. fly-by).
     */
    @Builder.Default
    boolean overfly = false;

    /**
     * Hold pattern details if this is a holding leg.
     */
    HoldingPattern holdingPattern;

    public enum TurnDirection {
        LEFT, RIGHT, EITHER
    }

    /**
     * Check if this leg has an altitude constraint.
     */
    public boolean hasAltitudeConstraint() {
        return altitudeConstraint != null;
    }

    /**
     * Check if this leg has a speed constraint.
     */
    public boolean hasSpeedConstraint() {
        return speedKnots != null;
    }

    /**
     * Get distance, calculating if necessary.
     */
    public double getPathDistance() {
        if (pathDistance != null) {
            return pathDistance;
        }
        // For some leg types, distance needs to be calculated from geometry
        return 0.0;
    }
}
