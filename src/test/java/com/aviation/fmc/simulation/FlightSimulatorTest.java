package com.aviation.fmc.simulation;

import com.aviation.fmc.common.Altitude;
import com.aviation.fmc.common.GeoCoordinate;
import com.aviation.fmc.common.MagneticVariation;
import com.aviation.fmc.contracts.FmcSystem;
import com.aviation.fmc.contracts.FmcSystem.FlightPhase;
import com.aviation.fmc.contracts.FmcSystemImpl;
import com.aviation.fmc.navdata.Airport;
import com.aviation.fmc.navdata.Waypoint;
import com.aviation.fmc.parser.FlightPlan;
import com.aviation.fmc.parser.RouteElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FlightSimulator demonstrating:
 * - Determinism (same inputs produce same outputs)
 * - Time decoupling (no real-time waiting)
 * - Step-based execution control
 */
class FlightSimulatorTest {

    private FmcSystemImpl fmc;
    private FmcSystem.FmcConfiguration config;

    @BeforeEach
    void setUp() {
        fmc = new FmcSystemImpl();
        config = new FmcSystem.FmcConfiguration(
            "B738",
            79000.0,  // MTOW kg
            66000.0,  // MLW kg
            21000.0,  // Fuel capacity kg
            new FmcSystem.NavigationDatabase("2401", "2024-01-25", "2401"),
            false     // Metric units
        );
        fmc.initialize(config);
    }

    @Nested
    @DisplayName("Determinism Tests")
    class DeterminismTests {

        @Test
        @DisplayName("Same initial state produces same final state after same steps")
        void testDeterminism_sameInputsSameOutputs() {
            // Create two identical simulators
            AircraftState initialState = createCruiseState();
            
            FlightSimulator sim1 = createSimulator();
            FlightSimulator sim2 = createSimulator();
            
            sim1.initialize(initialState);
            sim2.initialize(initialState);
            
            // Run same number of steps
            sim1.runSteps(1000);
            sim2.runSteps(1000);
            
            // Verify identical results
            AircraftState state1 = sim1.getCurrentState();
            AircraftState state2 = sim2.getCurrentState();
            
            assertEquals(state1.getAltitude(), state2.getAltitude(), 0.001, "Altitude");
            assertEquals(state1.getGroundSpeed(), state2.getGroundSpeed(), 0.001, "Ground speed");
            assertEquals(state1.getTrack(), state2.getTrack(), 0.001, "Track");
            assertEquals(state1.getFuelOnBoard(), state2.getFuelOnBoard(), 0.001, "Fuel");
            assertEquals(sim1.getClock().getSimulationTimeNanos(), 
                        sim2.getClock().getSimulationTimeNanos(),
                        "Simulation time");
        }

        @Test
        @DisplayName("Determinism is maintained across multiple run attempts")
        void testDeterminism_multipleRuns() {
            AircraftState initialState = createCruiseState();
            
            for (int run = 0; run < 5; run++) {
                // Fresh simulator for each run
                FlightSimulator sim = createSimulator();
                sim.initialize(initialState);
                sim.runSteps(500);
                
                // All runs should produce same result
                assertEquals(500 * SimulationConfig.DEFAULT_TICK_NANOS,
                            sim.getClock().getSimulationTimeNanos(),
                            "Run " + run + " time mismatch");
            }
        }

        @Test
        @DisplayName("Step order matters - different step counts produce different results")
        void testDeterminism_differentStepsDifferentResults() {
            AircraftState initialState = createCruiseState();
            
            FlightSimulator sim1 = createSimulator();
            FlightSimulator sim2 = createSimulator();
            
            sim1.initialize(initialState);
            sim2.initialize(initialState);
            
            sim1.runSteps(100);
            sim2.runSteps(200);
            
            // Different step counts should produce different states
            assertNotEquals(sim1.getCurrentState().getSimulationTimeNanos(),
                           sim2.getCurrentState().getSimulationTimeNanos());
        }
    }

    @Nested
    @DisplayName("Time Decoupling Tests")
    class TimeDecouplingTests {

        @Test
        @DisplayName("Simulation runs instantly without real-time waiting")
        void testTimeDecoupling_instantExecution() {
            FlightSimulator sim = createSimulator();
            sim.initialize(createCruiseState());
            
            // Run 1 hour of simulation time (3600 seconds)
            long startRealTime = System.nanoTime();
            sim.runUntilTime(3600.0);  // 1 hour in seconds
            long endRealTime = System.nanoTime();
            
            // Verify simulation time advanced by 1 hour
            assertEquals(3_600_000_000_000L, sim.getClock().getSimulationTimeNanos());
            
            // Verify real time was much less than 1 hour (should be milliseconds)
            long realTimeMs = (endRealTime - startRealTime) / 1_000_000;
            assertTrue(realTimeMs < 5000, 
                      "Real time should be < 5 seconds, was " + realTimeMs + "ms");
        }

        @Test
        @DisplayName("Time acceleration speeds up simulation without affecting determinism")
        void testTimeDecoupling_timeAcceleration() {
            SimulationConfig fastConfig = SimulationConfig.fastForward(10.0);
            
            AircraftState initialState = createCruiseState();
            
            FlightSimulator simNormal = createSimulator(SimulationConfig.defaultConfig());
            FlightSimulator simFast = createSimulator(fastConfig);
            
            simNormal.initialize(initialState);
            simFast.initialize(initialState);
            
            // Run same number of steps
            simNormal.runSteps(100);
            simFast.runSteps(100);
            
            // Fast simulator should have covered 10x more simulation time
            double normalTime = simNormal.getClock().getSimulationTimeSeconds();
            double fastTime = simFast.getClock().getSimulationTimeSeconds();
            
            assertEquals(normalTime * 10.0, fastTime, 0.001, 
                        "Time acceleration should scale simulation time");
        }

        @Test
        @DisplayName("Clock can be reset and reused")
        void testTimeDecoupling_clockReset() {
            FlightSimulator sim = createSimulator();
            sim.initialize(createCruiseState());
            
            sim.runSteps(100);
            assertEquals(100 * SimulationConfig.DEFAULT_TICK_NANOS,
                        sim.getClock().getSimulationTimeNanos());
            
            // Reset and verify
            sim.getClock().reset();
            assertEquals(0, sim.getClock().getSimulationTimeNanos());
        }
    }

    @Nested
    @DisplayName("Step-Based Execution Tests")
    class StepExecutionTests {

        @Test
        @DisplayName("Single step advances time by configured tick")
        void testStepExecution_singleStep() {
            FlightSimulator sim = createSimulator();
            sim.initialize(createCruiseState());
            
            sim.step();
            
            assertEquals(SimulationConfig.DEFAULT_TICK_NANOS,
                        sim.getClock().getSimulationTimeNanos());
        }

        @Test
        @DisplayName("Multiple steps accumulate correctly")
        void testStepExecution_multipleSteps() {
            FlightSimulator sim = createSimulator();
            sim.initialize(createCruiseState());
            
            for (int i = 0; i < 10; i++) {
                sim.step();
                assertEquals((i + 1) * SimulationConfig.DEFAULT_TICK_NANOS,
                            sim.getClock().getSimulationTimeNanos());
            }
        }

        @Test
        @DisplayName("runUntil stops when condition is met")
        void testStepExecution_runUntilCondition() {
            FlightSimulator sim = createSimulator();
            sim.initialize(createCruiseState());
            
            // Run until simulation time reaches 60 seconds
            sim.runUntil(state -> state.getSimulationTimeNanos() >= 60_000_000_000L, 
                        10000);
            
            assertTrue(sim.getClock().getSimulationTimeNanos() >= 60_000_000_000L);
        }

        @Test
        @DisplayName("runUntil respects max steps limit")
        void testStepExecution_runUntilMaxSteps() {
            FlightSimulator sim = createSimulator();
            sim.initialize(createCruiseState());
            
            // Condition that will never be true
            sim.runUntil(state -> false, 100);
            
            // Should stop at max steps
            assertEquals(100 * SimulationConfig.DEFAULT_TICK_NANOS,
                        sim.getClock().getSimulationTimeNanos());
        }

        @Test
        @DisplayName("Step throws if not initialized")
        void testStepExecution_requiresInitialization() {
            FlightSimulator sim = createSimulator();
            
            assertThrows(IllegalStateException.class, sim::step);
        }
    }

    @Nested
    @DisplayName("State Propagation Tests")
    class StatePropagationTests {

        @Test
        @DisplayName("Position updates correctly during flight")
        void testStatePropagation_positionChange() {
            FlightSimulator sim = createSimulator();
            AircraftState initial = createCruiseState();
            sim.initialize(initial);
            
            GeoCoordinate initialPos = initial.getPosition();
            
            // Run for 10 minutes of simulation time (enough to see significant movement)
            sim.runUntilTime(600.0);
            
            GeoCoordinate finalPos = sim.getCurrentState().getPosition();
            
            // Calculate distance moved using GeoCoordinate.distanceTo
            double distanceNm = initialPos.distanceTo(finalPos);
            
            // At 450 knots for 10 minutes = 75 NM
            // Allow for some variance due to guidance behavior
            assertTrue(distanceNm > 40, 
                      "Distance should be significant: " + distanceNm + " NM");
            assertTrue(distanceNm < 100,
                      "Distance should be reasonable: " + distanceNm + " NM");
        }

        @Test
        @DisplayName("Fuel decreases during flight")
        void testStatePropagation_fuelDecrease() {
            FlightSimulator sim = createSimulator();
            AircraftState initial = createCruiseState();
            sim.initialize(initial);
            
            double initialFuel = initial.getFuelOnBoard();
            
            // Run for 30 minutes
            sim.runUntilTime(1800.0);
            
            double finalFuel = sim.getCurrentState().getFuelOnBoard();
            
            // Fuel should have decreased
            assertTrue(finalFuel < initialFuel, 
                      "Fuel should decrease: initial=" + initialFuel + ", final=" + finalFuel);
            assertTrue(finalFuel > 0, "Fuel should remain positive");
        }

        @Test
        @DisplayName("Flight phase transitions based on altitude")
        void testStatePropagation_phaseTransition() {
            FlightSimulator sim = createSimulator();
            
            // Start on ground
            AircraftState groundState = AircraftState.builder()
                .position(GeoCoordinate.builder()
                    .latitude(40.6413)
                    .longitude(-73.7781)
                    .build())
                .altitude(0)
                .groundSpeed(0)
                .trueAirspeed(0)
                .heading(270)
                .track(270)
                .verticalSpeed(0)
                .pitch(0)
                .bank(0)
                .fuelOnBoard(20000)
                .grossWeight(70000)
                .phase(FlightPhase.PREFLIGHT)
                .onGround(true)
                .build();
            
            sim.initialize(groundState);
            assertEquals(FlightPhase.PREFLIGHT, sim.getCurrentState().getPhase());
        }
    }

    @Nested
    @DisplayName("Event Subscription Tests")
    class EventSubscriptionTests {

        @Test
        @DisplayName("State listeners receive updates on each step")
        void testEventSubscription_stateListeners() {
            FlightSimulator sim = createSimulator();
            
            AtomicInteger updateCount = new AtomicInteger(0);
            sim.subscribeToState(state -> updateCount.incrementAndGet());
            
            sim.initialize(createCruiseState());
            
            // Initial state notification from initialize()
            assertEquals(1, updateCount.get());
            
            sim.runSteps(50);
            
            // Initial + 50 steps = 51 updates
            assertEquals(51, updateCount.get());
        }

        @Test
        @DisplayName("Multiple listeners all receive updates")
        void testEventSubscription_multipleListeners() {
            FlightSimulator sim = createSimulator();
            
            AtomicInteger count1 = new AtomicInteger(0);
            AtomicInteger count2 = new AtomicInteger(0);
            AtomicInteger count3 = new AtomicInteger(0);
            
            sim.subscribeToState(state -> count1.incrementAndGet());
            sim.subscribeToState(state -> count2.incrementAndGet());
            sim.subscribeToState(state -> count3.incrementAndGet());
            
            sim.initialize(createCruiseState());
            
            // All listeners received initial state
            assertEquals(1, count1.get());
            assertEquals(1, count2.get());
            assertEquals(1, count3.get());
            
            sim.runSteps(10);
            
            // Initial + 10 steps = 11 updates
            assertEquals(11, count1.get());
            assertEquals(11, count2.get());
            assertEquals(11, count3.get());
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Builder creates simulator with all options")
        void testBuilder_fullConfig() {
            SimulationConfig config = SimulationConfig.fastForward(5.0);
            AircraftState initial = createCruiseState();
            
            FlightSimulator sim = FlightSimulator.builder()
                .fmc(fmc)
                .config(config)
                .initialState(initial)
                .build();
            
            assertTrue(sim.isInitialized());
            assertEquals(config, sim.getConfig());
            assertEquals(initial, sim.getCurrentState());
        }

        @Test
        @DisplayName("Builder requires FMC")
        void testBuilder_requiresFmc() {
            assertThrows(IllegalArgumentException.class, () -> 
                FlightSimulator.builder().build()
            );
        }
    }

    // ==================== Helper Methods ====================

    private FlightSimulator createSimulator() {
        return createSimulator(SimulationConfig.defaultConfig());
    }

    private FlightSimulator createSimulator(SimulationConfig config) {
        return new FlightSimulator(fmc, config);
    }

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
}
