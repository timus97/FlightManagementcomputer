package com.aviation.fmc.navdata;

import com.aviation.fmc.common.GeoCoordinate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Value;

/**
 * Represents a runway at an airport.
 * Each runway has two ends (e.g., 09L and 27R are opposite ends of the same runway).
 * 
 * Invariants:
 * - Runway headings must differ by exactly 180 degrees (magnetic)
 * - Runway identifiers must correspond to magnetic heading (rounded to nearest 10)
 * - TORA, TODA, ASDA, LDA must be positive and consistent
 * - Threshold coordinates must be consistent with runway length
 */
@Value
@Builder
public class Runway {

    public enum SurfaceType {
        ASPHALT,
        CONCRETE,
        GRASS,
        GRAVEL,
        WATER,
        ICE,
        SNOW,
        UNKNOWN
    }

    public enum LightingType {
        NONE,
        LIRL,       // Low Intensity Runway Lights
        MIRL,       // Medium Intensity Runway Lights
        HIRL,       // High Intensity Runway Lights
        PAPI,       // Precision Approach Path Indicator
        VASI,       // Visual Approach Slope Indicator
        ALSF,       // Approach Lighting System with Flashers
        MALSR,      // Medium Intensity Approach Lighting System with RAIL
        SSALR       // Simplified Short Approach Lighting System with RAIL
    }

    /**
     * ICAO code of the parent airport.
     */
    @NotNull(message = "Airport ICAO is required")
    @Pattern(regexp = "[A-Z0-9]{4}", message = "Invalid ICAO code")
    String airportIcao;

    /**
     * Runway identifier (e.g., "09L", "27R", "18", "36").
     * Format: 2 digits (01-36) + optional suffix (L/R/C).
     */
    @NotNull(message = "Runway identifier is required")
    @Pattern(regexp = "0[1-9]|[12][0-9]|3[0-6][LCR]?", message = "Invalid runway identifier")
    String identifier;

    /**
     * Magnetic heading of the runway in degrees.
     * Should match the identifier (e.g., 09L = 090°).
     */
    @DecimalMin(value = "0.0", message = "Heading must be >= 0")
    @DecimalMax(value = "360.0", message = "Heading must be <= 360")
    Double magneticHeading;

    /**
     * True heading of the runway (for GPS operations).
     */
    @DecimalMin(value = "0.0", message = "True heading must be >= 0")
    @DecimalMax(value = "360.0", message = "True heading must be <= 360")
    Double trueHeading;

    /**
     * Takeoff Runway Available - length available for takeoff run.
     * In meters.
     */
    @Positive(message = "TORA must be positive")
    @Max(value = 6000, message = "TORA must be <= 6000m")
    Integer tora;

    /**
     * Takeoff Distance Available - TORA plus clearway.
     * In meters.
     */
    @Positive(message = "TODA must be positive")
    @Max(value = 6000, message = "TODA must be <= 6000m")
    Integer toda;

    /**
     * Accelerate-Stop Distance Available - TORA plus stopway.
     * In meters.
     */
    @Positive(message = "ASDA must be positive")
    @Max(value = 6000, message = "ASDA must be <= 6000m")
    Integer asda;

    /**
     * Landing Distance Available - runway length available for landing.
     * In meters.
     */
    @Positive(message = "LDA must be positive")
    @Max(value = 6000, message = "LDA must be <= 6000m")
    Integer lda;

    /**
     * Runway width in meters.
     */
    @Positive(message = "Width must be positive")
    @Max(value = 100, message = "Width must be <= 100m")
    Integer width;

    @NotNull(message = "Surface type is required")
    SurfaceType surface;

    /**
     * Threshold coordinate (start of runway).
     */
    @NotNull(message = "Threshold coordinate is required")
    @Valid
    GeoCoordinate threshold;

    /**
     * Displaced threshold distance in meters (if any).
     */
    @Min(0)
    Integer displacedThreshold;

    @NotNull(message = "Lighting type is required")
    LightingType lighting;

    /**
     * Runway is closed for operations.
     */
    boolean closed;

    /**
     * Instrument approach available for this runway direction.
     */
    boolean instrumentApproach;

    /**
     * ILS/MLS/GPS approach available.
     */
    boolean precisionApproach;

    /**
     * Calculate crosswind component.
     * @param windDirection Wind direction in degrees (magnetic)
     * @param windSpeed Wind speed in knots
     * @return Crosswind component in knots (positive = right crosswind)
     */
    public double calculateCrosswind(double windDirection, double windSpeed) {
        double windAngle = Math.toRadians(windDirection - magneticHeading);
        return windSpeed * Math.sin(windAngle);
    }

    /**
     * Calculate headwind component.
     * @param windDirection Wind direction in degrees (magnetic)
     * @param windSpeed Wind speed in knots
     * @return Headwind component in knots (positive = headwind, negative = tailwind)
     */
    public double calculateHeadwind(double windDirection, double windSpeed) {
        double windAngle = Math.toRadians(windDirection - magneticHeading);
        return windSpeed * Math.cos(windAngle);
    }

    /**
     * Determine if runway is active given wind conditions.
     * Usually requires crosswind within limits and headwind (or minimal tailwind).
     */
    public boolean isActiveForWind(double windDirection, double windSpeed) {
        if (closed) return false;
        
        double crosswind = Math.abs(calculateCrosswind(windDirection, windSpeed));
        double headwind = calculateHeadwind(windDirection, windSpeed);
        
        // Typical limits: crosswind < 25 kts, tailwind < 10 kts
        return crosswind < 25.0 && headwind > -10.0;
    }

    /**
     * Get the reciprocal runway identifier.
     * E.g., "09L" -> "27R", "18" -> "36"
     */
    public String getReciprocalIdentifier() {
        String digits = identifier.replaceAll("[^0-9]", "");
        String suffix = identifier.replaceAll("[0-9]", "");
        
        int reciprocalNum = (Integer.parseInt(digits) + 18) % 36;
        if (reciprocalNum == 0) reciprocalNum = 36;
        
        // Swap L<->R, keep C
        String reciprocalSuffix = switch (suffix) {
            case "L" -> "R";
            case "R" -> "L";
            default -> suffix;
        };
        
        return String.format("%02d%s", reciprocalNum, reciprocalSuffix);
    }

    /**
     * Validate runway data consistency.
     */
    public boolean isValid() {
        // Check measurements consistency: TORA <= TODA, TORA <= ASDA
        if (tora > toda || tora > asda) {
            return false;
        }
        
        // Check heading matches identifier
        if (magneticHeading != null) {
            String digits = identifier.replaceAll("[^0-9]", "");
            int expectedHeading = Integer.parseInt(digits) * 10;
            double headingDiff = Math.abs(magneticHeading - expectedHeading);
            if (headingDiff > 5.0 && headingDiff < 355.0) {
                return false;
            }
        }
        
        return true;
    }
}
