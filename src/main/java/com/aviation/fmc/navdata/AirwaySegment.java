package com.aviation.fmc.navdata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Value;

/**
 * Represents a single segment of an airway between two waypoints.
 * 
 * Invariants:
 * - From and To waypoints must be different
 * - Distance must be positive and reasonable (< 500 NM)
 * - Track must be consistent with waypoint positions
 */
@Value
@Builder
public class AirwaySegment {

    /**
     * Starting waypoint of the segment.
     */
    @NotNull(message = "From waypoint is required")
    @Valid
    Waypoint fromWaypoint;

    /**
     * Ending waypoint of the segment.
     */
    @NotNull(message = "To waypoint is required")
    @Valid
    Waypoint toWaypoint;

    /**
     * Distance between waypoints in nautical miles.
     */
    @Positive(message = "Distance must be positive")
    @Max(value = 500, message = "Segment distance must be <= 500 NM")
    Double distance;

    /**
     * Magnetic track from start to end waypoint.
     */
    @DecimalMin(value = "0.0", message = "Track must be >= 0")
    @DecimalMax(value = "360.0", message = "Track must be <= 360")
    Double magneticTrack;

    /**
     * True track from start to end waypoint.
     */
    @DecimalMin(value = "0.0", message = "True track must be >= 0")
    @DecimalMax(value = "360.0", message = "True track must be <= 360")
    Double trueTrack;

    /**
     * Minimum enroute altitude for this specific segment.
     * Overrides airway MEA if specified.
     */
    @Min(value = 1000, message = "MEA must be >= 1000 ft")
    @Max(value = 45000, message = "MEA must be <= 45000 ft")
    Integer minimumEnrouteAltitude;

    /**
     * Minimum reception altitude (MRA) for navigation aids.
     */
    @Min(value = 1000, message = "MRA must be >= 1000 ft")
    @Max(value = 45000, message = "MRA must be <= 45000 ft")
    Integer minimumReceptionAltitude;

    /**
     * Calculate or return distance.
     */
    public double getDistance() {
        if (distance != null) {
            return distance;
        }
        return fromWaypoint.distanceTo(toWaypoint);
    }

    /**
     * Calculate or return magnetic track.
     */
    public double getMagneticTrack() {
        if (magneticTrack != null) {
            return magneticTrack;
        }
        return fromWaypoint.magneticBearingTo(toWaypoint);
    }

    /**
     * Calculate reciprocal track (for reverse direction).
     */
    public double getReciprocalTrack() {
        return (getMagneticTrack() + 180) % 360;
    }

    /**
     * Check if a point is on this segment (within tolerance).
     */
    public boolean containsWaypoint(Waypoint waypoint, double toleranceNm) {
        double distFromStart = fromWaypoint.distanceTo(waypoint);
        double distToEnd = waypoint.distanceTo(toWaypoint);
        double segmentDist = getDistance();
        
        // Check if point lies approximately on the segment
        return Math.abs(distFromStart + distToEnd - segmentDist) < toleranceNm;
    }
}
