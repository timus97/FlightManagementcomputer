package com.aviation.fmc.simulation;

import com.aviation.fmc.common.GeoCoordinate;
import com.aviation.fmc.contracts.FmcSystem.FlightPhase;
import lombok.Builder;
import lombok.Value;
import lombok.With;

/**
 * Immutable aircraft state for simulation.
 * 
 * Assumptions:
 * - All positions use WGS-84 coordinates
 * - Altitude is feet MSL (Mean Sea Level)
 * - Speeds are in knots
 * - Angles are in degrees
 * - Vertical speed is feet per minute (positive = climb)
 * - Fuel is in kilograms
 * - State is immutable - use withXxx() methods to create modified copies
 */
@Value
@Builder
public class AircraftState {
    
    /** Current WGS-84 position */
    GeoCoordinate position;
    
    /** Altitude in feet MSL */
    double altitude;
    
    /** Ground speed in knots */
    double groundSpeed;
    
    /** True airspeed in knots */
    double trueAirspeed;
    
    /** True heading in degrees (0-360) */
    double heading;
    
    /** True track in degrees (0-360) */
    double track;
    
    /** Vertical speed in feet per minute (positive = climb) */
    double verticalSpeed;
    
    /** Pitch angle in degrees (positive = nose up) */
    double pitch;
    
    /** Bank angle in degrees (positive = right wing down) */
    double bank;
    
    /** Fuel on board in kilograms */
    double fuelOnBoard;
    
    /** Gross weight in kilograms */
    double grossWeight;
    
    /** Current flight phase */
    @Builder.Default
    FlightPhase phase = FlightPhase.PREFLIGHT;
    
    /** Simulation time in nanoseconds when this state was captured */
    long simulationTimeNanos;
    
    /** True if aircraft is on the ground */
    @Builder.Default
    boolean onGround = true;
    
    /**
     * Create a copy with a new position.
     */
    public AircraftState withPosition(GeoCoordinate newPosition) {
        return AircraftState.builder()
            .position(newPosition)
            .altitude(this.altitude)
            .groundSpeed(this.groundSpeed)
            .trueAirspeed(this.trueAirspeed)
            .heading(this.heading)
            .track(this.track)
            .verticalSpeed(this.verticalSpeed)
            .pitch(this.pitch)
            .bank(this.bank)
            .fuelOnBoard(this.fuelOnBoard)
            .grossWeight(this.grossWeight)
            .phase(this.phase)
            .simulationTimeNanos(this.simulationTimeNanos)
            .onGround(this.onGround)
            .build();
    }
    
    /**
     * Create a copy with new altitude and vertical speed.
     */
    public AircraftState withAltitude(double newAltitude, double newVerticalSpeed) {
        return AircraftState.builder()
            .position(this.position)
            .altitude(newAltitude)
            .groundSpeed(this.groundSpeed)
            .trueAirspeed(this.trueAirspeed)
            .heading(this.heading)
            .track(this.track)
            .verticalSpeed(newVerticalSpeed)
            .pitch(this.pitch)
            .bank(this.bank)
            .fuelOnBoard(this.fuelOnBoard)
            .grossWeight(this.grossWeight)
            .phase(this.phase)
            .simulationTimeNanos(this.simulationTimeNanos)
            .onGround(newAltitude < 50)
            .build();
    }
    
    /**
     * Create a copy with new track and heading.
     */
    public AircraftState withHeadingTrack(double newHeading, double newTrack) {
        return AircraftState.builder()
            .position(this.position)
            .altitude(this.altitude)
            .groundSpeed(this.groundSpeed)
            .trueAirspeed(this.trueAirspeed)
            .heading(newHeading)
            .track(newTrack)
            .verticalSpeed(this.verticalSpeed)
            .pitch(this.pitch)
            .bank(this.bank)
            .fuelOnBoard(this.fuelOnBoard)
            .grossWeight(this.grossWeight)
            .phase(this.phase)
            .simulationTimeNanos(this.simulationTimeNanos)
            .onGround(this.onGround)
            .build();
    }
    
    /**
     * Create a copy with new speeds.
     */
    public AircraftState withSpeeds(double newGroundSpeed, double newTrueAirspeed) {
        return AircraftState.builder()
            .position(this.position)
            .altitude(this.altitude)
            .groundSpeed(newGroundSpeed)
            .trueAirspeed(newTrueAirspeed)
            .heading(this.heading)
            .track(this.track)
            .verticalSpeed(this.verticalSpeed)
            .pitch(this.pitch)
            .bank(this.bank)
            .fuelOnBoard(this.fuelOnBoard)
            .grossWeight(this.grossWeight)
            .phase(this.phase)
            .simulationTimeNanos(this.simulationTimeNanos)
            .onGround(this.onGround)
            .build();
    }
    
    /**
     * Create a copy with updated fuel and weight.
     */
    public AircraftState withFuel(double newFuelOnBoard) {
        double weightChange = this.fuelOnBoard - newFuelOnBoard;
        return AircraftState.builder()
            .position(this.position)
            .altitude(this.altitude)
            .groundSpeed(this.groundSpeed)
            .trueAirspeed(this.trueAirspeed)
            .heading(this.heading)
            .track(this.track)
            .verticalSpeed(this.verticalSpeed)
            .pitch(this.pitch)
            .bank(this.bank)
            .fuelOnBoard(newFuelOnBoard)
            .grossWeight(this.grossWeight - weightChange)
            .phase(this.phase)
            .simulationTimeNanos(this.simulationTimeNanos)
            .onGround(this.onGround)
            .build();
    }
    
    /**
     * Create a copy with new phase.
     */
    public AircraftState withPhase(FlightPhase newPhase) {
        return AircraftState.builder()
            .position(this.position)
            .altitude(this.altitude)
            .groundSpeed(this.groundSpeed)
            .trueAirspeed(this.trueAirspeed)
            .heading(this.heading)
            .track(this.track)
            .verticalSpeed(this.verticalSpeed)
            .pitch(this.pitch)
            .bank(this.bank)
            .fuelOnBoard(this.fuelOnBoard)
            .grossWeight(this.grossWeight)
            .phase(newPhase)
            .simulationTimeNanos(this.simulationTimeNanos)
            .onGround(this.onGround)
            .build();
    }
    
    /**
     * Create a copy with new simulation time.
     */
    public AircraftState withSimulationTimeNanos(long newTimeNanos) {
        return AircraftState.builder()
            .position(this.position)
            .altitude(this.altitude)
            .groundSpeed(this.groundSpeed)
            .trueAirspeed(this.trueAirspeed)
            .heading(this.heading)
            .track(this.track)
            .verticalSpeed(this.verticalSpeed)
            .pitch(this.pitch)
            .bank(this.bank)
            .fuelOnBoard(this.fuelOnBoard)
            .grossWeight(this.grossWeight)
            .phase(this.phase)
            .simulationTimeNanos(newTimeNanos)
            .onGround(this.onGround)
            .build();
    }
    
    /**
     * Create a copy with new attitude.
     */
    public AircraftState withAttitude(double newPitch, double newBank) {
        return AircraftState.builder()
            .position(this.position)
            .altitude(this.altitude)
            .groundSpeed(this.groundSpeed)
            .trueAirspeed(this.trueAirspeed)
            .heading(this.heading)
            .track(this.track)
            .verticalSpeed(this.verticalSpeed)
            .pitch(newPitch)
            .bank(newBank)
            .fuelOnBoard(this.fuelOnBoard)
            .grossWeight(this.grossWeight)
            .phase(this.phase)
            .simulationTimeNanos(this.simulationTimeNanos)
            .onGround(this.onGround)
            .build();
    }
}
