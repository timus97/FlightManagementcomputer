package com.aviation.fmc.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Value;

/**
 * Represents altitude in aviation context.
 * Supports various altitude reference types (MSL, AGL, FL).
 * 
 * Invariants:
 * - Altitude must be within valid aviation range (-2000 to 60000 ft)
 * - FL (Flight Level) values must be between 0 and 999
 */
@Value
@Builder
public class Altitude {

    public enum ReferenceType {
        MSL,        // Mean Sea Level
        AGL,        // Above Ground Level
        FL,         // Flight Level (standard pressure)
        PRESSURE    // Pressure altitude
    }

    @Min(value = -2000, message = "Altitude must be >= -2000 ft")
    @Max(value = 60000, message = "Altitude must be <= 60000 ft")
    int value;

    ReferenceType referenceType;

    /**
     * Pressure setting in hPa (hectopascals).
     * Standard pressure is 1013.25 hPa.
     * Used for FL to altitude conversions.
     */
    Double pressureSetting;

    public static Altitude flightLevel(int fl) {
        if (fl < 0 || fl > 999) {
            throw new IllegalArgumentException("Flight Level must be between 0 and 999");
        }
        return Altitude.builder()
                .value(fl * 100)
                .referenceType(ReferenceType.FL)
                .pressureSetting(1013.25)
                .build();
    }

    public static Altitude msl(int feet) {
        return Altitude.builder()
                .value(feet)
                .referenceType(ReferenceType.MSL)
                .build();
    }

    /**
     * Converts flight level to true altitude given local QNH.
     */
    public Altitude toTrueAltitude(double localQnh) {
        if (this.referenceType != ReferenceType.FL) {
            return this;
        }
        // Approximate conversion: 30 ft per hPa difference
        double altitudeDiff = (1013.25 - localQnh) * 30;
        return Altitude.builder()
                .value((int) (this.value + altitudeDiff))
                .referenceType(ReferenceType.MSL)
                .pressureSetting(localQnh)
                .build();
    }
}
