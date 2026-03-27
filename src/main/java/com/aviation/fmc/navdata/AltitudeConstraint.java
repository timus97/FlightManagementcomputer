package com.aviation.fmc.navdata;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

/**
 * Represents altitude constraints for a procedure leg or waypoint.
 * 
 * Invariants:
 * - Ceiling must be >= floor if both specified
 * - Altitudes must be within valid range
 * - Constraint type determines interpretation
 */
@Value
@Builder
public class AltitudeConstraint {

    public enum ConstraintType {
        AT,             // At exactly this altitude
        AT_OR_ABOVE,    // At or above this altitude
        AT_OR_BELOW,    // At or below this altitude
        BETWEEN,        // Between floor and ceiling (inclusive)
        GLIDE_PATH      // On glide path (for approaches)
    }

    @NotNull(message = "Constraint type is required")
    ConstraintType type;

    /**
     * Altitude floor in feet (for AT_OR_ABOVE and BETWEEN).
     */
    @Min(value = -2000, message = "Altitude must be >= -2000 ft")
    @Max(value = 60000, message = "Altitude must be <= 60000 ft")
    Integer floor;

    /**
     * Altitude ceiling in feet (for AT_OR_BELOW and BETWEEN).
     */
    @Min(value = -2000, message = "Altitude must be >= -2000 ft")
    @Max(value = 60000, message = "Altitude must be <= 60000 ft")
    Integer ceiling;

    /**
     * Check if an altitude satisfies this constraint.
     */
    public boolean isSatisfied(int altitude) {
        return switch (type) {
            case AT -> floor != null && altitude == floor;
            case AT_OR_ABOVE -> floor != null && altitude >= floor;
            case AT_OR_BELOW -> ceiling != null && altitude <= ceiling;
            case BETWEEN -> floor != null && ceiling != null && 
                           altitude >= floor && altitude <= ceiling;
            case GLIDE_PATH -> true; // Glide path requires vertical guidance
        };
    }

    /**
     * Get target altitude (for AT constraints).
     */
    public Integer getTargetAltitude() {
        if (type == ConstraintType.AT) {
            return floor;
        }
        return null;
    }

    /**
     * Format constraint as string for display.
     */
    public String format() {
        return switch (type) {
            case AT -> String.format("@%d", floor);
            case AT_OR_ABOVE -> String.format("A%d", floor);
            case AT_OR_BELOW -> String.format("B%d", ceiling);
            case BETWEEN -> String.format("%d-%d", floor, ceiling);
            case GLIDE_PATH -> "GS";
        };
    }
}
