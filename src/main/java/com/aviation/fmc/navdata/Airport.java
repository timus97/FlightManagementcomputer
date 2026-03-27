package com.aviation.fmc.navdata;

import com.aviation.fmc.common.GeoCoordinate;
import com.aviation.fmc.common.MagneticVariation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Represents an airport in the navigation database.
 * Follows ARINC 424 specification for airport records.
 * 
 * Invariants:
 * - ICAO code must be exactly 4 alphanumeric characters
 * - IATA code (if present) must be exactly 3 alphanumeric characters
 * - At least one runway must exist
 * - Elevation must be consistent with runway thresholds
 * - Magnetic variation must be present for instrument airports
 */
@Value
@Builder
public class Airport {

    public enum AirportType {
        CIVIL,          // Civilian airport
        MILITARY,       // Military airbase
        JOINT,          // Joint civil/military use
        SEAPLANE,       // Seaplane base
        HELIPORT,       // Heliport only
        CLOSED          // Permanently closed
    }

    public enum LandingType {
        LAND,           // Land airport
        WATER,          // Water landing
        AMPHIBIOUS      // Both land and water
    }

    /**
     * ICAO airport identifier (e.g., "KJFK", "EGLL", "LFPG").
     * Exactly 4 alphanumeric characters.
     */
    @NotNull(message = "ICAO code is required")
    @Pattern(regexp = "[A-Z0-9]{4}", message = "ICAO code must be exactly 4 alphanumeric characters")
    String icaoCode;

    /**
     * IATA airport code (e.g., "JFK", "LHR", "CDG").
     * Exactly 3 alphanumeric characters. Optional.
     */
    @Pattern(regexp = "[A-Z0-9]{3}", message = "IATA code must be exactly 3 alphanumeric characters")
    String iataCode;

    @NotBlank(message = "Airport name is required")
    @Size(max = 50, message = "Airport name must not exceed 50 characters")
    String name;

    @NotBlank(message = "City name is required")
    @Size(max = 40, message = "City name must not exceed 40 characters")
    String city;

    @NotBlank(message = "Country code is required")
    @Pattern(regexp = "[A-Z]{2}", message = "Country code must be 2-letter ISO code")
    String countryCode;

    @NotNull(message = "Airport type is required")
    AirportType type;

    @NotNull(message = "Landing type is required")
    LandingType landingType;

    @NotNull(message = "Reference coordinate is required")
    @Valid
    GeoCoordinate referencePoint;

    /**
     * Airport magnetic variation for runway headings.
     */
    @Valid
    MagneticVariation magneticVariation;

    /**
     * Transition altitude in feet.
     * Below this altitude, altitudes are expressed as MSL.
     * Above this altitude, flight levels are used.
     */
    @Min(value = 1000, message = "Transition altitude must be >= 1000 ft")
    @Max(value = 25000, message = "Transition altitude must be <= 25000 ft")
    Integer transitionAltitude;

    /**
     * Transition level (FL) - normally calculated from transition altitude.
     */
    @Min(value = 30, message = "Transition level must be >= FL30")
    @Max(value = 600, message = "Transition level must be <= FL600")
    Integer transitionLevel;

    @Singular
    @NotEmpty(message = "At least one runway is required")
    @Valid
    List<Runway> runways;

    @Singular
    @Valid
    List<TerminalProcedure> sids;

    @Singular
    @Valid
    List<TerminalProcedure> stars;

    @Singular
    @Valid
    List<TerminalProcedure> approaches;

    /**
     * Communication frequencies (ATIS, Tower, Ground, etc.).
     */
    @Singular
    @Valid
    List<CommunicationFrequency> frequencies;

    /**
     * Check if airport supports instrument operations.
     */
    public boolean isInstrumentAirport() {
        return !sids.isEmpty() || !stars.isEmpty() || !approaches.isEmpty();
    }

    /**
     * Find runway by identifier.
     */
    public Optional<Runway> findRunway(String runwayId) {
        return runways.stream()
                .filter(r -> r.getIdentifier().equals(runwayId))
                .findFirst();
    }

    /**
     * Get active runways based on wind direction.
     */
    public List<Runway> getActiveRunways(double windDirection, double windSpeed) {
        return runways.stream()
                .filter(r -> r.isActiveForWind(windDirection, windSpeed))
                .toList();
    }

    /**
     * Validates that airport data is consistent.
     */
    public boolean isValid() {
        // ICAO code validation
        if (icaoCode == null || !icaoCode.matches("[A-Z0-9]{4}")) {
            return false;
        }

        // Runway consistency check
        for (Runway runway : runways) {
            if (!runway.getAirportIcao().equals(icaoCode)) {
                return false;
            }
        }

        // Instrument airport must have magnetic variation
        if (isInstrumentAirport() && magneticVariation == null) {
            return false;
        }

        return true;
    }
}
