package com.aviation.fmc.simulation;

import com.aviation.fmc.common.GeoCoordinate;
import com.aviation.fmc.contracts.FmcSystem.FlightPhase;

/**
 * Pure physics engine for aircraft state propagation.
 * 
 * This class contains NO state - all methods are pure functions.
 * Same inputs always produce same outputs (deterministic).
 * 
 * Assumptions:
 * - Earth is modeled as a sphere with radius 3440.065 NM
 * - Standard atmosphere model for TAS calculations
 * - Simplified turn dynamics (coordinated turns)
 * - No wind model (can be added via WindModel interface)
 * - Fuel flow is proportional to altitude and speed
 * - Bank angle limits: max 30 degrees for airliners
 * - Turn rate is function of bank angle and speed
 */
public class PhysicsEngine {
    
    private static final double EARTH_RADIUS_NM = 3440.065;
    private static final double NM_TO_FEET = 6076.12;
    private static final double MAX_BANK_ANGLE = 30.0;
    private static final double STANDARD_TURN_RATE = 3.0; // degrees/second
    
    /** Steps between coordinate renormalization to prevent drift */
    private static final int RENORMALIZE_INTERVAL = 1000;
    
    private int stepCount = 0;
    
    /**
     * Propagate aircraft state forward by delta time.
     * Pure function - deterministic given same inputs.
     * 
     * @param state Current aircraft state
     * @param targets Target values from autopilot
     * @param deltaSeconds Time step in seconds
     * @return New aircraft state after propagation
     */
    public AircraftState propagate(AircraftState state, AutopilotTargets targets, 
                                   double deltaSeconds) {
        stepCount++;
        
        // 1. Calculate new track (turning)
        double newTrack = propagateTrack(state.getTrack(), targets.targetTrack(),
                                         state.getGroundSpeed(), deltaSeconds);
        
        // 2. Calculate new position
        GeoCoordinate newPosition = propagatePosition(
            state.getPosition(),
            state.getGroundSpeed(),
            newTrack,
            deltaSeconds
        );
        
        // Periodic renormalization to prevent floating-point drift
        if (stepCount % RENORMALIZE_INTERVAL == 0) {
            newPosition = renormalizeCoordinate(newPosition);
        }
        
        // 3. Calculate new altitude
        double newAltitude = propagateAltitude(
            state.getAltitude(),
            targets.targetVerticalSpeed(),
            targets.targetAltitude(),
            targets.altitudeCapture(),
            deltaSeconds
        );
        
        // 4. Calculate new speed
        double newGroundSpeed = propagateSpeed(
            state.getGroundSpeed(),
            targets.targetGroundSpeed(),
            newAltitude,
            deltaSeconds
        );
        
        // 5. Calculate true airspeed from groundspeed and altitude
        double newTrueAirspeed = calculateTrueAirspeed(newGroundSpeed, newAltitude);
        
        // 6. Calculate fuel consumption
        double newFuel = propagateFuel(state.getFuelOnBoard(), newAltitude, 
                                       newTrueAirspeed, deltaSeconds);
        
        // 7. Calculate attitude
        double newBank = calculateBankAngle(state.getTrack(), targets.targetTrack());
        double newPitch = calculatePitchAngle(targets.targetVerticalSpeed());
        
        // 8. Determine flight phase based on altitude and VS
        FlightPhase newPhase = determineFlightPhase(newAltitude, 
            targets.targetVerticalSpeed(), state.getPhase());
        
        return AircraftState.builder()
            .position(newPosition)
            .altitude(newAltitude)
            .groundSpeed(newGroundSpeed)
            .trueAirspeed(newTrueAirspeed)
            .heading(newTrack) // Simplified: heading = track (no wind)
            .track(newTrack)
            .verticalSpeed(targets.targetVerticalSpeed())
            .pitch(newPitch)
            .bank(newBank)
            .fuelOnBoard(newFuel)
            .grossWeight(state.getGrossWeight() - (state.getFuelOnBoard() - newFuel))
            .phase(newPhase)
            .simulationTimeNanos(state.getSimulationTimeNanos() + 
                (long)(deltaSeconds * 1_000_000_000L))
            .onGround(newAltitude < 50)
            .build();
    }
    
    /**
     * Propagate position using great circle navigation.
     */
    public GeoCoordinate propagatePosition(GeoCoordinate position, double speedKnots,
                                           double trackDegrees, double deltaSeconds) {
        // Distance traveled in nautical miles
        double distanceNm = speedKnots * (deltaSeconds / 3600.0);
        
        if (distanceNm < 1e-9) {
            return position; // No movement
        }
        
        double lat1 = Math.toRadians(position.getLatitude());
        double lon1 = Math.toRadians(position.getLongitude());
        double track = Math.toRadians(trackDegrees);
        double d = distanceNm / EARTH_RADIUS_NM;
        
        double lat2 = Math.asin(
            Math.sin(lat1) * Math.cos(d) +
            Math.cos(lat1) * Math.sin(d) * Math.cos(track)
        );
        
        double lon2 = lon1 + Math.atan2(
            Math.sin(track) * Math.sin(d) * Math.cos(lat1),
            Math.cos(d) - Math.sin(lat1) * Math.sin(lat2)
        );
        
        return GeoCoordinate.builder()
            .latitude(Math.toDegrees(lat2))
            .longitude(Math.toDegrees(lon2))
            .elevationFt(position.getElevationFt())
            .build();
    }
    
    /**
     * Propagate track towards target track (turning).
     */
    public double propagateTrack(double currentTrack, double targetTrack,
                                 double speedKnots, double deltaSeconds) {
        // Calculate turn rate based on speed
        // Standard rate turn = 3 deg/sec at typical speeds
        // Turn rate = (g * tan(bank)) / speed
        // For simplicity, use standard rate turn limited by speed
        double turnRate = Math.min(STANDARD_TURN_RATE, 
            (1091.0 * Math.tan(Math.toRadians(MAX_BANK_ANGLE))) / 
            Math.max(speedKnots, 100.0));
        
        double maxTurn = turnRate * deltaSeconds;
        
        // Calculate shortest angular distance (signed)
        double diff = shortestAngularDistance(currentTrack, targetTrack);
        
        // Limit turn to max rate
        double turn = Math.copySign(Math.min(Math.abs(diff), maxTurn), diff);
        
        return normalizeAngle(currentTrack + turn);
    }
    
    /**
     * Calculate the shortest angular distance from one angle to another.
     * Returns a signed value in [-180, 180) degrees.
     * Positive means turn right, negative means turn left.
     */
    public double shortestAngularDistance(double fromAngle, double toAngle) {
        double diff = toAngle - fromAngle;
        
        // Normalize to [-180, 180)
        while (diff >= 180.0) diff -= 360.0;
        while (diff < -180.0) diff += 360.0;
        
        return diff;
    }
    
    /**
     * Propagate altitude with vertical speed.
     */
    public double propagateAltitude(double currentAltitude, double verticalSpeed,
                                    double targetAltitude, boolean altitudeCapture,
                                    double deltaSeconds) {
        if (altitudeCapture && Math.abs(currentAltitude - targetAltitude) < 10) {
            return targetAltitude; // Captured
        }
        
        // Altitude change = VS * time (VS in fpm, time in seconds)
        double altChange = verticalSpeed * (deltaSeconds / 60.0);
        double newAltitude = currentAltitude + altChange;
        
        // Don't overshoot target
        if (altitudeCapture) {
            if (verticalSpeed > 0 && newAltitude > targetAltitude) {
                return targetAltitude;
            } else if (verticalSpeed < 0 && newAltitude < targetAltitude) {
                return targetAltitude;
            }
        }
        
        // Clamp to valid range
        return Math.max(0, Math.min(60000, newAltitude));
    }
    
    /**
     * Propagate speed towards target.
     */
    public double propagateSpeed(double currentSpeed, double targetSpeed,
                                 double altitude, double deltaSeconds) {
        // Typical acceleration/deceleration rates
        double accelRate = 2.0; // knots per second
        
        double diff = targetSpeed - currentSpeed;
        double maxChange = accelRate * deltaSeconds;
        double change = Math.copySign(Math.min(Math.abs(diff), maxChange), diff);
        
        double newSpeed = currentSpeed + change;
        
        // Clamp to valid range based on altitude
        double minSpeed = 100; // Stall speed approximation
        double maxSpeed = 500; // VMO approximation
        
        return Math.max(minSpeed, Math.min(maxSpeed, newSpeed));
    }
    
    /**
     * Calculate true airspeed from groundspeed and altitude.
     * Uses standard atmosphere density ratio.
     */
    public double calculateTrueAirspeed(double groundSpeedKnots, double altitudeFt) {
        // Simplified: TAS increases ~2% per 1000ft above sea level
        // TAS = GS * (1 + altitude/50000) for no-wind condition
        double densityRatio = 1.0 + (altitudeFt / 50000.0);
        return groundSpeedKnots * densityRatio;
    }
    
    /**
     * Calculate fuel consumption.
     */
    public double propagateFuel(double currentFuel, double altitude,
                                double trueAirspeed, double deltaSeconds) {
        // Simplified fuel flow model
        // Base flow ~3000 kg/hr for B738 at cruise
        // Varies with altitude and speed
        
        double baseFlow = 3000.0; // kg/hr at cruise
        double altitudeFactor = 0.8 + (altitude / 50000.0) * 0.4;
        double speedFactor = trueAirspeed / 450.0;
        
        double fuelFlow = baseFlow * altitudeFactor * speedFactor;
        double fuelConsumed = fuelFlow * (deltaSeconds / 3600.0);
        
        return Math.max(0, currentFuel - fuelConsumed);
    }
    
    /**
     * Calculate required bank angle for turn.
     */
    public double calculateBankAngle(double currentTrack, double targetTrack) {
        double diff = Math.abs(normalizeAngle(targetTrack - currentTrack));
        
        if (diff < 1.0) {
            return 0.0; // No turn needed
        }
        
        // Bank proportional to track error, max 30 degrees
        return Math.copySign(Math.min(diff / 3.0, MAX_BANK_ANGLE), 
            normalizeAngle(targetTrack - currentTrack));
    }
    
    /**
     * Calculate pitch angle for vertical speed.
     */
    public double calculatePitchAngle(double verticalSpeed) {
        // Pitch ~ VS / (speed * 100) for typical airliner
        // Simplified: ~1 degree per 1000 fpm
        return verticalSpeed / 1000.0;
    }
    
    /**
     * Determine flight phase based on altitude and vertical speed.
     */
    public FlightPhase determineFlightPhase(double altitude, double verticalSpeed,
                                            FlightPhase currentPhase) {
        // Phase transitions based on altitude
        if (altitude < 50) {
            return FlightPhase.LANDING;
        } else if (altitude < 1000) {
            if (verticalSpeed < -500) {
                return FlightPhase.LANDING;
            } else if (verticalSpeed > 500) {
                return FlightPhase.TAKEOFF;
            }
            return FlightPhase.TAXI;
        } else if (altitude < 10000) {
            if (verticalSpeed > 500) {
                return FlightPhase.CLIMB;
            } else if (verticalSpeed < -500) {
                return FlightPhase.APPROACH;
            }
            return currentPhase;
        } else if (altitude < 18000) {
            if (verticalSpeed > 500) {
                return FlightPhase.CLIMB;
            } else if (verticalSpeed < -500) {
                return FlightPhase.DESCENT;
            }
            return currentPhase;
        } else if (altitude < 28000) {
            if (verticalSpeed > 500) {
                return FlightPhase.CLIMB;
            } else if (verticalSpeed < -500) {
                return FlightPhase.DESCENT;
            }
            return FlightPhase.CRUISE;
        } else {
            if (verticalSpeed > 500) {
                return FlightPhase.CLIMB;
            } else if (verticalSpeed < -500) {
                return FlightPhase.DESCENT;
            }
            return FlightPhase.CRUISE;
        }
    }
    
    /**
     * Normalize angle to [0, 360) range.
     */
    public double normalizeAngle(double angle) {
        angle = angle % 360.0;
        if (angle < 0) {
            angle += 360.0;
        }
        return angle;
    }
    
    /**
     * Renormalize coordinate to prevent floating-point drift.
     */
    public GeoCoordinate renormalizeCoordinate(GeoCoordinate coord) {
        double lat = Math.max(-90.0, Math.min(90.0, coord.getLatitude()));
        double lon = coord.getLongitude();
        
        // Wrap longitude to [-180, 180)
        while (lon > 180.0) lon -= 360.0;
        while (lon < -180.0) lon += 360.0;
        
        return GeoCoordinate.builder()
            .latitude(lat)
            .longitude(lon)
            .elevationFt(coord.getElevationFt())
            .build();
    }
    
    /**
     * Reset step counter (for testing).
     */
    public void reset() {
        stepCount = 0;
    }
}
