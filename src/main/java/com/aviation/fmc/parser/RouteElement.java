package com.aviation.fmc.parser;

import com.aviation.fmc.common.Altitude;
import com.aviation.fmc.navdata.Airway;
import com.aviation.fmc.navdata.Waypoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Value;

/**
 * Represents a single element in the flight plan route.
 * Can be a waypoint, airway segment, or direct route segment.
 * 
 * Invariants:
 * - Must have either waypoint or airway reference
 * - Distance must be positive
 * - Estimated time must be positive
 */
@Value
@Builder
public class RouteElement {

    public enum ElementType {
        WAYPOINT,       // Individual waypoint
        AIRWAY,         // Airway segment
        DIRECT,         // Direct (great circle) routing
        SID,            // SID segment
        STAR,           // STAR segment
        APPROACH,       // Approach segment
        HOLDING,        // Holding pattern
        VECTOR          // Radar vectors
    }

    /**
     * Sequence number in the route.
     */
    @PositiveOrZero
    int sequenceNumber;

    /**
     * Type of route element.
     */
    @NotNull(message = "Element type is required")
    ElementType type;

    /**
     * Waypoint for this element (if applicable).
     */
    @Valid
    Waypoint waypoint;

    /**
     * Airway for this element (if applicable).
     */
    @Valid
    Airway airway;

    /**
     * Course/track to this element (magnetic).
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
     * Distance from previous element in nautical miles.
     */
    @Positive(message = "Distance must be positive")
    Double distance;

    /**
     * Estimated time to this waypoint from previous.
     */
    @Positive(message = "Estimated time must be positive")
    Double estimatedTimeMinutes;

    /**
     * Altitude constraint at this element.
     */
    @Valid
    Altitude altitudeConstraint;

    /**
     * Speed constraint at this element (knots).
     */
    @Min(value = 80, message = "Speed must be >= 80 knots")
    @Max(value = 500, message = "Speed must be <= 500 knots")
    Integer speedConstraint;

    /**
     * Wind direction at this point (magnetic).
     */
    @DecimalMin(value = "0.0", message = "Wind direction must be >= 0")
    @DecimalMax(value = "360.0", message = "Wind direction must be <= 360")
    Double windDirection;

    /**
     * Wind speed at this point (knots).
     */
    @Min(value = 0, message = "Wind speed must be >= 0")
    @Max(value = 200, message = "Wind speed must be <= 200 knots")
    Integer windSpeed;

    /**
     * Temperature at this point (Celsius).
     */
    @DecimalMin(value = "-80.0", message = "Temperature must be >= -80°C")
    @DecimalMax(value = "60.0", message = "Temperature must be <= 60°C")
    Double temperature;

    /**
     * Element is currently active (aircraft is on this leg).
     */
    @Builder.Default
    boolean active = false;

    /**
     * Element has been completed.
     */
    @Builder.Default
    boolean completed = false;

    /**
     * Element is fly-over (vs. fly-by).
     */
    @Builder.Default
    boolean flyOver = false;

    /**
     * Estimated time of arrival at this waypoint (UTC).
     */
    String estimatedTime;

    /**
     * Actual time of arrival at this waypoint (UTC).
     */
    String actualTime;

    /**
     * Get magnetic course with wind correction.
     */
    public double getMagneticCourse() {
        if (course != null) {
            return course;
        }
        if (waypoint != null && sequenceNumber > 0) {
            // This would need previous waypoint reference to calculate
        }
        return 0.0;
    }

    /**
     * Calculate groundspeed given true airspeed.
     */
    public double calculateGroundspeed(int trueAirspeed) {
        if (windDirection == null || windSpeed == null || course == null) {
            return trueAirspeed;
        }
        
        // Wind angle relative to course
        double windAngle = Math.toRadians(windDirection - course);
        
        // Calculate headwind component
        double headwind = windSpeed * Math.cos(windAngle);
        
        // Groundspeed = TAS - headwind (headwind positive = slower)
        return trueAirspeed - headwind;
    }

    /**
     * Check if this element has been passed.
     */
    public boolean isPassed() {
        return completed;
    }

    /**
     * Procedure name (for SID/STAR/Approach elements).
     */
    String procedureName;

    /**
     * Get display name for this element.
     */
    public String getDisplayName() {
        return switch (type) {
            case WAYPOINT -> waypoint != null ? waypoint.getIdentifier() : "???";
            case AIRWAY -> airway != null ? airway.getDesignator() : "DCT";
            case DIRECT -> "DCT";
            case SID -> procedureName != null ? procedureName : "SID";
            case STAR -> procedureName != null ? procedureName : "STAR";
            case APPROACH -> procedureName != null ? procedureName : "APP";
            case HOLDING -> waypoint != null ? waypoint.getIdentifier() + " (HOLD)" : "HOLD";
            case VECTOR -> "VECTOR";
        };
    }
}
