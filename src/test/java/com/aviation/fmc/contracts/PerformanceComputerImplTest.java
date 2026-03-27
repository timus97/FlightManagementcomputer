package com.aviation.fmc.contracts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PerformanceComputerImpl.
 */
class PerformanceComputerImplTest {

    private PerformanceComputerImpl performanceComputer;

    // Typical narrow-body aircraft parameters
    private static final double ZERO_FUEL_WEIGHT = 55000.0; // kg
    private static final double FUEL_ON_BOARD = 12000.0; // kg
    private static final double CENTER_OF_GRAVITY = 25.0; // % MAC

    @BeforeEach
    void setUp() {
        performanceComputer = new PerformanceComputerImpl();
        performanceComputer.initialize(ZERO_FUEL_WEIGHT, FUEL_ON_BOARD, CENTER_OF_GRAVITY);
    }

    @Test
    void testInitialize() {
        assertThat(performanceComputer.getFuelOnBoard()).isEqualTo(FUEL_ON_BOARD);
        assertThat(performanceComputer.getGrossWeight()).isEqualTo(ZERO_FUEL_WEIGHT + FUEL_ON_BOARD);
    }

    @Test
    void testCalculateOptimalCruiseAltitude() {
        double optimalAltitude = performanceComputer.calculateOptimalCruiseAltitude();

        // Should be between 31000 and 41000 ft for typical weights
        assertThat(optimalAltitude).isBetween(31000.0, 41000.0);
    }

    @Test
    void testCalculateOptimalCruiseAltitudeHeavierAircraft() {
        // Heavier aircraft should have lower optimal altitude
        performanceComputer.initialize(60000.0, 15000.0, CENTER_OF_GRAVITY);
        double heavyAltitude = performanceComputer.calculateOptimalCruiseAltitude();

        performanceComputer.initialize(50000.0, 8000.0, CENTER_OF_GRAVITY);
        double lightAltitude = performanceComputer.calculateOptimalCruiseAltitude();

        assertThat(heavyAltitude).isLessThan(lightAltitude);
    }

    @Test
    void testCalculateOptimalCruiseMach() {
        double optimalMach = performanceComputer.calculateOptimalCruiseMach();

        // Should be between 0.76 and 0.82
        assertThat(optimalMach).isBetween(0.76, 0.82);
    }

    @Test
    void testCalculateFuelRequired() {
        double distance = 500.0; // NM
        double altitude = 35000.0; // ft
        double mach = 0.78;

        double fuelRequired = performanceComputer.calculateFuelRequired(distance, altitude, mach);

        // Should be positive and reasonable for a 500 NM flight
        assertThat(fuelRequired).isGreaterThan(0.0);
        assertThat(fuelRequired).isLessThan(FUEL_ON_BOARD); // Should fit in tanks
    }

    @Test
    void testCalculateMaximumRange() {
        double maxRange = performanceComputer.calculateMaximumRange();

        // Should be positive and reasonable
        assertThat(maxRange).isGreaterThan(0.0);
        assertThat(maxRange).isGreaterThan(1000.0); // At least 1000 NM
    }

    @Test
    void testCalculateMaximumRangeWithMoreFuel() {
        // More fuel should give more range
        performanceComputer.initialize(ZERO_FUEL_WEIGHT, 15000.0, CENTER_OF_GRAVITY);
        double longRange = performanceComputer.calculateMaximumRange();

        performanceComputer.initialize(ZERO_FUEL_WEIGHT, 8000.0, CENTER_OF_GRAVITY);
        double shortRange = performanceComputer.calculateMaximumRange();

        assertThat(longRange).isGreaterThan(shortRange);
    }

    @Test
    void testCalculateEndurance() {
        double endurance = performanceComputer.calculateEndurance();

        // Should be positive (in minutes)
        assertThat(endurance).isGreaterThan(0.0);
        assertThat(endurance).isGreaterThan(120.0); // At least 2 hours
    }

    @Test
    void testCalculateTimeRequired() {
        double distance = 600.0; // NM
        double altitude = 35000.0; // ft
        double mach = 0.78;

        double timeRequired = performanceComputer.calculateTimeRequired(distance, altitude, mach);

        // At M0.78 at FL350, TAS is roughly 450 knots
        // 600 NM / 450 kts = 1.33 hours = 80 minutes
        assertThat(timeRequired).isCloseTo(80.0, within(20.0));
    }

    @Test
    void testCalculateTimeRequiredZeroDistance() {
        double timeRequired = performanceComputer.calculateTimeRequired(0.0, 35000.0, 0.78);
        assertThat(timeRequired).isEqualTo(0.0);
    }

    @Test
    void testCalculateTopOfDescent() {
        double targetAltitude = 3000.0; // ft
        double descentAngle = 3.0; // degrees

        double tod = performanceComputer.calculateTopOfDescent(targetAltitude, descentAngle);

        // Should be positive (distance before destination)
        assertThat(tod).isGreaterThan(0.0);

        // At 3 degrees, altitude difference of 32000 ft should give roughly 100 NM
        assertThat(tod).isCloseTo(100.0, within(20.0));
    }

    @Test
    void testCalculateTopOfClimb() {
        double cruiseAltitude = 35000.0; // ft

        double toc = performanceComputer.calculateTopOfClimb(cruiseAltitude);

        // Should be positive
        assertThat(toc).isGreaterThan(0.0);
    }

    @Test
    void testUpdateFuelFlow() {
        performanceComputer.updateFuelFlow(3000.0);
        // No direct assertion, but should not throw
    }

    @Test
    void testConsumeFuel() {
        double initialFuel = performanceComputer.getFuelOnBoard();

        performanceComputer.consumeFuel(60.0); // 1 hour

        double remainingFuel = performanceComputer.getFuelOnBoard();
        assertThat(remainingFuel).isLessThan(initialFuel);
    }

    @Test
    void testConsumeFuelDoesNotGoNegative() {
        // Consume way more fuel than available
        performanceComputer.consumeFuel(1000.0);

        assertThat(performanceComputer.getFuelOnBoard()).isEqualTo(0.0);
    }

    @Test
    void testGetFuelOnBoard() {
        assertThat(performanceComputer.getFuelOnBoard()).isEqualTo(FUEL_ON_BOARD);
    }

    @Test
    void testGetGrossWeight() {
        double expectedGrossWeight = ZERO_FUEL_WEIGHT + FUEL_ON_BOARD;
        assertThat(performanceComputer.getGrossWeight()).isEqualTo(expectedGrossWeight);
    }

    @Test
    void testCalculateTakeoffPerformance() {
        double runwayLength = 3000.0; // meters
        double elevation = 1000.0; // ft
        double temperature = 25.0; // Celsius
        double wind = 10.0; // knots headwind

        PerformanceComputer.TakeoffPerformance toPerf = 
            performanceComputer.calculateTakeoffPerformance(runwayLength, elevation, temperature, wind);

        assertThat(toPerf).isNotNull();
        assertThat(toPerf.v1()).isGreaterThan(0.0);
        assertThat(toPerf.vr()).isGreaterThan(toPerf.v1());
        assertThat(toPerf.v2()).isGreaterThan(toPerf.vr());
        assertThat(toPerf.requiredDistance()).isGreaterThan(0.0);
        assertThat(toPerf.balancedFieldLength()).isGreaterThan(toPerf.requiredDistance());
    }

    @Test
    void testCalculateTakeoffPerformanceWithinLimits() {
        // Long runway should be within limits
        PerformanceComputer.TakeoffPerformance toPerf = 
            performanceComputer.calculateTakeoffPerformance(4000.0, 0.0, 15.0, 10.0);

        assertThat(toPerf.withinLimits()).isTrue();
    }

    @Test
    void testCalculateTakeoffPerformanceOverweight() {
        // Overweight aircraft on short runway should be out of limits
        performanceComputer.initialize(70000.0, 15000.0, CENTER_OF_GRAVITY); // Very heavy

        PerformanceComputer.TakeoffPerformance toPerf = 
            performanceComputer.calculateTakeoffPerformance(2000.0, 5000.0, 35.0, 0.0);

        assertThat(toPerf.withinLimits()).isFalse();
    }

    @Test
    void testCalculateTakeoffPerformanceWindEffect() {
        // Same conditions, different winds
        PerformanceComputer.TakeoffPerformance headwindPerf = 
            performanceComputer.calculateTakeoffPerformance(3000.0, 0.0, 15.0, 20.0);

        PerformanceComputer.TakeoffPerformance tailwindPerf = 
            performanceComputer.calculateTakeoffPerformance(3000.0, 0.0, 15.0, -10.0);

        // Headwind should result in shorter required distance
        assertThat(headwindPerf.requiredDistance()).isLessThan(tailwindPerf.requiredDistance());

        // Headwind should result in lower V1/VR/V2
        assertThat(headwindPerf.v1()).isLessThan(tailwindPerf.v1());
    }

    @Test
    void testCalculateLandingPerformance() {
        double runwayLength = 2500.0; // meters
        double elevation = 1000.0; // ft
        double temperature = 25.0; // Celsius
        double wind = 10.0; // knots headwind

        PerformanceComputer.LandingPerformance ldgPerf = 
            performanceComputer.calculateLandingPerformance(runwayLength, elevation, temperature, wind);

        assertThat(ldgPerf).isNotNull();
        assertThat(ldgPerf.vref()).isGreaterThan(0.0);
        assertThat(ldgPerf.requiredDistance()).isGreaterThan(0.0);
        assertThat(ldgPerf.actualLandingDistance()).isGreaterThan(0.0);
        assertThat(ldgPerf.actualLandingDistance()).isLessThan(ldgPerf.requiredDistance());
    }

    @Test
    void testCalculateLandingPerformanceWithinLimits() {
        // Initialize with lighter weight to ensure we're within landing limits
        performanceComputer.initialize(50000.0, 5000.0, CENTER_OF_GRAVITY);
        
        PerformanceComputer.LandingPerformance ldgPerf = 
            performanceComputer.calculateLandingPerformance(3000.0, 0.0, 15.0, 10.0);

        assertThat(ldgPerf.withinLimits()).isTrue();
    }

    @Test
    void testCalculateLandingPerformanceWindEffect() {
        PerformanceComputer.LandingPerformance headwindPerf = 
            performanceComputer.calculateLandingPerformance(2500.0, 0.0, 15.0, 20.0);

        PerformanceComputer.LandingPerformance tailwindPerf = 
            performanceComputer.calculateLandingPerformance(2500.0, 0.0, 15.0, -10.0);

        // Headwind should result in shorter required distance
        assertThat(headwindPerf.requiredDistance()).isLessThan(tailwindPerf.requiredDistance());

        // Headwind should result in lower Vref
        assertThat(headwindPerf.vref()).isLessThan(tailwindPerf.vref());
    }

    @Test
    void testGetPerformanceLimits() {
        PerformanceComputer.PerformanceLimits limits = performanceComputer.getPerformanceLimits();

        assertThat(limits).isNotNull();
        assertThat(limits.maxAltitude()).isEqualTo(41000.0);
        assertThat(limits.maxMach()).isEqualTo(0.82);
        assertThat(limits.maxSpeedKias()).isEqualTo(340.0);
        assertThat(limits.minSpeedKias()).isEqualTo(120.0);
        assertThat(limits.maxFuel()).isEqualTo(20000.0);
        assertThat(limits.maxPayload()).isEqualTo(20000.0);
        assertThat(limits.maxTakeoffWeight()).isEqualTo(75000.0);
        assertThat(limits.maxLandingWeight()).isEqualTo(65000.0);
    }

    @Test
    void testSetAircraftLimits() {
        performanceComputer.setAircraftLimits(80000.0, 70000.0, 65000.0, 25000.0);

        PerformanceComputer.PerformanceLimits limits = performanceComputer.getPerformanceLimits();
        assertThat(limits.maxTakeoffWeight()).isEqualTo(80000.0);
        assertThat(limits.maxLandingWeight()).isEqualTo(70000.0);
        assertThat(limits.maxFuel()).isEqualTo(25000.0);
    }

    @Test
    void testWeightTracking() {
        double initialWeight = performanceComputer.getGrossWeight();

        // Consume some fuel
        performanceComputer.consumeFuel(30.0); // 30 minutes

        double newWeight = performanceComputer.getGrossWeight();
        assertThat(newWeight).isLessThan(initialWeight);
    }

    @Test
    void testTemperatureEffectOnTakeoff() {
        // Hot day performance
        PerformanceComputer.TakeoffPerformance hotPerf = 
            performanceComputer.calculateTakeoffPerformance(3000.0, 0.0, 40.0, 0.0);

        // Cold day performance
        PerformanceComputer.TakeoffPerformance coldPerf = 
            performanceComputer.calculateTakeoffPerformance(3000.0, 0.0, 0.0, 0.0);

        // Hot day should require more distance
        assertThat(hotPerf.requiredDistance()).isGreaterThan(coldPerf.requiredDistance());
    }

    @Test
    void testElevationEffectOnTakeoff() {
        // Sea level
        PerformanceComputer.TakeoffPerformance seaLevelPerf = 
            performanceComputer.calculateTakeoffPerformance(3000.0, 0.0, 15.0, 0.0);

        // High elevation (Denver-like)
        PerformanceComputer.TakeoffPerformance highElevationPerf = 
            performanceComputer.calculateTakeoffPerformance(3000.0, 5000.0, 15.0, 0.0);

        // High elevation should require more distance
        assertThat(highElevationPerf.requiredDistance()).isGreaterThan(seaLevelPerf.requiredDistance());
    }
}
