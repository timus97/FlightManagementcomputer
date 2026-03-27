package com.aviation.fmc.contracts;

/**
 * Contract for the FMC Performance Computer.
 * Handles performance calculations including fuel, speed, altitude, and time predictions.
 */
public interface PerformanceComputer {

    /**
     * Initialize performance calculations with aircraft weight and fuel.
     * 
     * @param zeroFuelWeight Zero fuel weight in kg/lbs
     * @param fuelOnBoard Current fuel on board in kg/lbs
     * @param centerOfGravity Center of gravity position (% MAC)
     */
    void initialize(double zeroFuelWeight, double fuelOnBoard, double centerOfGravity);

    /**
     * Calculate optimal cruise altitude.
     * 
     * @return Optimal altitude in feet
     */
    double calculateOptimalCruiseAltitude();

    /**
     * Calculate optimal cruise speed (Mach).
     * 
     * @return Optimal Mach number
     */
    double calculateOptimalCruiseMach();

    /**
     * Calculate fuel required for a given distance.
     * 
     * @param distance Distance in nautical miles
     * @param altitude Cruise altitude in feet
     * @param mach Cruise Mach number
     * @return Fuel required in kg/lbs
     */
    double calculateFuelRequired(double distance, double altitude, double mach);

    /**
     * Calculate maximum range with current fuel.
     * 
     * @return Maximum range in nautical miles
     */
    double calculateMaximumRange();

    /**
     * Calculate endurance with current fuel.
     * 
     * @return Endurance in minutes
     */
    double calculateEndurance();

    /**
     * Calculate time required for a given distance.
     * 
     * @param distance Distance in nautical miles
     * @param altitude Cruise altitude in feet
     * @param mach Cruise Mach number
     * @return Time required in minutes
     */
    double calculateTimeRequired(double distance, double altitude, double mach);

    /**
     * Calculate top of descent point.
     * 
     * @param targetAltitude Target altitude at destination
     * @param descentAngle Descent angle in degrees (typically 3.0)
     * @return Distance before destination to start descent (NM)
     */
    double calculateTopOfDescent(double targetAltitude, double descentAngle);

    /**
     * Calculate top of climb point.
     * 
     * @param cruiseAltitude Target cruise altitude
     * @return Distance from departure to reach cruise altitude (NM)
     */
    double calculateTopOfClimb(double cruiseAltitude);

    /**
     * Update fuel flow rate.
     * 
     * @param fuelFlow Current fuel flow in kg/lbs per hour
     */
    void updateFuelFlow(double fuelFlow);

    /**
     * Consume fuel over a time period.
     * 
     * @param minutes Time period in minutes
     */
    void consumeFuel(double minutes);

    /**
     * Get current fuel on board.
     * 
     * @return Fuel in kg/lbs
     */
    double getFuelOnBoard();

    /**
     * Get current gross weight.
     * 
     * @return Weight in kg/lbs
     */
    double getGrossWeight();

    /**
     * Calculate takeoff performance.
     * 
     * @param runwayLength Available runway length (m)
     * @param elevation Runway elevation (ft)
     * @param temperature Temperature (°C)
     * @param wind Wind component (kts, positive = headwind)
     * @return Takeoff performance data
     */
    TakeoffPerformance calculateTakeoffPerformance(
        double runwayLength,
        double elevation,
        double temperature,
        double wind
    );

    /**
     * Calculate landing performance.
     * 
     * @param runwayLength Available runway length (m)
     * @param elevation Runway elevation (ft)
     * @param temperature Temperature (°C)
     * @param wind Wind component (kts, positive = headwind)
     * @return Landing performance data
     */
    LandingPerformance calculateLandingPerformance(
        double runwayLength,
        double elevation,
        double temperature,
        double wind
    );

    /**
     * Get performance limits.
     * 
     * @return Current performance limits
     */
    PerformanceLimits getPerformanceLimits();

    /**
     * Takeoff performance data.
     */
    record TakeoffPerformance(
        double v1,          // Decision speed (kts)
        double vr,          // Rotation speed (kts)
        double v2,          // Takeoff safety speed (kts)
        double requiredDistance,
        double balancedFieldLength,
        boolean withinLimits
    ) {}

    /**
     * Landing performance data.
     */
    record LandingPerformance(
        double vref,        // Reference landing speed (kts)
        double requiredDistance,
        double actualLandingDistance,
        boolean withinLimits
    ) {}

    /**
     * Performance limits.
     */
    record PerformanceLimits(
        double maxAltitude,
        double maxMach,
        double maxSpeedKias,
        double minSpeedKias,
        double maxFuel,
        double maxPayload,
        double maxTakeoffWeight,
        double maxLandingWeight
    ) {}
}
