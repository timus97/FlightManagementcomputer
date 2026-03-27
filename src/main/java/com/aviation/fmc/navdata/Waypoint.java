package com.aviation.fmc.navdata;

import com.aviation.fmc.common.GeoCoordinate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Value;

/**
 * Represents a navigation waypoint in the navigation database.
 * Waypoints are fixed geographical positions used for route definition.
 * Follows ARINC 424 specification.
 * 
 * Invariants:
 * - Identifier must be 2-5 characters for enroute waypoints
 * - Coordinates must be valid WGS-84 positions
 * - Waypoint type determines usage constraints
 * - Duplicate identifiers require region code disambiguation
 */
@Value
@Builder
public class Waypoint {

    public enum WaypointType {
        // Enroute waypoints
        ENROUTE_WAYPOINT,       // Named enroute waypoint
        RNAV_WAYPOINT,          // RNAV waypoint (5-character name)
        
        // Terminal waypoints
        TERMINAL_WAYPOINT,      // SID/STAR/Approach waypoint
        RUNWAY_WAYPOINT,        // Runway threshold waypoint
        
        // Navaid-related
        VOR_DME,                // VOR/DME station
        VORTAC,                 // VOR/TACAN station
        NDB,                    // Non-directional beacon
        DME,                    // Standalone DME
        TACAN,                  // TACAN station
        
        // Special
        HOLDING,                // Holding pattern fix
        FLYOVER,                // Fly-over waypoint
        
        // Composite
        INTXN,                  // Intersection
        
        // Oceanic
        OCEANIC_WAYPOINT,       // Oceanic waypoint (e.g., "N52W030")
        
        // User defined
        PILOT_DEFINED,          // User-created waypoint
        TEMPORARY               // Temporary waypoint
    }

    public enum UsageRestriction {
        NONE,                   // No restrictions
        HIGH_ALTITUDE,          // High altitude airways only
        LOW_ALTITUDE,           // Low altitude airways only
        TERMINAL_ONLY,          // Terminal area only
        SID_ONLY,               // SID usage only
        STAR_ONLY,              // STAR usage only
        APPROACH_ONLY           // Approach usage only
    }

    /**
     * Waypoint identifier.
     * - Named waypoints: 2-5 characters
     * - RNAV waypoints: 5 characters
     * - Oceanic waypoints: Format "N52W030"
     */
    @NotBlank(message = "Waypoint identifier is required")
    @Pattern(regexp = "[A-Z0-9]{2,5}|[NS]\\d{2}[EW]\\d{3}", 
             message = "Invalid waypoint identifier format")
    String identifier;

    /**
     * ICAO region code for disambiguation (2 characters).
     * Required when duplicate identifiers exist.
     */
    @Pattern(regexp = "[A-Z0-9]{2}", message = "Region code must be 2 characters")
    String regionCode;

    @NotNull(message = "Waypoint type is required")
    WaypointType type;

    @NotNull(message = "Coordinate is required")
    @Valid
    GeoCoordinate coordinate;

    /**
     * Usage restriction for this waypoint.
     */
    @NotNull
    @Builder.Default
    UsageRestriction usageRestriction = UsageRestriction.NONE;

    /**
     * For navaid waypoints - frequency in MHz (VOR) or kHz (NDB).
     */
    Double frequency;

    /**
     * For DME/TACAN/VORTAC - DME channel.
     */
    String dmeChannel;

    /**
     * Navaid class (for VOR/NDB): T=Terminal, L=Low altitude, H=High altitude
     */
    @Pattern(regexp = "[TLH]", message = "Invalid navaid class")
    String navaidClass;

    /**
     * Magnetic variation at this waypoint location.
     */
    Double magneticVariation;

    /**
     * Waypoint is a compulsory reporting point.
     */
    @Builder.Default
    boolean compulsoryReportingPoint = false;

    /**
     * Waypoint is fly-over (vs. fly-by).
     */
    @Builder.Default
    boolean flyOver = false;

    /**
     * Published holding pattern available at this waypoint.
     */
    HoldingPattern holdingPattern;

    /**
     * Check if waypoint is a navaid (VOR, NDB, DME, etc.).
     */
    public boolean isNavaid() {
        return type == WaypointType.VOR_DME ||
               type == WaypointType.VORTAC ||
               type == WaypointType.NDB ||
               type == WaypointType.DME ||
               type == WaypointType.TACAN;
    }

    /**
     * Get frequency as formatted string.
     */
    public String getFormattedFrequency() {
        if (frequency == null) return null;
        
        if (type == WaypointType.NDB) {
            return String.format("%.1f kHz", frequency);
        } else {
            return String.format("%.2f MHz", frequency);
        }
    }

    /**
     * Calculate magnetic bearing from this waypoint to another.
     */
    public double magneticBearingTo(Waypoint other) {
        double trueBearing = coordinate.bearingTo(other.coordinate);
        if (magneticVariation != null) {
            return (trueBearing - magneticVariation + 360) % 360;
        }
        return trueBearing;
    }

    /**
     * Calculate distance to another waypoint in nautical miles.
     */
    public double distanceTo(Waypoint other) {
        return coordinate.distanceTo(other.coordinate);
    }

    /**
     * Create a unique key for this waypoint (including region if needed).
     */
    public String getUniqueKey() {
        if (regionCode != null && !regionCode.isEmpty()) {
            return regionCode + "/" + identifier;
        }
        return identifier;
    }

    /**
     * Check if waypoint can be used for the specified airway type.
     */
    public boolean isUsableForAirwayType(Airway.AirwayType airwayType) {
        return switch (usageRestriction) {
            case NONE -> true;
            case HIGH_ALTITUDE -> airwayType == Airway.AirwayType.HIGH_ALTITUDE || 
                                   airwayType == Airway.AirwayType.JET;
            case LOW_ALTITUDE -> airwayType == Airway.AirwayType.LOW_ALTITUDE || 
                                 airwayType == Airway.AirwayType.VICTOR;
            case TERMINAL_ONLY -> airwayType == Airway.AirwayType.TERMINAL;
            default -> false;
        };
    }
}
