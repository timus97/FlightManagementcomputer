package com.aviation.fmc.validation;

import com.aviation.fmc.navdata.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates navigation data entities against aviation invariants.
 * Implements comprehensive validation for airports, waypoints, airways, and procedures.
 */
public class NavdataValidator {

    /**
     * Validates an airport against all invariants.
     */
    public ValidationResult validateAirport(Airport airport) {
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        List<ValidationResult.ValidationWarning> warnings = new ArrayList<>();

        // ICAO code format
        if (airport.getIcaoCode() == null || !airport.getIcaoCode().matches("[A-Z0-9]{4}")) {
            errors.add(ValidationResult.ValidationError.of(
                "AIRPORT_ICAO_INVALID",
                "ICAO code must be exactly 4 alphanumeric characters",
                "icaoCode",
                airport.getIcaoCode()
            ));
        }

        // IATA code format (if present)
        if (airport.getIataCode() != null && !airport.getIataCode().matches("[A-Z0-9]{3}")) {
            errors.add(ValidationResult.ValidationError.of(
                "AIRPORT_IATA_INVALID",
                "IATA code must be exactly 3 alphanumeric characters",
                "iataCode",
                airport.getIataCode()
            ));
        }

        // At least one runway
        if (airport.getRunways() == null || airport.getRunways().isEmpty()) {
            errors.add(ValidationResult.ValidationError.of(
                "AIRPORT_NO_RUNWAYS",
                "Airport must have at least one runway",
                "runways",
                null
            ));
        }

        // Runway consistency
        if (airport.getRunways() != null) {
            for (Runway runway : airport.getRunways()) {
                if (!runway.getAirportIcao().equals(airport.getIcaoCode())) {
                    errors.add(ValidationResult.ValidationError.of(
                        "AIRPORT_RUNWAY_MISMATCH",
                        String.format("Runway %s belongs to different airport %s", 
                            runway.getIdentifier(), runway.getAirportIcao()),
                        "runways",
                        runway.getIdentifier()
                    ));
                }
            }
        }

        // Instrument airport requires magnetic variation
        if (airport.isInstrumentAirport() && airport.getMagneticVariation() == null) {
            errors.add(ValidationResult.ValidationError.of(
                "AIRPORT_MISSING_MAGVAR",
                "Instrument airport must have magnetic variation defined",
                "magneticVariation",
                null
            ));
        }

        // Transition altitude/level consistency
        if (airport.getTransitionAltitude() != null && airport.getTransitionLevel() != null) {
            // Transition level should roughly correspond to transition altitude
            int expectedLevel = (airport.getTransitionAltitude() / 100) + 10; // Rough approximation
            if (Math.abs(airport.getTransitionLevel() - expectedLevel) > 20) {
                warnings.add(ValidationResult.ValidationWarning.of(
                    "AIRPORT_TRANSITION_MISMATCH",
                    String.format("Transition altitude %d and level FL%d may be inconsistent",
                        airport.getTransitionAltitude(), airport.getTransitionLevel()),
                    "transitionLevel"
                ));
            }
        }

        // Validate all runways
        if (airport.getRunways() != null) {
            for (Runway runway : airport.getRunways()) {
                ValidationResult runwayResult = validateRunway(runway);
                if (!runwayResult.isValid()) {
                    errors.addAll(runwayResult.getErrors());
                }
                warnings.addAll(runwayResult.getWarnings());
            }
        }

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, errors, warnings);
    }

    /**
     * Validates a runway against all invariants.
     */
    public ValidationResult validateRunway(Runway runway) {
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        List<ValidationResult.ValidationWarning> warnings = new ArrayList<>();

        // Identifier format validation
        if (runway.getIdentifier() == null || 
            !runway.getIdentifier().matches("0[1-9]|[12][0-9]|3[0-6][LCR]?")) {
            errors.add(ValidationResult.ValidationError.of(
                "RUNWAY_ID_INVALID",
                "Invalid runway identifier format",
                "identifier",
                runway.getIdentifier()
            ));
        }

        // TORA <= TODA and TORA <= ASDA
        if (runway.getTora() != null) {
            if (runway.getToda() != null && runway.getTora() > runway.getToda()) {
                errors.add(ValidationResult.ValidationError.of(
                    "RUNWAY_TORA_TODA",
                    "TORA must be less than or equal to TODA",
                    "tora",
                    runway.getTora()
                ));
            }
            if (runway.getAsda() != null && runway.getTora() > runway.getAsda()) {
                errors.add(ValidationResult.ValidationError.of(
                    "RUNWAY_TORA_ASDA",
                    "TORA must be less than or equal to ASDA",
                    "tora",
                    runway.getTora()
                ));
            }
        }

        // Heading matches identifier
        if (runway.getMagneticHeading() != null && runway.getIdentifier() != null) {
            String digits = runway.getIdentifier().replaceAll("[^0-9]", "");
            int expectedHeading = Integer.parseInt(digits) * 10;
            double headingDiff = Math.abs(runway.getMagneticHeading() - expectedHeading);
            if (headingDiff > 5.0 && headingDiff < 355.0) {
                warnings.add(ValidationResult.ValidationWarning.of(
                    "RUNWAY_HEADING_MISMATCH",
                    String.format("Magnetic heading %.1f° does not match runway identifier %s (expected ~%d°)",
                        runway.getMagneticHeading(), runway.getIdentifier(), expectedHeading),
                    "magneticHeading"
                ));
            }
        }

        // True heading vs magnetic heading consistency
        if (runway.getTrueHeading() != null && runway.getMagneticHeading() != null) {
            double diff = Math.abs(runway.getTrueHeading() - runway.getMagneticHeading());
            if (diff > 30.0 && diff < 330.0) {
                warnings.add(ValidationResult.ValidationWarning.of(
                    "RUNWAY_HEADING_DIFF_LARGE",
                    String.format("Large difference between true (%.1f°) and magnetic (%.1f°) headings",
                        runway.getTrueHeading(), runway.getMagneticHeading()),
                    "trueHeading"
                ));
            }
        }

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, errors, warnings);
    }

    /**
     * Validates a waypoint against all invariants.
     */
    public ValidationResult validateWaypoint(Waypoint waypoint) {
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        List<ValidationResult.ValidationWarning> warnings = new ArrayList<>();

        // Identifier format
        if (waypoint.getIdentifier() == null ||
            (!waypoint.getIdentifier().matches("[A-Z0-9]{2,5}") &&
             !waypoint.getIdentifier().matches("[NS]\\d{2}[EW]\\d{3}"))) {
            errors.add(ValidationResult.ValidationError.of(
                "WAYPOINT_ID_INVALID",
                "Invalid waypoint identifier format",
                "identifier",
                waypoint.getIdentifier()
            ));
        }

        // Navaid frequency validation
        if (waypoint.isNavaid()) {
            if (waypoint.getFrequency() == null) {
                errors.add(ValidationResult.ValidationError.of(
                    "WAYPOINT_NAV_NO_FREQ",
                    "Navaid waypoint must have frequency",
                    "frequency",
                    null
                ));
            } else {
                // VOR: 108.0-117.95 MHz
                // NDB: 190-1750 kHz
                if (waypoint.getType() == Waypoint.WaypointType.VOR_DME || 
                    waypoint.getType() == Waypoint.WaypointType.VORTAC) {
                    if (waypoint.getFrequency() < 108.0 || waypoint.getFrequency() > 118.0) {
                        errors.add(ValidationResult.ValidationError.of(
                            "WAYPOINT_VOR_FREQ_INVALID",
                            "VOR frequency must be between 108.0 and 117.95 MHz",
                            "frequency",
                            waypoint.getFrequency()
                        ));
                    }
                }
            }
        }

        // Coordinate validation
        if (waypoint.getCoordinate() == null) {
            errors.add(ValidationResult.ValidationError.of(
                "WAYPOINT_NO_COORD",
                "Waypoint must have coordinates",
                "coordinate",
                null
            ));
        }

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, errors, warnings);
    }

    /**
     * Validates an airway against all invariants.
     */
    public ValidationResult validateAirway(Airway airway) {
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        List<ValidationResult.ValidationWarning> warnings = new ArrayList<>();

        // Designator format
        if (airway.getDesignator() == null ||
            !airway.getDesignator().matches("[JVUTLQMNPY]\\d{1,4}|[A-Z]{2}\\d{3}")) {
            errors.add(ValidationResult.ValidationError.of(
                "AIRWAY_DESIGNATOR_INVALID",
                "Invalid airway designator format",
                "designator",
                airway.getDesignator()
            ));
        }

        // At least 2 waypoints
        if (airway.getSegments() == null || airway.getSegments().size() < 1) {
            errors.add(ValidationResult.ValidationError.of(
                "AIRWAY_TOO_SHORT",
                "Airway must have at least 2 waypoints (1 segment)",
                "segments",
                airway.getSegments()
            ));
        }

        // Segment connectivity
        if (airway.getSegments() != null && airway.getSegments().size() > 1) {
            for (int i = 0; i < airway.getSegments().size() - 1; i++) {
                AirwaySegment current = airway.getSegments().get(i);
                AirwaySegment next = airway.getSegments().get(i + 1);
                
                String currentEnd = current.getToWaypoint().getIdentifier();
                String nextStart = next.getFromWaypoint().getIdentifier();
                
                if (!currentEnd.equals(nextStart)) {
                    errors.add(ValidationResult.ValidationError.of(
                        "AIRWAY_SEGMENT_DISCONNECT",
                        String.format("Airway segments are not connected: %s != %s", 
                            currentEnd, nextStart),
                        "segments",
                        i
                    ));
                }
            }
        }

        // Altitude constraints consistency
        if (airway.getMinimumEnrouteAltitude() != null && 
            airway.getMaximumAuthorizedAltitude() != null) {
            if (airway.getMinimumEnrouteAltitude() > airway.getMaximumAuthorizedAltitude()) {
                errors.add(ValidationResult.ValidationError.of(
                    "AIRWAY_ALTITUDE_INVALID",
                    "MEA cannot be greater than MAA",
                    "minimumEnrouteAltitude",
                    airway.getMinimumEnrouteAltitude()
                ));
            }
        }

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, errors, warnings);
    }

    /**
     * Validates a terminal procedure against all invariants.
     */
    public ValidationResult validateTerminalProcedure(TerminalProcedure procedure) {
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        List<ValidationResult.ValidationWarning> warnings = new ArrayList<>();

        // At least one leg
        if (procedure.getLegs() == null || procedure.getLegs().isEmpty()) {
            errors.add(ValidationResult.ValidationError.of(
                "PROCEDURE_NO_LEGS",
                "Procedure must have at least one leg",
                "legs",
                null
            ));
        }

        // First leg should be an IF (Initial Fix) for most procedures
        if (procedure.getLegs() != null && !procedure.getLegs().isEmpty()) {
            ProcedureLeg firstLeg = procedure.getLegs().get(0);
            if (firstLeg.getType() != ProcedureLeg.LegType.IF &&
                procedure.getType() != TerminalProcedure.ProcedureType.APPROACH) {
                warnings.add(ValidationResult.ValidationWarning.of(
                    "PROCEDURE_FIRST_LEG",
                    "First leg of procedure should typically be an Initial Fix (IF)",
                    "legs[0].type"
                ));
            }
        }

        // Approach type must be specified for approaches
        if (procedure.getType() == TerminalProcedure.ProcedureType.APPROACH &&
            procedure.getApproachType() == null) {
            warnings.add(ValidationResult.ValidationWarning.of(
                "APPROACH_NO_TYPE",
                "Approach procedure should specify approach type",
                "approachType"
            ));
        }

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, errors, warnings);
    }
}
