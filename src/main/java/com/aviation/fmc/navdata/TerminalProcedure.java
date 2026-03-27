package com.aviation.fmc.navdata;

import com.aviation.fmc.common.Altitude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * Represents a terminal procedure: SID, STAR, or Approach.
 * Standard Instrument Departure (SID), Standard Terminal Arrival Route (STAR),
 * or Instrument Approach Procedure.
 * 
 * Invariants:
 * - Procedure name must follow ICAO conventions
 * - Must have at least one leg
 * - Runway reference must exist at airport
 * - Waypoints must be sequential
 */
@Value
@Builder
public class TerminalProcedure {

    public enum ProcedureType {
        SID,            // Standard Instrument Departure
        STAR,           // Standard Terminal Arrival Route
        APPROACH,       // Instrument Approach Procedure
        DEPARTURE,      // Non-SID departure procedure
        ARRIVAL         // Non-STAR arrival procedure
    }

    public enum ApproachType {
        ILS,            // Instrument Landing System
        LOC,            // Localizer only
        LDA,            // Localizer Directional Aid
        SDF,            // Simplified Directional Facility
        VOR,            // VOR approach
        NDB,            // NDB approach
        VOR_DME,        // VOR/DME approach
        NDB_DME,        // NDB/DME approach
        RNAV_GPS,       // RNAV (GPS) approach
        RNP,            // Required Navigation Performance
        VISUAL,         // Visual approach
        CIRCLING        // Circling approach
    }

    /**
     * Procedure identifier (e.g., "BOSOX3", "HARYS4", "ILS 27L").
     */
    @NotBlank(message = "Procedure name is required")
    @Size(max = 10, message = "Procedure name must not exceed 10 characters")
    String name;

    /**
     * Computer code for the procedure (often same as name).
     */
    @Size(max = 10, message = "Computer code must not exceed 10 characters")
    String computerCode;

    @NotNull(message = "Procedure type is required")
    ProcedureType type;

    /**
     * For approach procedures - the specific approach type.
     */
    ApproachType approachType;

    /**
     * Airport ICAO code this procedure belongs to.
     */
    @NotNull(message = "Airport ICAO is required")
    @Pattern(regexp = "[A-Z0-9]{4}", message = "Invalid ICAO code")
    String airportIcao;

    /**
     * Runway this procedure is associated with (if applicable).
     * Can be null for airport-wide procedures.
     */
    @Pattern(regexp = "0[1-9]|[12][0-9]|3[0-6][LCR]?", message = "Invalid runway identifier")
    String runway;

    /**
     * Procedure legs/waypoints in order.
     */
    @NotEmpty(message = "At least one leg is required")
    @Valid
    List<ProcedureLeg> legs;

    /**
     * Required navigation equipment.
     */
    @Singular("requiredEquipment")
    List<String> requiredEquipment;

    /**
     * Procedure is RNAV-based.
     */
    @Builder.Default
    boolean rnav = false;

    /**
     * Procedure requires GPS.
     */
    @Builder.Default
    boolean requiresGps = false;

    /**
     * Calculate total distance of the procedure.
     */
    public double calculateDistance() {
        double distance = 0.0;
        for (int i = 0; i < legs.size() - 1; i++) {
            distance += legs.get(i).getPathDistance();
        }
        return distance;
    }

    /**
     * Get the initial fix (first waypoint).
     */
    public Waypoint getInitialFix() {
        return legs.get(0).getFix();
    }

    /**
     * Get the final fix (last waypoint).
     */
    public Waypoint getFinalFix() {
        return legs.get(legs.size() - 1).getFix();
    }

    /**
     * Check if this is a vector-based procedure (radar vectors).
     */
    public boolean hasVectorLegs() {
        return legs.stream().anyMatch(l -> l.getType() == ProcedureLeg.LegType.VECTOR);
    }
}
