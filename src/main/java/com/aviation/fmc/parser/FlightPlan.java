package com.aviation.fmc.parser;

import com.aviation.fmc.common.Altitude;
import com.aviation.fmc.navdata.Airport;
import com.aviation.fmc.validation.ValidationResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Represents a parsed flight plan for FMC processing.
 * This is the output model from the flight plan parser.
 * 
 * Invariants:
 * - Flight number must be alphanumeric, 1-8 characters
 * - Departure and arrival airports must be specified
 * - Route must have at least one element
 * - Total distance must be positive
 * - ETD must be before ETA
 */
@Value
@Builder
public class FlightPlan {

    public enum FlightPlanType {
        IFR,            // Instrument Flight Rules
        VFR,            // Visual Flight Rules
        IFR_VFR,        // IFR to VFR
        VFR_IFR,        // VFR to IFR
        DVFR            // Defense VFR
    }

    public enum FlightPlanStatus {
        DRAFT,          // Being created
        ACTIVE,         // Currently flying
        COMPLETED,      // Flight completed
        CANCELLED,      // Flight cancelled
        SUSPENDED       // Temporarily suspended
    }

    /**
     * Flight number/identifier.
     * Format: Airline code + numeric (e.g., "BA123", "UAL456")
     */
    @NotBlank(message = "Flight number is required")
    @Pattern(regexp = "[A-Z0-9]{1,8}", message = "Flight number must be 1-8 alphanumeric characters")
    String flightNumber;

    /**
     * Aircraft registration/tail number.
     */
    @Pattern(regexp = "[A-Z]-[A-Z]{4}|[A-Z]{2}-[A-Z]{3}|[A-Z0-9]{3,6}", 
             message = "Invalid aircraft registration format")
    String aircraftRegistration;

    /**
     * Aircraft type designator (ICAO code).
     * e.g., "B738", "A320", "C172"
     */
    @NotBlank(message = "Aircraft type is required")
    @Pattern(regexp = "[A-Z0-9]{2,4}", message = "Invalid aircraft type designator")
    String aircraftType;

    /**
     * Flight rules for this flight plan.
     */
    @NotNull(message = "Flight plan type is required")
    @Builder.Default
    FlightPlanType flightPlanType = FlightPlanType.IFR;

    /**
     * Current status of the flight plan.
     */
    @NotNull
    @Builder.Default
    FlightPlanStatus status = FlightPlanStatus.DRAFT;

    /**
     * Departure airport.
     */
    @NotNull(message = "Departure airport is required")
    @Valid
    Airport departure;

    /**
     * Departure runway (if specified).
     */
    @Pattern(regexp = "0[1-9]|[12][0-9]|3[0-6][LCR]?", message = "Invalid runway identifier")
    String departureRunway;

    /**
     * SID (Standard Instrument Departure) name.
     */
    @Size(max = 10, message = "SID name must not exceed 10 characters")
    String sid;

    /**
     * SID transition waypoint.
     */
    @Size(max = 10, message = "SID transition must not exceed 10 characters")
    String sidTransition;

    /**
     * Estimated departure time (UTC).
     */
    @NotNull(message = "Estimated departure time is required")
    LocalDateTime estimatedDepartureTime;

    /**
     * Destination/arrival airport.
     */
    @NotNull(message = "Destination airport is required")
    @Valid
    Airport destination;

    /**
     * Arrival runway (if specified).
     */
    @Pattern(regexp = "0[1-9]|[12][0-9]|3[0-6][LCR]?", message = "Invalid runway identifier")
    String arrivalRunway;

    /**
     * STAR (Standard Terminal Arrival Route) name.
     */
    @Size(max = 10, message = "STAR name must not exceed 10 characters")
    String star;

    /**
     * STAR transition waypoint.
     */
    @Size(max = 10, message = "STAR transition must not exceed 10 characters")
    String starTransition;

    /**
     * Approach procedure name.
     */
    @Size(max = 15, message = "Approach name must not exceed 15 characters")
    String approach;

    /**
     * Estimated arrival time (UTC).
     */
    LocalDateTime estimatedArrivalTime;

    /**
     * Route elements (waypoints, airways, direct segments).
     */
    @NotEmpty(message = "Route must have at least one element")
    @Valid
    List<RouteElement> route;

    /**
     * Cruise altitude/level.
     */
    @NotNull(message = "Cruise altitude is required")
    @Valid
    Altitude cruiseAltitude;

    /**
     * True airspeed in knots.
     */
    @Min(value = 50, message = "True airspeed must be >= 50 knots")
    @Max(value = 1000, message = "True airspeed must be <= 1000 knots")
    Integer trueAirspeed;

    /**
     * Alternate airports.
     */
    @Singular
    @Valid
    List<Airport> alternates;

    /**
     * Fuel on board in minutes (endurance).
     */
    @Positive(message = "Fuel endurance must be positive")
    Duration fuelOnBoard;

    /**
     * Fuel required for the flight.
     */
    @Positive(message = "Fuel required must be positive")
    Duration fuelRequired;

    /**
     * Persons on board.
     */
    @Min(value = 1, message = "Persons on board must be >= 1")
    @Max(value = 999, message = "Persons on board must be <= 999")
    Integer personsOnBoard;

    /**
     * Remarks and additional information.
     */
    @Size(max = 500, message = "Remarks must not exceed 500 characters")
    String remarks;

    /**
     * Get total route distance in nautical miles.
     */
    public double getTotalDistance() {
        return route.stream()
                .mapToDouble(RouteElement::getDistance)
                .sum();
    }

    /**
     * Get estimated flight duration.
     */
    public Duration getEstimatedDuration() {
        if (estimatedDepartureTime != null && estimatedArrivalTime != null) {
            return Duration.between(estimatedDepartureTime, estimatedArrivalTime);
        }
        if (trueAirspeed != null && trueAirspeed > 0) {
            double hours = getTotalDistance() / trueAirspeed;
            return Duration.ofMinutes((long) (hours * 60));
        }
        return null;
    }

    /**
     * Check if flight plan is valid for activation.
     */
    public boolean isValidForActivation() {
        return status == FlightPlanStatus.DRAFT &&
               departure != null &&
               destination != null &&
               estimatedDepartureTime != null &&
               cruiseAltitude != null &&
               !route.isEmpty();
    }

    /**
     * Find route element by waypoint identifier.
     */
    public Optional<RouteElement> findRouteElement(String waypointId) {
        return route.stream()
                .filter(r -> r.getWaypoint() != null && 
                           r.getWaypoint().getIdentifier().equals(waypointId))
                .findFirst();
    }

    /**
     * Get the active leg of the flight plan.
     */
    public Optional<RouteElement> getActiveLeg() {
        return route.stream()
                .filter(RouteElement::isActive)
                .findFirst();
    }

    /**
     * Calculate remaining distance to destination.
     */
    public double getRemainingDistance() {
        boolean foundActive = false;
        double remaining = 0.0;
        
        for (RouteElement element : route) {
            if (element.isActive()) {
                foundActive = true;
            }
            if (foundActive) {
                remaining += element.getDistance();
            }
        }
        return remaining;
    }

    /**
     * Validate fuel requirements.
     */
    public ValidationResult validateFuel() {
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        
        if (fuelRequired == null || fuelOnBoard == null) {
            return ValidationResult.success();
        }
        
        Duration requiredWithReserve = fuelRequired.plusMinutes(45); // Minimum reserve
        if (fuelOnBoard.compareTo(requiredWithReserve) < 0) {
            errors.add(ValidationResult.ValidationError.of(
                "FLIGHTPLAN_INSUFFICIENT_FUEL",
                String.format("Insufficient fuel: required + reserve = %d min, available = %d min",
                    requiredWithReserve.toMinutes(), fuelOnBoard.toMinutes()),
                "fuelOnBoard",
                fuelOnBoard.toMinutes()
            ));
        }
        
        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }
}
