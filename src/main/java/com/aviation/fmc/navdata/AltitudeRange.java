package com.aviation.fmc.navdata;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Value;

/**
 * Represents a valid altitude range.
 * 
 * Invariants:
 * - Floor must be <= ceiling
 * - Both must be within valid aviation altitude range
 */
@Value
public class AltitudeRange {

    @Min(value = -2000, message = "Floor must be >= -2000 ft")
    @Max(value = 60000, message = "Floor must be <= 60000 ft")
    int floor;

    @Min(value = -2000, message = "Ceiling must be >= -2000 ft")
    @Max(value = 60000, message = "Ceiling must be <= 60000 ft")
    int ceiling;

    public AltitudeRange(int floor, int ceiling) {
        if (floor > ceiling) {
            throw new IllegalArgumentException("Floor must be <= ceiling");
        }
        this.floor = floor;
        this.ceiling = ceiling;
    }

    /**
     * Check if an altitude is within this range.
     */
    public boolean contains(int altitude) {
        return altitude >= floor && altitude <= ceiling;
    }

    /**
     * Check if another range overlaps with this range.
     */
    public boolean overlaps(AltitudeRange other) {
        return this.floor <= other.ceiling && other.floor <= this.ceiling;
    }

    /**
     * Get the intersection of two ranges.
     */
    public AltitudeRange intersect(AltitudeRange other) {
        int newFloor = Math.max(this.floor, other.floor);
        int newCeiling = Math.min(this.ceiling, other.ceiling);
        if (newFloor > newCeiling) {
            return null; // No intersection
        }
        return new AltitudeRange(newFloor, newCeiling);
    }

    @Override
    public String toString() {
        return String.format("%d - %d ft", floor, ceiling);
    }
}
