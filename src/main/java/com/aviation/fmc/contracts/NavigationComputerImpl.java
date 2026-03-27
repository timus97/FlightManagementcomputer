package com.aviation.fmc.contracts;

import com.aviation.fmc.common.GeoCoordinate;
import com.aviation.fmc.common.MagneticVariation;
import com.aviation.fmc.navdata.HoldingPattern;
import com.aviation.fmc.navdata.Waypoint;
import com.aviation.fmc.parser.FlightPlan;
import com.aviation.fmc.parser.RouteElement;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of the Navigation Computer.
 * Handles position determination, waypoint sequencing, and navigation calculations.
 */
public class NavigationComputerImpl implements NavigationComputer {

    private static final double EARTH_RADIUS_NM = 3440.065;
    private static final double MAX_XTK_DEVIATION = 5.0; // Max cross-track for warning

    private Position currentPosition;
    private FlightPlan activeFlightPlan;
    private int activeRouteIndex = 0;
    private final List<Waypoint> passedWaypoints = new ArrayList<>();
    private final LinkedList<Waypoint> upcomingWaypoints = new LinkedList<>();

    // Holding pattern state
    private boolean inHolding = false;
    private HoldingPattern activeHoldingPattern;
    private int holdingEntryCount = 0;

    // Direct-to state
    private Waypoint directToWaypoint;
    private boolean directToActive = false;

    // Magnetic variation at current position
    private MagneticVariation magneticVariation;

    @Override
    public void updatePosition(GeoCoordinate position, double altitude, double heading, double groundspeed) {
        this.currentPosition = new Position(
            position,
            altitude,
            heading,
            groundspeed,
            System.currentTimeMillis()
        );

        // Check if we've passed the active waypoint
        if (!inHolding && !directToActive && activeFlightPlan != null) {
            checkWaypointSequencing();
        }
    }

    @Override
    public Optional<Position> getCurrentPosition() {
        return Optional.ofNullable(currentPosition);
    }

    @Override
    public Optional<Waypoint> getActiveWaypoint() {
        if (directToActive && directToWaypoint != null) {
            return Optional.of(directToWaypoint);
        }
        if (upcomingWaypoints.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(upcomingWaypoints.getFirst());
    }

    @Override
    public Optional<Waypoint> getNextWaypoint() {
        if (upcomingWaypoints.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(upcomingWaypoints.get(1));
    }

    @Override
    public boolean sequenceWaypoint() {
        if (upcomingWaypoints.isEmpty()) {
            return false;
        }

        Waypoint passed = upcomingWaypoints.removeFirst();
        passedWaypoints.add(passed);
        activeRouteIndex++;

        // Clear direct-to if we reached the direct-to waypoint
        if (directToActive && passed.equals(directToWaypoint)) {
            directToActive = false;
            directToWaypoint = null;
        }

        return !upcomingWaypoints.isEmpty();
    }

    @Override
    public double distanceToActiveWaypoint() {
        if (currentPosition == null) {
            return Double.MAX_VALUE;
        }

        Optional<Waypoint> active = getActiveWaypoint();
        if (active.isEmpty()) {
            return Double.MAX_VALUE;
        }

        return currentPosition.coordinate().distanceTo(active.get().getCoordinate());
    }

    @Override
    public double bearingToActiveWaypoint() {
        if (currentPosition == null) {
            return 0.0;
        }

        Optional<Waypoint> active = getActiveWaypoint();
        if (active.isEmpty()) {
            return 0.0;
        }

        double trueBearing = currentPosition.coordinate().bearingTo(active.get().getCoordinate());
        
        // Convert to magnetic if variation available
        if (magneticVariation != null) {
            return magneticVariation.trueToMagnetic(trueBearing, java.time.Year.now());
        }
        
        return trueBearing;
    }

    @Override
    public double getCrossTrackError() {
        if (currentPosition == null || upcomingWaypoints.isEmpty()) {
            return 0.0;
        }

        if (upcomingWaypoints.size() < 2 && passedWaypoints.isEmpty()) {
            // Only one waypoint, can't calculate cross-track
            return 0.0;
        }

        Waypoint from;
        Waypoint to;
        
        if (passedWaypoints.isEmpty()) {
            // No passed waypoints - track is from first to second upcoming
            from = upcomingWaypoints.get(0);
            to = upcomingWaypoints.get(1);
        } else {
            // Has passed waypoints - track is from last passed to first upcoming
            from = passedWaypoints.get(passedWaypoints.size() - 1);
            to = upcomingWaypoints.getFirst();
        }

        return calculateCrossTrackDistance(
            currentPosition.coordinate(),
            from.getCoordinate(),
            to.getCoordinate()
        );
    }

    @Override
    public double getRequiredTrack() {
        if (upcomingWaypoints.isEmpty()) {
            return 0.0;
        }

        if (upcomingWaypoints.size() < 2 && passedWaypoints.isEmpty()) {
            // Only one waypoint in the route, no track defined
            return 0.0;
        }

        Waypoint from;
        Waypoint to;
        
        if (passedWaypoints.isEmpty()) {
            // No passed waypoints - track is from first to second upcoming
            from = upcomingWaypoints.get(0);
            to = upcomingWaypoints.get(1);
        } else {
            // Has passed waypoints - track is from last passed to first upcoming
            from = passedWaypoints.get(passedWaypoints.size() - 1);
            to = upcomingWaypoints.getFirst();
        }

        double trueTrack = from.getCoordinate().bearingTo(to.getCoordinate());
        
        if (magneticVariation != null) {
            return magneticVariation.trueToMagnetic(trueTrack, java.time.Year.now());
        }
        
        return trueTrack;
    }

    @Override
    public double estimateTimeToWaypoint() {
        double distance = distanceToActiveWaypoint();
        double speed = currentPosition != null ? currentPosition.groundspeed() : 250;
        
        if (speed <= 0) {
            return Double.MAX_VALUE;
        }
        
        return (distance / speed) * 60.0; // minutes
    }

    @Override
    public double getRemainingDistance() {
        if (currentPosition == null || upcomingWaypoints.isEmpty()) {
            return 0.0;
        }

        double totalDistance = distanceToActiveWaypoint();
        
        // Add distances between remaining waypoints
        for (int i = 0; i < upcomingWaypoints.size() - 1; i++) {
            totalDistance += upcomingWaypoints.get(i).distanceTo(upcomingWaypoints.get(i + 1));
        }
        
        return totalDistance;
    }

    @Override
    public boolean isInHolding() {
        return inHolding;
    }

    @Override
    public boolean enterHolding(String fixIdentifier) {
        if (fixIdentifier == null && !upcomingWaypoints.isEmpty()) {
            // Hold at current position/next waypoint
            inHolding = true;
            holdingEntryCount = 0;
            return true;
        }

        // Find waypoint with given identifier
        Optional<Waypoint> fix = upcomingWaypoints.stream()
            .filter(w -> w.getIdentifier().equals(fixIdentifier))
            .findFirst();

        if (fix.isPresent()) {
            inHolding = true;
            holdingEntryCount = 0;
            return true;
        }

        return false;
    }

    @Override
    public boolean exitHolding() {
        if (!inHolding) {
            return false;
        }
        inHolding = false;
        activeHoldingPattern = null;
        holdingEntryCount = 0;
        return true;
    }

    @Override
    public boolean directTo(String waypointIdentifier) {
        if (activeFlightPlan == null) {
            return false;
        }

        // Find waypoint in flight plan
        Optional<Waypoint> target = upcomingWaypoints.stream()
            .filter(w -> w.getIdentifier().equals(waypointIdentifier))
            .findFirst();

        if (target.isPresent()) {
            directToWaypoint = target.get();
            directToActive = true;
            
            // Remove waypoints before the direct-to waypoint
            while (!upcomingWaypoints.isEmpty() && !upcomingWaypoints.getFirst().equals(target.get())) {
                passedWaypoints.add(upcomingWaypoints.removeFirst());
            }
            
            return true;
        }

        return false;
    }

    @Override
    public boolean interceptCourseTo(String waypointIdentifier, double course) {
        // Similar to direct-to but with specific inbound course
        return directTo(waypointIdentifier);
    }

    @Override
    public List<Waypoint> getPassedWaypoints() {
        return new ArrayList<>(passedWaypoints);
    }

    @Override
    public List<Waypoint> getUpcomingWaypoints() {
        return new ArrayList<>(upcomingWaypoints);
    }

    // ==================== Helper Methods ====================

    /**
     * Load flight plan into navigation computer.
     */
    public void loadFlightPlan(FlightPlan flightPlan) {
        this.activeFlightPlan = flightPlan;
        this.passedWaypoints.clear();
        this.upcomingWaypoints.clear();
        this.activeRouteIndex = 0;
        this.directToActive = false;
        this.directToWaypoint = null;
        this.inHolding = false;

        // Extract waypoints from route
        if (flightPlan.getRoute() != null) {
            for (RouteElement element : flightPlan.getRoute()) {
                if (element.getWaypoint() != null) {
                    upcomingWaypoints.add(element.getWaypoint());
                }
            }
        }

        // Set magnetic variation from departure airport if available
        if (flightPlan.getDeparture() != null && flightPlan.getDeparture().getMagneticVariation() != null) {
            this.magneticVariation = flightPlan.getDeparture().getMagneticVariation();
        }
    }

    /**
     * Set magnetic variation for navigation calculations.
     */
    public void setMagneticVariation(MagneticVariation variation) {
        this.magneticVariation = variation;
    }

    /**
     * Check if we should sequence to next waypoint.
     */
    private void checkWaypointSequencing() {
        double distance = distanceToActiveWaypoint();
        
        // Simple fly-by logic: sequence when within 1 NM or passed abeam
        if (distance < 1.0 || isAbeamWaypoint()) {
            sequenceWaypoint();
        }
    }

    /**
     * Check if aircraft is abeam the active waypoint.
     */
    private boolean isAbeamWaypoint() {
        if (upcomingWaypoints.size() < 2 || currentPosition == null) {
            return false;
        }

        Waypoint current = upcomingWaypoints.getFirst();
        Waypoint next = upcomingWaypoints.get(1);
        
        double bearingToCurrent = currentPosition.coordinate().bearingTo(current.getCoordinate());
        double trackToNext = current.getCoordinate().bearingTo(next.getCoordinate());
        
        double angleDiff = Math.abs(bearingToCurrent - trackToNext);
        if (angleDiff > 180) {
            angleDiff = 360 - angleDiff;
        }
        
        // If angle is > 90 degrees, we've passed the waypoint
        return angleDiff > 90;
    }

    /**
     * Calculate cross-track distance from current position to track line.
     * @return Distance in NM (positive = right of course, negative = left)
     */
    private double calculateCrossTrackDistance(GeoCoordinate position, GeoCoordinate from, GeoCoordinate to) {
        double trackDistance = from.distanceTo(to);
        double distanceFromStart = from.distanceTo(position);
        
        // Calculate bearing from start to aircraft and start to end
        double bearingToAircraft = from.bearingTo(position);
        double trackBearing = from.bearingTo(to);
        
        // Calculate angular difference
        double angleDiff = bearingToAircraft - trackBearing;
        
        // Convert to radians for trigonometry
        double angleRad = Math.toRadians(angleDiff);
        
        // Cross-track distance = distance from start * sin(angle difference)
        double xtk = distanceFromStart * Math.sin(angleRad);
        
        return xtk;
    }

    /**
     * Get navigation status.
     */
    public NavigationStatus getNavigationStatus() {
        return new NavigationStatus(
            currentPosition != null,
            !upcomingWaypoints.isEmpty(),
            currentPosition != null ? "GPS/DME" : "NONE",
            calculatePositionAccuracy(),
            8 // Simulated satellite count
        );
    }

    private double calculatePositionAccuracy() {
        // Simplified accuracy calculation
        if (currentPosition == null) {
            return Double.MAX_VALUE;
        }
        return 0.1; // 0.1 NM accuracy (typical for GPS)
    }
}
