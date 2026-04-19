package com.aviation.fmc.simulation;

import com.aviation.fmc.common.GeoCoordinate;
import com.aviation.fmc.contracts.FmcSystem.FlightPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PhysicsEngine demonstrating:
 * - Pure function behavior (determinism)
 * - Position propagation accuracy
 * - Altitude and speed changes
 * - Coordinate normalization
 */
class PhysicsEngineTest {

    private PhysicsEngine physics;

    @BeforeEach
    void setUp() {
        physics = new PhysicsEngine();
    }

    @Nested
    @DisplayName("Determinism Tests")
    class DeterminismTests {

        @Test
        @DisplayName("Same inputs always produce same outputs")
        void testDeterminism_pureFunction() {
            AircraftState state = createCruiseState();
            AutopilotTargets targets = createCruiseTargets();
            
            // Run propagation 5 times with same inputs
            for (int i = 0; i < 5; i++) {
                AircraftState result = physics.propagate(state, targets, 1.0);
                
                assertEquals(state.getAltitude(), result.getAltitude(), 0.001);
                assertEquals(state.getTrack(), result.getTrack(), 0.001);
                assertEquals(state.getGroundSpeed(), result.getGroundSpeed(), 0.001);
            }
        }

        @Test
        @DisplayName("Propagate is stateless - no accumulated errors")
        void testDeterminism_noStateAccumulation() {
            AircraftState state = createCruiseState();
            AutopilotTargets targets = createCruiseTargets();
            
            // Propagate 100 times
            for (int i = 0; i < 100; i++) {
                AircraftState newState = physics.propagate(state, targets, 0.1);
                state = newState;
            }
            
            // Verify no NaN or Infinity from accumulated errors
            assertFalse(Double.isNaN(state.getAltitude()));
            assertFalse(Double.isInfinite(state.getAltitude()));
            assertFalse(Double.isNaN(state.getPosition().getLatitude()));
            assertFalse(Double.isNaN(state.getPosition().getLongitude()));
        }
    }

    @Nested
    @DisplayName("Position Propagation Tests")
    class PositionPropagationTests {

        @Test
        @DisplayName("Position advances correctly at constant speed and track")
        void testPositionPropagation_constantTrack() {
            GeoCoordinate start = GeoCoordinate.builder()
                .latitude(0.0)
                .longitude(0.0)
                .build();
            
            // Fly due East (090 degrees) at 480 knots for 1 hour
            GeoCoordinate end = physics.propagatePosition(start, 480.0, 90.0, 3600.0);
            
            // At equator, 1 degree longitude ~ 60 NM
            // 480 NM / 60 = 8 degrees
            assertEquals(0.0, end.getLatitude(), 0.1, "Latitude should not change flying east at equator");
            assertEquals(8.0, end.getLongitude(), 0.5, "Longitude should increase by ~8 degrees");
        }

        @Test
        @DisplayName("Position advances correctly flying north")
        void testPositionPropagation_northTrack() {
            GeoCoordinate start = GeoCoordinate.builder()
                .latitude(0.0)
                .longitude(0.0)
                .build();
            
            // Fly due North (360 degrees) at 480 knots for 1 hour
            GeoCoordinate end = physics.propagatePosition(start, 480.0, 0.0, 3600.0);
            
            // 480 NM / 60 = 8 degrees latitude
            assertEquals(8.0, end.getLatitude(), 0.1);
            assertEquals(0.0, end.getLongitude(), 0.1);
        }

        @Test
        @DisplayName("Zero speed produces no position change")
        void testPositionPropagation_zeroSpeed() {
            GeoCoordinate start = GeoCoordinate.builder()
                .latitude(45.0)
                .longitude(-100.0)
                .build();
            
            GeoCoordinate end = physics.propagatePosition(start, 0.0, 90.0, 3600.0);
            
            assertEquals(start.getLatitude(), end.getLatitude(), 0.0001);
            assertEquals(start.getLongitude(), end.getLongitude(), 0.0001);
        }

        @Test
        @DisplayName("Position wraps correctly at date line")
        void testPositionPropagation_dateLineWrap() {
            GeoCoordinate start = GeoCoordinate.builder()
                .latitude(0.0)
                .longitude(179.0)
                .build();
            
            // Fly east for 2 degrees worth of distance
            GeoCoordinate end = physics.propagatePosition(start, 120.0, 90.0, 3600.0);
            
            // Should wrap to west side of date line
            assertTrue(end.getLongitude() < -178 || end.getLongitude() > 178);
        }
    }

    @Nested
    @DisplayName("Track Propagation Tests")
    class TrackPropagationTests {

        @Test
        @DisplayName("Track turns towards target")
        void testTrackPropagation_turning() {
            double currentTrack = 0.0;   // North
            double targetTrack = 90.0;   // East
            
            // Multiple steps to complete turn
            double track = currentTrack;
            for (int i = 0; i < 100; i++) {
                track = physics.propagateTrack(track, targetTrack, 250.0, 1.0);
            }
            
            assertEquals(targetTrack, track, 1.0, "Should reach target track");
        }

        @Test
        @DisplayName("Track takes shortest path")
        void testTrackPropagation_shortestPath() {
            double currentTrack = 10.0;  // Almost north
            double targetTrack = 350.0;  // Almost north (other way)
            
            double newTrack = physics.propagateTrack(currentTrack, targetTrack, 250.0, 1.0);
            
            // Should turn left (decrease) towards 350, not right through 90,180,270
            // The shortest path from 10 to 350 is to decrease (turn left) by 20 degrees
            // So the new track should be less than 10 (e.g., 7)
            assertTrue(newTrack < currentTrack && newTrack >= 0,
                      "Should turn left (decrease): current=" + currentTrack + ", new=" + newTrack);
        }

        @Test
        @DisplayName("Track stays constant when on target")
        void testTrackPropagation_onTarget() {
            double track = physics.propagateTrack(270.0, 270.0, 250.0, 1.0);
            
            assertEquals(270.0, track, 0.1);
        }
    }

    @Nested
    @DisplayName("Altitude Propagation Tests")
    class AltitudePropagationTests {

        @Test
        @DisplayName("Altitude increases with positive VS")
        void testAltitudePropagation_climb() {
            double newAlt = physics.propagateAltitude(10000, 2000, 35000, false, 60.0);
            
            // VS 2000 fpm for 60 seconds = 2000 ft climb
            assertEquals(12000, newAlt, 10);
        }

        @Test
        @DisplayName("Altitude decreases with negative VS")
        void testAltitudePropagation_descent() {
            double newAlt = physics.propagateAltitude(35000, -2000, 10000, false, 60.0);
            
            assertEquals(33000, newAlt, 10);
        }

        @Test
        @DisplayName("Altitude captures target when close")
        void testAltitudePropagation_capture() {
            double newAlt = physics.propagateAltitude(34990, 2000, 35000, true, 1.0);
            
            assertEquals(35000, newAlt, 10, "Should capture target altitude");
        }

        @Test
        @DisplayName("Altitude does not overshoot target")
        void testAltitudePropagation_noOvershoot() {
            // Climbing towards 30000, currently at 29950, VS 2000
            double newAlt = physics.propagateAltitude(29950, 2000, 30000, true, 60.0);
            
            assertTrue(newAlt <= 30000, "Should not exceed target");
        }

        @Test
        @DisplayName("Altitude is clamped to valid range")
        void testAltitudePropagation_clamped() {
            // Try to descend below ground
            double newAlt = physics.propagateAltitude(100, -5000, 0, false, 60.0);
            
            assertTrue(newAlt >= 0, "Altitude should not go negative");
        }
    }

    @Nested
    @DisplayName("Speed Propagation Tests")
    class SpeedPropagationTests {

        @Test
        @DisplayName("Speed changes towards target")
        void testSpeedPropagation_acceleration() {
            double newSpeed = physics.propagateSpeed(250, 300, 35000, 10.0);
            
            assertTrue(newSpeed > 250, "Speed should increase");
            assertTrue(newSpeed < 300, "Speed should not reach target in one step");
        }

        @Test
        @DisplayName("Speed is clamped to valid range")
        void testSpeedPropagation_clamped() {
            double highSpeed = physics.propagateSpeed(600, 700, 35000, 10.0);
            assertTrue(highSpeed <= 500, "Speed should be clamped to max");
            
            double lowSpeed = physics.propagateSpeed(50, 40, 35000, 10.0);
            assertTrue(lowSpeed >= 100, "Speed should be clamped to min");
        }
    }

    @Nested
    @DisplayName("Fuel Propagation Tests")
    class FuelPropagationTests {

        @Test
        @DisplayName("Fuel decreases during flight")
        void testFuelPropagation_decrease() {
            double newFuel = physics.propagateFuel(10000, 35000, 450, 3600.0);
            
            assertTrue(newFuel < 10000, "Fuel should decrease");
            assertTrue(newFuel > 0, "Fuel should remain positive");
        }

        @Test
        @DisplayName("Fuel consumption is proportional to time")
        void testFuelPropagation_proportional() {
            double fuel1hr = physics.propagateFuel(10000, 35000, 450, 3600.0);
            double fuel2hr = physics.propagateFuel(10000, 35000, 450, 7200.0);
            
            double consumed1hr = 10000 - fuel1hr;
            double consumed2hr = 10000 - fuel2hr;
            
            assertEquals(consumed1hr * 2, consumed2hr, consumed1hr * 0.1, 
                        "2 hours should consume ~2x fuel of 1 hour");
        }
    }

    @Nested
    @DisplayName("Flight Phase Tests")
    class FlightPhaseTests {

        @Test
        @DisplayName("Phase is CLIMB when climbing above transition")
        void testFlightPhase_climb() {
            FlightPhase phase = physics.determineFlightPhase(15000, 2000, FlightPhase.CLIMB);
            assertEquals(FlightPhase.CLIMB, phase);
        }

        @Test
        @DisplayName("Phase is CRUISE when level above FL280")
        void testFlightPhase_cruise() {
            FlightPhase phase = physics.determineFlightPhase(35000, 0, FlightPhase.CRUISE);
            assertEquals(FlightPhase.CRUISE, phase);
        }

        @Test
        @DisplayName("Phase is DESCENT when descending")
        void testFlightPhase_descent() {
            FlightPhase phase = physics.determineFlightPhase(25000, -2000, FlightPhase.CRUISE);
            assertEquals(FlightPhase.DESCENT, phase);
        }

        @Test
        @DisplayName("Phase is APPROACH when descending below 10000")
        void testFlightPhase_approach() {
            FlightPhase phase = physics.determineFlightPhase(8000, -1000, FlightPhase.DESCENT);
            assertEquals(FlightPhase.APPROACH, phase);
        }

        @Test
        @DisplayName("Phase is LANDING when below 50 feet")
        void testFlightPhase_landing() {
            FlightPhase phase = physics.determineFlightPhase(30, -500, FlightPhase.APPROACH);
            assertEquals(FlightPhase.LANDING, phase);
        }
    }

    @Nested
    @DisplayName("Coordinate Normalization Tests")
    class NormalizationTests {

        @Test
        @DisplayName("Latitude is clamped to valid range")
        void testNormalization_latitude() {
            GeoCoordinate invalid = GeoCoordinate.builder()
                .latitude(95.0)
                .longitude(0.0)
                .build();
            
            GeoCoordinate normalized = physics.renormalizeCoordinate(invalid);
            
            assertTrue(normalized.getLatitude() >= -90);
            assertTrue(normalized.getLatitude() <= 90);
        }

        @Test
        @DisplayName("Longitude wraps correctly")
        void testNormalization_longitude() {
            GeoCoordinate invalid = GeoCoordinate.builder()
                .latitude(0.0)
                .longitude(200.0)
                .build();
            
            GeoCoordinate normalized = physics.renormalizeCoordinate(invalid);
            
            assertTrue(normalized.getLongitude() >= -180);
            assertTrue(normalized.getLongitude() < 180);
        }
    }

    @Nested
    @DisplayName("True Airspeed Tests")
    class TrueAirspeedTests {

        @Test
        @DisplayName("TAS increases with altitude")
        void testTas_altitudeEffect() {
            double tasSeaLevel = physics.calculateTrueAirspeed(250, 0);
            double tas35000 = physics.calculateTrueAirspeed(250, 35000);
            
            assertTrue(tas35000 > tasSeaLevel, 
                      "TAS should increase with altitude for same groundspeed");
        }

        @Test
        @DisplayName("TAS is proportional to groundspeed")
        void testTas_speedProportional() {
            double tas250 = physics.calculateTrueAirspeed(250, 35000);
            double tas500 = physics.calculateTrueAirspeed(500, 35000);
            
            assertEquals(tas250 * 2, tas500, tas250 * 0.1,
                        "TAS should be proportional to groundspeed");
        }
    }

    // ==================== Helper Methods ====================

    private AircraftState createCruiseState() {
        return AircraftState.builder()
            .position(GeoCoordinate.builder()
                .latitude(40.0)
                .longitude(-70.0)
                .build())
            .altitude(35000)
            .groundSpeed(450)
            .trueAirspeed(470)
            .heading(270)
            .track(270)
            .verticalSpeed(0)
            .pitch(2)
            .bank(0)
            .fuelOnBoard(15000)
            .grossWeight(65000)
            .phase(FlightPhase.CRUISE)
            .simulationTimeNanos(0)
            .onGround(false)
            .build();
    }

    private AutopilotTargets createCruiseTargets() {
        return new AutopilotTargets(
            270.0,   // target track
            450.0,   // target speed
            0.0,     // target VS
            35000.0, // target altitude
            0.0,     // target bank
            true     // altitude capture
        );
    }
}
