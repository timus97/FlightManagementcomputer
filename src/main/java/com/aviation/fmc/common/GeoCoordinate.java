package com.aviation.fmc.common;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

/**
 * Geographic coordinate representing a point on Earth's surface.
 * Uses WGS-84 datum standard for aviation navigation.
 * 
 * Invariants:
 * - Latitude must be between -90 and +90 degrees
 * - Longitude must be between -180 and +180 degrees
 * - Precision to 6 decimal places (~0.1m accuracy)
 */
@Value
@Builder
public class GeoCoordinate {

    private static final double EARTH_RADIUS_NM = 3440.065;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
    Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
    Double longitude;

    /**
     * Elevation in feet above mean sea level (MSL).
     * Can be negative for locations below sea level.
     */
    @DecimalMin(value = "-2000.0", message = "Elevation must be >= -2000 ft")
    @DecimalMax(value = "60000.0", message = "Elevation must be <= 60000 ft")
    Double elevationFt;

    /**
     * Calculates great circle distance to another coordinate in nautical miles.
     */
    public double distanceTo(GeoCoordinate other) {
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = Math.toRadians(other.latitude - this.latitude);
        double dLon = Math.toRadians(other.longitude - this.longitude);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_NM * c;
    }

    /**
     * Calculates initial bearing (true course) to another coordinate.
     */
    public double bearingTo(GeoCoordinate other) {
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLon = Math.toRadians(other.longitude - this.longitude);

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }
}
