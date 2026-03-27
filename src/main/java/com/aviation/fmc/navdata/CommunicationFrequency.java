package com.aviation.fmc.navdata;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Value;

/**
 * Represents a communication frequency at an airport.
 * 
 * Invariants:
 * - Frequency must be in valid aviation band
 * - Frequency precision must match service type
 */
@Value
@Builder
public class CommunicationFrequency {

    public enum ServiceType {
        ATIS,           // Automatic Terminal Information Service
        DELIVERY,       // Clearance Delivery
        GROUND,         // Ground Control
        TOWER,          // Tower Control
        APPROACH,       // Approach Control
        DEPARTURE,      // Departure Control
        CENTER,         // ARTCC/Center Control
        UNICOM,         // Universal Communications
        MULTICOM,       // Multicom frequency
        EMERGENCY,      // Emergency (121.5)
        GUARD,          // Guard frequency (243.0 military)
        ATF,            // Aerodrome Traffic Frequency
        MF,             // Mandatory Frequency
        RCO,            // Remote Communications Outlet
        GCO             // Ground Communications Outlet
    }

    /**
     * Communication service type.
     */
    @NotNull(message = "Service type is required")
    ServiceType serviceType;

    /**
     * Frequency in MHz.
     * VHF range: 118.000 - 136.975 MHz
     * UHF range: 225.000 - 399.975 MHz (military)
     */
    @DecimalMin(value = "118.0", message = "Frequency must be >= 118.0 MHz")
    @DecimalMax(value = "400.0", message = "Frequency must be <= 400.0 MHz")
    Double frequency;

    /**
     * Service name/sector (e.g., "North", "East", "Primary").
     */
    @Size(max = 30, message = "Sector name must not exceed 30 characters")
    String sector;

    /**
     * Operating hours (e.g., "24hr", "0600-2200").
     */
    @Size(max = 20, message = "Operating hours must not exceed 20 characters")
    String hours;

    /**
     * Remarks about the frequency.
     */
    @Size(max = 100, message = "Remarks must not exceed 100 characters")
    String remarks;

    /**
     * Primary frequency for this service.
     */
    @Builder.Default
    boolean primary = false;

    /**
     * Format frequency for display.
     */
    public String formatFrequency() {
        if (frequency == null) return "N/A";
        
        // VHF frequencies display 3 decimal places
        if (frequency < 200) {
            return String.format("%.3f MHz", frequency);
        }
        // UHF frequencies display 2 decimal places
        return String.format("%.2f MHz", frequency);
    }

    /**
     * Check if this is an emergency frequency.
     */
    public boolean isEmergencyFrequency() {
        return serviceType == ServiceType.EMERGENCY ||
               serviceType == ServiceType.GUARD ||
               (frequency != null && Math.abs(frequency - 121.5) < 0.01);
    }
}
