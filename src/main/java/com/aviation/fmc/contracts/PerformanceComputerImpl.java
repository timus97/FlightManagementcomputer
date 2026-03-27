package com.aviation.fmc.contracts;

/**
 * Implementation of the Performance Computer.
 * Handles performance calculations including fuel, speed, altitude, and time predictions.
 */
public class PerformanceComputerImpl implements PerformanceComputer {

    // Aircraft performance constants (typical narrow-body values)
    private static final double TYPICAL_CRUISE_MACH = 0.78;
    private static final double MAX_OPERATING_MACH = 0.82;
    private static final double MAX_OPERATING_ALTITUDE = 41000.0;
    private static final double OPTIMAL_CLIMB_SPEED = 290.0; // KIAS
    private static final double OPTIMAL_DESCENT_SPEED = 280.0; // KIAS
    private static final double STANDARD_DESCENT_ANGLE = 3.0; // degrees
    private static final double FUEL_FLOW_CRUISE = 2500.0; // kg/hour typical
    private static final double FUEL_FLOW_CLIMB = 4000.0; // kg/hour
    private static final double FUEL_FLOW_DESCENT = 1200.0; // kg/hour (idle)
    private static final double CLIMB_RATE = 1500.0; // fpm
    private static final double DESCENT_RATE = 1800.0; // fpm

    // Aircraft state
    private double zeroFuelWeight;
    private double fuelOnBoard;
    private double centerOfGravity;
    private double currentFuelFlow = FUEL_FLOW_CRUISE;

    // Performance limits
    private double maxTakeoffWeight = 75000.0; // kg
    private double maxLandingWeight = 65000.0; // kg
    private double maxZeroFuelWeight = 60000.0; // kg
    private double maxFuelCapacity = 20000.0; // kg
    private double maxPayload = 20000.0; // kg

    @Override
    public void initialize(double zeroFuelWeight, double fuelOnBoard, double centerOfGravity) {
        this.zeroFuelWeight = zeroFuelWeight;
        this.fuelOnBoard = fuelOnBoard;
        this.centerOfGravity = centerOfGravity;
        this.currentFuelFlow = FUEL_FLOW_CRUISE;
    }

    @Override
    public double calculateOptimalCruiseAltitude() {
        double grossWeight = getGrossWeight();
        
        // Simplified optimal altitude calculation
        // Heavier aircraft fly lower, lighter aircraft fly higher
        // Typical: 41000 ft at light weights, 31000 ft at max weight
        double weightRatio = (grossWeight - maxZeroFuelWeight) / (maxTakeoffWeight - maxZeroFuelWeight);
        double optimalAltitude = 41000 - (weightRatio * 10000);
        
        // Clamp to operational limits
        return Math.min(optimalAltitude, MAX_OPERATING_ALTITUDE);
    }

    @Override
    public double calculateOptimalCruiseMach() {
        double grossWeight = getGrossWeight();
        
        // Optimal Mach varies with weight
        // Lighter = faster optimal Mach (less drag penalty)
        double weightRatio = (grossWeight - maxZeroFuelWeight) / (maxTakeoffWeight - maxZeroFuelWeight);
        double optimalMach = 0.76 + (1 - weightRatio) * 0.04;
        
        // Clamp to limits
        return Math.min(optimalMach, MAX_OPERATING_MACH);
    }

    @Override
    public double calculateFuelRequired(double distance, double altitude, double mach) {
        // Calculate flight time
        double timeHours = calculateTimeRequired(distance, altitude, mach) / 60.0;
        
        // Calculate fuel flow at given conditions
        double fuelFlow = calculateFuelFlowAtConditions(altitude, mach);
        
        // Add reserves (contingency + alternate + final reserve)
        double tripFuel = fuelFlow * timeHours;
        double contingency = tripFuel * 0.05; // 5% contingency
        double alternateFuel = 1500.0; // Fixed alternate reserve (kg)
        double finalReserve = 1200.0; // 30 min at holding (kg)
        
        return tripFuel + contingency + alternateFuel + finalReserve;
    }

    @Override
    public double calculateMaximumRange() {
        // Maximum range = fuel on board / fuel consumption per NM
        double optimalAltitude = calculateOptimalCruiseAltitude();
        double optimalMach = calculateOptimalCruiseMach();
        
        // Convert Mach to TAS at altitude
        double tas = machToTas(optimalMach, optimalAltitude);
        double fuelFlow = calculateFuelFlowAtConditions(optimalAltitude, optimalMach);
        
        // Fuel consumption per NM
        double fuelPerNm = fuelFlow / tas;
        
        // Usable fuel (minus reserves)
        double usableFuel = fuelOnBoard - 3000.0; // Reserve buffer
        
        return usableFuel / fuelPerNm;
    }

    @Override
    public double calculateEndurance() {
        double optimalAltitude = calculateOptimalCruiseAltitude();
        double optimalMach = calculateOptimalCruiseMach();
        
        double fuelFlow = calculateFuelFlowAtConditions(optimalAltitude, optimalMach);
        double usableFuel = fuelOnBoard - 3000.0; // Reserve buffer
        
        return (usableFuel / fuelFlow) * 60.0; // Convert to minutes
    }

    @Override
    public double calculateTimeRequired(double distance, double altitude, double mach) {
        double tas = machToTas(mach, altitude);
        
        if (tas <= 0) {
            return Double.MAX_VALUE;
        }
        
        return (distance / tas) * 60.0; // minutes
    }

    @Override
    public double calculateTopOfDescent(double targetAltitude, double descentAngle) {
        if (currentFuelFlow <= 0) {
            return 0.0;
        }
        
        // Use current altitude from navigation computer if available
        double currentAltitude = 35000.0; // Default to typical cruise
        
        double altitudeDifference = currentAltitude - targetAltitude;
        
        // Distance = altitude difference / tan(descent angle)
        double descentAngleRad = Math.toRadians(descentAngle);
        double distanceNm = altitudeDifference / (6076.12 * Math.tan(descentAngleRad));
        
        return distanceNm;
    }

    @Override
    public double calculateTopOfClimb(double cruiseAltitude) {
        // Simplified TOC calculation
        // Assumes constant climb rate and ground speed
        double climbDistance = (cruiseAltitude / CLIMB_RATE) * (OPTIMAL_CLIMB_SPEED / 60.0);
        return climbDistance;
    }

    @Override
    public void updateFuelFlow(double fuelFlow) {
        this.currentFuelFlow = fuelFlow;
    }

    @Override
    public void consumeFuel(double minutes) {
        double fuelConsumed = (currentFuelFlow / 60.0) * minutes;
        fuelOnBoard = Math.max(0.0, fuelOnBoard - fuelConsumed);
    }

    @Override
    public double getFuelOnBoard() {
        return fuelOnBoard;
    }

    @Override
    public double getGrossWeight() {
        return zeroFuelWeight + fuelOnBoard;
    }

    @Override
    public TakeoffPerformance calculateTakeoffPerformance(
            double runwayLength,
            double elevation,
            double temperature,
            double wind) {
        
        double grossWeight = getGrossWeight();
        
        // Calculate speeds based on weight (simplified)
        double v1 = 140 + (grossWeight - 50000) / 1000.0;
        double vr = 145 + (grossWeight - 50000) / 1000.0;
        double v2 = 150 + (grossWeight - 50000) / 1000.0;
        
        // Adjust for wind
        v1 = Math.max(120, v1 - wind * 0.5);
        vr = Math.max(125, vr - wind * 0.5);
        v2 = Math.max(130, v2 - wind * 0.5);
        
        // Calculate required distance
        double baseDistance = 1500 + (grossWeight - 50000) / 20.0;
        double elevationFactor = 1 + (elevation / 10000.0); // Higher = longer
        double tempFactor = 1 + ((temperature - 15) / 100.0); // Hotter = longer
        double windFactor = 1 - (wind / 100.0); // Headwind = shorter
        
        double requiredDistance = baseDistance * elevationFactor * tempFactor * windFactor;
        double balancedFieldLength = requiredDistance * 1.15;
        
        boolean withinLimits = requiredDistance <= runwayLength && 
                              grossWeight <= maxTakeoffWeight;
        
        return new TakeoffPerformance(
            Math.round(v1),
            Math.round(vr),
            Math.round(v2),
            Math.round(requiredDistance),
            Math.round(balancedFieldLength),
            withinLimits
        );
    }

    @Override
    public LandingPerformance calculateLandingPerformance(
            double runwayLength,
            double elevation,
            double temperature,
            double wind) {
        
        double grossWeight = getGrossWeight();
        
        // Vref calculation based on weight
        double vref = 130 + (grossWeight - 50000) / 1500.0;
        
        // Adjust for wind
        vref = Math.max(115, vref - wind * 0.3);
        
        // Calculate distances
        double baseDistance = 1200 + (grossWeight - 50000) / 30.0;
        double elevationFactor = 1 + (elevation / 15000.0);
        double tempFactor = 1 + ((temperature - 15) / 150.0);
        double windFactor = 1 - (wind / 150.0);
        
        double requiredDistance = baseDistance * elevationFactor * tempFactor * windFactor;
        double actualLandingDistance = requiredDistance * 0.85; // Typically 85% of required
        
        boolean withinLimits = requiredDistance <= runwayLength && 
                              grossWeight <= maxLandingWeight;
        
        return new LandingPerformance(
            Math.round(vref),
            Math.round(requiredDistance),
            Math.round(actualLandingDistance),
            withinLimits
        );
    }

    @Override
    public PerformanceLimits getPerformanceLimits() {
        return new PerformanceLimits(
            MAX_OPERATING_ALTITUDE,
            MAX_OPERATING_MACH,
            340.0, // Max speed KIAS
            120.0, // Min speed KIAS (clean)
            maxFuelCapacity,
            maxPayload,
            maxTakeoffWeight,
            maxLandingWeight
        );
    }

    // ==================== Helper Methods ====================

    /**
     * Calculate fuel flow at given flight conditions.
     */
    private double calculateFuelFlowAtConditions(double altitude, double mach) {
        // Base fuel flow
        double fuelFlow = FUEL_FLOW_CRUISE;
        
        // Adjust for altitude (more efficient at altitude up to a point)
        double altitudeFactor = 1.0 - (altitude / 100000.0);
        fuelFlow *= Math.max(0.7, altitudeFactor);
        
        // Adjust for Mach (higher Mach = higher fuel flow)
        double machFactor = 1.0 + (mach - TYPICAL_CRUISE_MACH) * 2.0;
        fuelFlow *= Math.max(0.8, machFactor);
        
        return fuelFlow;
    }

    /**
     * Convert Mach to True Airspeed.
     * @param mach Mach number
     * @param altitude Altitude in feet
     * @return True airspeed in knots
     */
    private double machToTas(double mach, double altitude) {
        // Simplified: Speed of sound decreases with altitude
        // At sea level: ~661 knots, at 35000 ft: ~575 knots
        double speedOfSound = 661.0 * Math.sqrt((288.15 - 0.00198 * altitude) / 288.15);
        return mach * speedOfSound;
    }

    /**
     * Set aircraft limits for performance calculations.
     */
    public void setAircraftLimits(
            double maxTakeoffWeight,
            double maxLandingWeight,
            double maxZeroFuelWeight,
            double maxFuelCapacity) {
        this.maxTakeoffWeight = maxTakeoffWeight;
        this.maxLandingWeight = maxLandingWeight;
        this.maxZeroFuelWeight = maxZeroFuelWeight;
        this.maxFuelCapacity = maxFuelCapacity;
    }
}
