package com.aviation.fmc.navdata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Optional;

/**
 * Represents an airway (ATS route) in the navigation database.
 * Airways are predefined routes connecting waypoints for enroute navigation.
 * Follows ARINC 424 specification.
 * 
 * Invariants:
 * - Designator must follow ICAO conventions (e.g., "J123", "V456", "L750")
 * - Must have at least 2 waypoints (start and end)
 * - Waypoints must be sequential and connected
 * - Distance between waypoints must be reasonable (< 500 NM typically)
 * - Direction must be consistent with waypoint ordering
 */
@Value
@Builder
public class Airway {

    public enum AirwayType {
        HIGH_ALTITUDE,      // Jet routes (J-routes) - FL180 and above
        LOW_ALTITUDE,       // Victor airways (V-routes) - below FL180
        JET,                // Jet route (alternative naming)
        VICTOR,             // Victor airway (alternative naming)
        RNAV,               // Area Navigation route
        TERMINAL,           // Terminal area route
        HELICOPTER,         // Helicopter route
        OCEANIC,            // Oceanic route
        DIRECT              // Direct routing (point-to-point)
    }

    public enum Direction {
        FORWARD,            // Can only be flown in waypoint order
        BACKWARD,           // Can only be flown in reverse order
        BIDIRECTIONAL       // Can be flown in either direction
    }

    /**
     * Airway designator following ICAO conventions.
     * - J-routes: J followed by 1-3 digits (e.g., "J123")
     * - V-routes: V followed by 1-3 digits (e.g., "V12")
     * - RNAV: L, M, N, P followed by digits (e.g., "L750")
     * - European: U, T, Q, Y routes
     */
    @NotBlank(message = "Airway designator is required")
    @Pattern(regexp = "[JVUTLQMNPY]\\d{1,4}|[A-Z]{2}\\d{3}", 
             message = "Invalid airway designator format")
    String designator;

    @NotNull(message = "Airway type is required")
    AirwayType type;

    /**
     * Flight direction restriction.
     */
    @NotNull
    @Builder.Default
    Direction direction = Direction.BIDIRECTIONAL;

    /**
     * Minimum enroute altitude (MEA) in feet.
     * Ensures terrain and obstacle clearance.
     */
    @Min(value = 1000, message = "MEA must be >= 1000 ft")
    @Max(value = 45000, message = "MEA must be <= 45000 ft")
    Integer minimumEnrouteAltitude;

    /**
     * Minimum obstruction clearance altitude (MOCA) in feet.
     * Provides obstacle clearance but not navaid reception.
     */
    @Min(value = 1000, message = "MOCA must be >= 1000 ft")
    @Max(value = 45000, message = "MOCA must be <= 45000 ft")
    Integer minimumObstructionClearanceAltitude;

    /**
     * Maximum authorized altitude (MAA) in feet.
     */
    @Min(value = 1000, message = "MAA must be >= 1000 ft")
    @Max(value = 60000, message = "MAA must be <= 60000 ft")
    Integer maximumAuthorizedAltitude;

    /**
     * Changeover point from one navaid to another.
     * Distance from the waypoint in nautical miles.
     */
    Double changeoverPoint;

    /**
     * Distance between waypoints in nautical miles.
     */
    @Positive(message = "Total distance must be positive")
    Double totalDistance;

    /**
     * Waypoint sequence defining the airway.
     * Must be ordered from start to end.
     */
    @NotEmpty(message = "At least 2 waypoints are required")
    @Size(min = 2, message = "Airway must have at least 2 waypoints")
    @Valid
    List<AirwaySegment> segments;

    /**
     * Get the starting waypoint of the airway.
     */
    public Waypoint getStartWaypoint() {
        return segments.get(0).getFromWaypoint();
    }

    /**
     * Get the ending waypoint of the airway.
     */
    public Waypoint getEndWaypoint() {
        return segments.get(segments.size() - 1).getToWaypoint();
    }

    /**
     * Check if airway contains a specific waypoint.
     */
    public boolean containsWaypoint(String waypointId) {
        return segments.stream()
                .anyMatch(s -> s.getFromWaypoint().getIdentifier().equals(waypointId) ||
                              s.getToWaypoint().getIdentifier().equals(waypointId));
    }

    /**
     * Find segment containing a specific waypoint.
     */
    public Optional<AirwaySegment> findSegmentWithWaypoint(String waypointId) {
        return segments.stream()
                .filter(s -> s.getFromWaypoint().getIdentifier().equals(waypointId) ||
                            s.getToWaypoint().getIdentifier().equals(waypointId))
                .findFirst();
    }

    /**
     * Calculate total airway length in nautical miles.
     */
    public double calculateTotalDistance() {
        return segments.stream()
                .mapToDouble(AirwaySegment::getDistance)
                .sum();
    }

    /**
     * Check if flight along this airway is valid given altitude constraints.
     */
    public boolean isAltitudeValid(int altitudeFt) {
        if (minimumEnrouteAltitude != null && altitudeFt < minimumEnrouteAltitude) {
            return false;
        }
        if (maximumAuthorizedAltitude != null && altitudeFt > maximumAuthorizedAltitude) {
            return false;
        }
        return true;
    }

    /**
     * Validate airway consistency.
     */
    public boolean isValid() {
        // Check segments connect properly
        for (int i = 0; i < segments.size() - 1; i++) {
            AirwaySegment current = segments.get(i);
            AirwaySegment next = segments.get(i + 1);
            
            if (!current.getToWaypoint().getIdentifier()
                       .equals(next.getFromWaypoint().getIdentifier())) {
                return false;
            }
        }
        
        // Check total distance matches sum of segments
        double calculatedDistance = calculateTotalDistance();
        if (totalDistance != null && 
            Math.abs(calculatedDistance - totalDistance) > 0.1) {
            return false;
        }
        
        // Check altitude constraints are consistent
        if (minimumObstructionClearanceAltitude != null && 
            minimumEnrouteAltitude != null &&
            minimumObstructionClearanceAltitude > minimumEnrouteAltitude) {
            return false;
        }
        
        return true;
    }

    /**
     * Get appropriate cruise altitude range for this airway.
     */
    public AltitudeRange getValidAltitudeRange() {
        return new AltitudeRange(
            minimumEnrouteAltitude != null ? minimumEnrouteAltitude : 1000,
            maximumAuthorizedAltitude != null ? maximumAuthorizedAltitude : 45000
        );
    }
}
