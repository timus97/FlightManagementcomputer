package com.aviation.fmc.contracts;

import com.aviation.fmc.common.GeoCoordinate;
import com.aviation.fmc.navdata.Waypoint;

import java.util.List;
import java.util.Optional;

/**
 * Contract for the FMC Navigation Computer (FMC NAV).
 * Handles position determination, waypoint sequencing, and navigation calculations.
 */
public interface NavigationComputer {

    /**
     * Update current aircraft position.
     * 
     * @param position Current WGS-84 coordinates
     * @param altitude Current altitude in feet
     * @param heading Current true heading in degrees
     * @param groundspeed Current groundspeed in knots
     */
    void updatePosition(GeoCoordinate position, double altitude, double heading, double groundspeed);

    /**
     * Get current aircraft position.
     * 
     * @return Current position or empty if not available
     */
    Optional<Position> getCurrentPosition();

    /**
     * Get the active (to) waypoint.
     * 
     * @return Active waypoint
     */
    Optional<Waypoint> getActiveWaypoint();

    /**
     * Get the next waypoint after the active one.
     * 
     * @return Next waypoint
     */
    Optional<Waypoint> getNextWaypoint();

    /**
     * Sequence to the next waypoint.
     * Called when passing the active waypoint.
     * 
     * @return true if sequencing was successful
     */
    boolean sequenceWaypoint();

    /**
     * Get distance to active waypoint.
     * 
     * @return Distance in nautical miles
     */
    double distanceToActiveWaypoint();

    /**
     * Get bearing to active waypoint.
     * 
     * @return Magnetic bearing in degrees
     */
    double bearingToActiveWaypoint();

    /**
     * Get cross-track error from current leg.
     * 
     * @return Cross-track error in nautical miles (positive = right of course)
     */
    double getCrossTrackError();

    /**
     * Get required track for current leg.
     * 
     * @return Magnetic track in degrees
     */
    double getRequiredTrack();

    /**
     * Estimate time to active waypoint.
     * 
     * @return ETE in minutes
     */
    double estimateTimeToWaypoint();

    /**
     * Get remaining route distance.
     * 
     * @return Distance in nautical miles
     */
    double getRemainingDistance();

    /**
     * Check if aircraft is in holding pattern.
     * 
     * @return true if in holding
     */
    boolean isInHolding();

    /**
     * Enter holding pattern at current position or specified fix.
     * 
     * @param fixIdentifier Fix to hold at (null for current position)
     * @return true if holding entry was successful
     */
    boolean enterHolding(String fixIdentifier);

    /**
     * Exit holding pattern and resume route.
     * 
     * @return true if exit was successful
     */
    boolean exitHolding();

    /**
     * Direct to a specified waypoint.
     * Creates a direct route from current position to the waypoint.
     * 
     * @param waypointIdentifier Target waypoint
     * @return true if direct was successful
     */
    boolean directTo(String waypointIdentifier);

    /**
     * Intercept a course to a waypoint.
     * 
     * @param waypointIdentifier Target waypoint
     * @param course Desired inbound course
     * @return true if intercept was set
     */
    boolean interceptCourseTo(String waypointIdentifier, double course);

    /**
     * Get list of passed waypoints.
     * 
     * @return List of passed waypoints
     */
    List<Waypoint> getPassedWaypoints();

    /**
     * Get list of upcoming waypoints.
     * 
     * @return List of upcoming waypoints
     */
    List<Waypoint> getUpcomingWaypoints();

    /**
     * Aircraft position record.
     */
    record Position(
        GeoCoordinate coordinate,
        double altitude,
        double heading,
        double groundspeed,
        long timestamp
    ) {}

    /**
     * Navigation status.
     */
    record NavigationStatus(
        boolean positionValid,
        boolean waypointActive,
        String navSource,  // GPS, DME/DME, VOR/DME, etc.
        double accuracy,
        int satellites
    ) {}
}
