package com.aviation.fmc.common;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Builder;
import lombok.Value;

import java.time.Year;

/**
 * Represents magnetic variation (declination) at a location.
 * Magnetic variation is the difference between true north and magnetic north.
 * 
 * Invariants:
 * - Variation must be between -180 and +180 degrees
 * - Annual change rate must be reasonable (-60 to +60 minutes/year)
 */
@Value
@Builder
public class MagneticVariation {

    /**
     * Magnetic variation in degrees.
     * Positive = East variation (magnetic north is east of true north)
     * Negative = West variation (magnetic north is west of true north)
     */
    @DecimalMin(value = "-180.0", message = "Variation must be >= -180")
    @DecimalMax(value = "180.0", message = "Variation must be <= 180")
    Double variationDegrees;

    /**
     * Annual change in magnetic variation (minutes per year).
     * Positive = increasing east variation
     * Negative = increasing west variation
     */
    @DecimalMin(value = "-60.0", message = "Annual change must be >= -60 min/year")
    @DecimalMax(value = "60.0", message = "Annual change must be <= 60 min/year")
    Double annualChangeMinutes;

    /**
     * Reference year for the variation measurement.
     */
    Year referenceYear;

    /**
     * Calculates magnetic variation for a given year.
     */
    public double calculateVariationForYear(Year year) {
        if (annualChangeMinutes == null || referenceYear == null) {
            return variationDegrees;
        }
        int yearsDiff = year.getValue() - referenceYear.getValue();
        double totalChange = (annualChangeMinutes * yearsDiff) / 60.0;
        return variationDegrees + totalChange;
    }

    /**
     * Converts true heading to magnetic heading.
     */
    public double trueToMagnetic(double trueHeading, Year year) {
        double variation = calculateVariationForYear(year);
        // East variation subtracts, West variation adds
        double magnetic = trueHeading - variation;
        return (magnetic + 360) % 360;
    }

    /**
     * Converts magnetic heading to true heading.
     */
    public double magneticToTrue(double magneticHeading, Year year) {
        double variation = calculateVariationForYear(year);
        // East variation adds, West variation subtracts
        double trueHeading = magneticHeading + variation;
        return (trueHeading + 360) % 360;
    }
}
