# FMC Simulator - Flight Management Computer Simulation Software

A comprehensive Java-based Flight Management Computer (FMC) simulation system designed for aviation software development, testing, and training purposes. This project provides a complete architecture for simulating modern aircraft FMC functionality including navigation data management, flight plan processing, performance calculations, and guidance commands.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Navdata Schemas](#navdata-schemas)
  - [Airport](#airport)
  - [Waypoint](#waypoint)
  - [Airway](#airway)
- [Validation Rules and Invariants](#validation-rules-and-invariants)
- [Flight Plan Parser](#flight-plan-parser)
- [FMC Simulation Contracts](#fmc-simulation-contracts)
  - [FmcSystem](#fmcsystem)
  - [NavigationComputer](#navigationcomputer)
  - [PerformanceComputer](#performancecomputer)
  - [GuidanceComputer](#guidancecomputer)
- [Datalink Integration](#datalink-integration)
  - [CPDLC](#cpdlc)
  - [ADS-C](#ads-c)
  - [Flight Plan Uplink](#flight-plan-uplink)
  - [Wind Data](#wind-data)
- [Getting Started](#getting-started)
- [Usage Examples](#usage-examples)
- [Technical Specifications](#technical-specifications)
- [Contributing](#contributing)
- [License](#license)

## Overview

The FMC Simulator is a domain-driven design implementation of a Flight Management Computer system. It follows aviation industry standards including:

- **ARINC 424** - Navigation database specification
- **ICAO Doc 4444** - Flight plan format
- **WGS-84** - World Geodetic System for coordinate reference
- **RTCA DO-178C** - Software considerations in airborne systems (design principles)

### Key Features

- Complete navigation data model (Airports, Waypoints, Airways, Procedures)
- ICAO flight plan parsing and validation
- Performance calculations (fuel, time, altitude, speed)
- Lateral and vertical navigation guidance
- Magnetic variation calculations
- Great circle navigation
- Holding pattern calculations
- Takeoff and landing performance

## Architecture

The system follows a layered architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│                     FMC Contracts Layer                      │
│  ┌─────────────┐ ┌──────────────┐ ┌─────────────────────┐  │
│  │ FmcSystem   │ │ Navigation   │ │ PerformanceComputer │  │
│  │ Interface   │ │ Computer     │ │ Interface           │  │
│  └─────────────┘ └──────────────┘ └─────────────────────┘  │
│  ┌─────────────┐                                           │
│  │ Guidance    │                                           │
│  │ Computer    │                                           │
│  └─────────────┘                                           │
├─────────────────────────────────────────────────────────────┤
│                     Parser Layer                             │
│  ┌─────────────┐ ┌──────────────┐ ┌─────────────────────┐  │
│  │ FlightPlan  │ │ RouteElement │ │ ICAO Parser         │  │
│  │ Parser      │ │ Parser      │ │ Implementation      │  │
│  └─────────────┘ └──────────────┘ └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                     Navdata Layer                            │
│  ┌─────────────┐ ┌──────────────┐ ┌─────────────────────┐  │
│  │ Airport     │ │ Waypoint     │ │ Airway              │  │
│  │ Model       │ │ Model        │ │ Model               │  │
│  └─────────────┘ └──────────────┘ └─────────────────────┘  │
│  ┌─────────────┐ ┌──────────────┐ ┌─────────────────────┐  │
│  │ Runway      │ │ Terminal     │ │ Holding             │  │
│  │ Model       │ │ Procedure    │ │ Pattern             │  │
│  └─────────────┘ └──────────────┘ └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                     Common Layer                             │
│  ┌─────────────┐ ┌──────────────┐ ┌─────────────────────┐  │
│  │ GeoCoordinate│ │ Altitude     │ │ MagneticVariation   │  │
│  └─────────────┘ └──────────────┘ └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                   Validation Layer                           │
│  ┌─────────────┐ ┌──────────────┐                          │
│  │ Navdata     │ │ Validation   │                          │
│  │ Validator   │ │ Result       │                          │
│  └─────────────┘ └──────────────┘                          │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
fmc-simulator/
├── pom.xml                          # Maven configuration
├── README.md                        # This file
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/
│   │           └── aviation/
│   │               └── fmc/
│   │                   ├── common/          # Common value objects
│   │                   │   ├── Altitude.java
│   │                   │   ├── GeoCoordinate.java
│   │                   │   └── MagneticVariation.java
│   │                   ├── contracts/       # FMC interfaces
│   │                   │   ├── FmcSystem.java
│   │                   │   ├── NavigationComputer.java
│   │                   │   ├── PerformanceComputer.java
│   │                   │   └── GuidanceComputer.java
│   │                   ├── model/           # Core domain models
│   │                   ├── navdata/         # Navigation data schemas
│   │                   │   ├── Airport.java
│   │                   │   ├── Airway.java
│   │                   │   ├── AirwaySegment.java
│   │                   │   ├── AltitudeConstraint.java
│   │                   │   ├── AltitudeRange.java
│   │                   │   ├── CommunicationFrequency.java
│   │                   │   ├── HoldingPattern.java
│   │                   │   ├── ProcedureLeg.java
│   │                   │   ├── Runway.java
│   │                   │   ├── TerminalProcedure.java
│   │                   │   └── Waypoint.java
│   │                   ├── parser/          # Flight plan parser
│   │                   │   ├── FlightPlan.java
│   │                   │   ├── FlightPlanParser.java
│   │                   │   ├── IcaoFlightPlanParser.java
│   │                   │   ├── ParserOutput.java
│   │                   │   └── RouteElement.java
│   │                   └── validation/      # Validation framework
│   │                       ├── NavdataValidator.java
│   │                       └── ValidationResult.java
│   └── test/
│       └── java/
│           └── com/aviation/fmc/    # Unit tests
└── docs/                            # Additional documentation
```

## Navdata Schemas

### Airport

The `Airport` class represents an aviation facility with runways, procedures, and communication frequencies.

**Key Attributes:**
- `icaoCode`: 4-character ICAO identifier (e.g., "KJFK", "EGLL")
- `iataCode`: 3-character IATA code (e.g., "JFK", "LHR")
- `referencePoint`: Airport reference point (ARP) coordinates
- `magneticVariation`: Magnetic variation for runway headings
- `transitionAltitude`: Altitude where FL transition occurs
- `runways`: List of available runways
- `sids`: Standard Instrument Departures
- `stars`: Standard Terminal Arrival Routes
- `approaches`: Instrument Approach Procedures

**Invariants:**
- ICAO code must be exactly 4 alphanumeric characters
- At least one runway must exist
- Instrument airports must have magnetic variation defined
- Runway ICAO codes must match the airport

**Example:**
```java
Airport jfk = Airport.builder()
    .icaoCode("KJFK")
    .iataCode("JFK")
    .name("John F Kennedy International")
    .city("New York")
    .countryCode("US")
    .type(Airport.AirportType.CIVIL)
    .referencePoint(GeoCoordinate.builder()
        .latitude(40.6413)
        .longitude(-73.7781)
        .elevationFt(13.0)
        .build())
    .magneticVariation(MagneticVariation.builder()
        .variationDegrees(-13.0)
        .annualChangeMinutes(-3.0)
        .build())
    .transitionAltitude(18000)
    .build();
```

### Waypoint

The `Waypoint` class represents a fixed geographical position used for navigation.

**Types:**
- `ENROUTE_WAYPOINT`: Named enroute waypoint
- `RNAV_WAYPOINT`: RNAV waypoint (5-character name)
- `TERMINAL_WAYPOINT`: SID/STAR/Approach waypoint
- `VOR_DME`, `VORTAC`, `NDB`, `DME`, `TACAN`: Navaid types
- `OCEANIC_WAYPOINT`: Oceanic waypoint (e.g., "N52W030")

**Invariants:**
- Identifier must be 2-5 characters (or oceanic format)
- Coordinates must be valid WGS-84 positions
- Navaid waypoints must have frequency information

**Example:**
```java
Waypoint waypoint = Waypoint.builder()
    .identifier("BOSOX")
    .type(Waypoint.WaypointType.ENROUTE_WAYPOINT)
    .coordinate(GeoCoordinate.builder()
        .latitude(42.3523)
        .longitude(-70.8904)
        .build())
    .build();
```

### Airway

The `Airway` class represents a predefined ATS route connecting waypoints.

**Types:**
- `HIGH_ALTITUDE`: Jet routes (J-routes) - FL180 and above
- `LOW_ALTITUDE`: Victor airways (V-routes) - below FL180
- `RNAV`: Area Navigation routes
- `OCEANIC`: Oceanic routes

**Invariants:**
- Designator must follow ICAO conventions (e.g., "J123", "V456")
- At least 2 waypoints required
- Segments must be connected sequentially
- Altitude constraints must be consistent

**Example:**
```java
Airway airway = Airway.builder()
    .designator("J123")
    .type(Airway.AirwayType.HIGH_ALTITUDE)
    .direction(Airway.Direction.BIDIRECTIONAL)
    .minimumEnrouteAltitude(18000)
    .maximumAuthorizedAltitude(45000)
    .segments(List.of(segment1, segment2, segment3))
    .build();
```

## Validation Rules and Invariants

### NavdataValidator

The `NavdataValidator` provides comprehensive validation for all navigation data entities:

**Airport Validation:**
- ICAO code format (4 alphanumeric characters)
- IATA code format (3 alphanumeric characters, if present)
- At least one runway exists
- Runway consistency (ICAO codes match)
- Magnetic variation present for instrument airports
- Transition altitude/level consistency

**Runway Validation:**
- Identifier format (01-36 with optional L/R/C suffix)
- TORA ≤ TODA and TORA ≤ ASDA
- Heading matches identifier (within 5°)
- True vs magnetic heading consistency

**Waypoint Validation:**
- Identifier format validation
- Coordinate presence
- Navaid frequency validation (VOR: 108-118 MHz, NDB: 190-1750 kHz)

**Airway Validation:**
- Designator format (J/V/L/M/N/P + digits)
- Segment connectivity
- Altitude constraint consistency (MEA ≤ MAA)

**Terminal Procedure Validation:**
- At least one leg exists
- First leg should be IF for non-approaches
- Approach type specified for approaches

### ValidationResult

All validation operations return a `ValidationResult` containing:
- `valid`: Boolean indicating overall validity
- `errors`: List of validation errors with codes and messages
- `warnings`: List of non-fatal warnings

## Flight Plan Parser

### FlightPlan Model

The `FlightPlan` class represents a parsed flight plan ready for FMC processing:

**Key Attributes:**
- `flightNumber`: 1-8 alphanumeric characters
- `aircraftType`: ICAO aircraft type designator
- `departure`: Departure airport
- `destination`: Destination airport
- `route`: List of route elements
- `cruiseAltitude`: Planned cruise altitude
- `estimatedDepartureTime`: ETD in UTC

**Route Elements:**
- Waypoints
- Airways
- Direct segments
- SID/STAR/Approach procedures

### ICAO Flight Plan Parser

The `IcaoFlightPlanParser` implements ICAO Doc 4444 specification for flight plan parsing.

**Supported Format:**
```
(FPL-UAL123-IS
 -B738/M-SDE2E3FGHIJ2J3J4J5M1RWY/LB1
 -KJFK1200
 -N0450F350 DCT BOSOX J123 ALB DCT
 -KBOS0200 KBDL
 -PBN/A1B1C1D1 NAV/GBAS DOF/240101)
```

**Usage:**
```java
FlightPlanParser parser = new IcaoFlightPlanParser();
ParserOutput output = parser.parse(fplString, navigationContext);

if (output.isSuccess()) {
    FlightPlan flightPlan = output.getFlightPlan();
    // Process flight plan
} else {
    List<ParseError> errors = output.getErrors();
    // Handle errors
}
```

## FMC Simulation Contracts

### FmcSystem

The main interface for the Flight Management Computer system.

**Key Operations:**
- `initialize(FmcConfiguration)`: Initialize the system
- `loadFlightPlan(FlightPlan)`: Load a flight plan
- `activateFlightPlan()`: Activate for execution
- `getNavigationComputer()`: Access navigation functions
- `getPerformanceComputer()`: Access performance calculations
- `getGuidanceComputer()`: Access guidance commands

**Flight Phases:**
- PREFLIGHT, TAKEOFF, CLIMB, CRUISE, DESCENT, APPROACH, LANDING, COMPLETED

### NavigationComputer

Handles position determination, waypoint sequencing, and navigation calculations.

**Key Operations:**
- `updatePosition(GeoCoordinate, altitude, heading, groundspeed)`: Update aircraft position
- `getActiveWaypoint()`: Get current "to" waypoint
- `sequenceWaypoint()`: Advance to next waypoint
- `directTo(waypointId)`: Create direct route to waypoint
- `enterHolding(fixId)`: Enter holding pattern
- `calculateCrossTrackError()`: Get lateral deviation

### PerformanceComputer

Handles performance calculations for fuel, speed, altitude, and time.

**Key Operations:**
- `calculateOptimalCruiseAltitude()`: Optimum altitude
- `calculateOptimalCruiseMach()`: Optimum Mach
- `calculateFuelRequired(distance, altitude, mach)`: Fuel prediction
- `calculateTopOfDescent(targetAltitude, descentAngle)`: TOD calculation
- `calculateTakeoffPerformance(...)`: Takeoff speeds and distances
- `calculateLandingPerformance(...)`: Landing speeds and distances

### GuidanceComputer

Generates steering commands for autopilot and flight director.

**Key Operations:**
- `setLnavEnabled(true)`: Enable lateral navigation
- `setVnavEnabled(true)`: Enable vertical navigation
- `getLateralCommand()`: Get roll/heading commands
- `getVerticalCommand()`: Get pitch/throttle commands
- `armApproach(type)`: Arm approach mode
- `setTargetAltitude(altitude)`: Set altitude target

**Command Types:**
- Lateral: HEADING, TRACK, LNAV, LOC, ROLLOUT
- Vertical: ALTITUDE, VERTICAL_SPEED, VNAV_PATH, GLIDEPATH, FLARE

## Datalink Integration

Modern FMC systems rely heavily on **datalink communications** for automated information exchange between aircraft and ground systems. This simulator includes a comprehensive datalink framework.

### Overview

Datalink services include:
- **CPDLC** - Controller-Pilot Datalink Communications
- **ADS-C** - Automatic Dependent Surveillance-Contract
- **FIS-B** - Flight Information Services
- **AOC** - Airline Operational Control

### CPDLC

Controller-Pilot Datalink Communications replaces voice for routine ATC clearances:

**Example Messages:**
```
UPLINK:   "CLEARED TO CLIMB FL350"
RESPONSE: [WILCO/ROGER/UNABLE/STANDBY]

UPLINK:   "CLEARED DIRECT BOSOX"
FMC ACTION: Load direct-to waypoint

UPLINK:   "CLEARED ILS APPROACH RUNWAY 27L"
FMC ACTION: Arm approach mode
```

**Implementation:**
```java
// Request departure clearance
datalink.requestDepartureClearance("KJFK", "A12");

// Process incoming clearances
for (CpdlcMessage msg : datalink.getPendingMessages()) {
    if (msg.type() == CpdlcMessage.MessageType.CLEARANCE) {
        // Pilot reviews on CDU
        datalink.acceptMessage(msg.messageId());
        // FMC executes clearance
        fmc.getGuidanceComputer().setTargetAltitude(newAltitude);
    }
}
```

### ADS-C

Automatic Dependent Surveillance-Contract provides position reporting in oceanic/remote airspace:

**Features:**
- Automatic position reports every 10-14 minutes
- Includes position, altitude, next waypoint, ETA
- Reduces pilot workload
- Improves separation in non-radar airspace

**Implementation:**
```java
// Start reporting to ATC center
datalink.startAdsCReporting("KZBW");

// Automatic report generation
PositionReport report = new PositionReport(
    latitude, longitude, altitude,
    groundspeed, nextWaypoint,
    distanceToNext, etaNext,
    Instant.now()
);
datalink.sendPositionReport(report);
```

### Flight Plan Uplink

Airlines can push flight plans directly to aircraft:

```java
// Request flight plan from ground
datalink.requestFlightPlan("UAL123");

// Receive and validate
Optional<FlightPlan> uplink = datalink.getUplinkedFlightPlan();
if (uplink.isPresent()) {
    FlightPlan plan = uplink.get();
    FmcSystem.LoadResult result = fmc.loadFlightPlan(plan);
    
    if (result.success()) {
        datalink.acceptFlightPlanUplink();
    } else {
        datalink.rejectFlightPlanUplink("Validation failed");
    }
}
```

### Wind Data

Wind uplinks are critical for accurate VNAV calculations:

```java
// Request wind data for route
datalink.requestWindData();

// Process wind uplink
Optional<WindUplink> wind = datalink.getWindUplink();
if (wind.isPresent()) {
    for (WindLevel level : wind.get().windLevels()) {
        // Update FMC wind model
        // Recalculate TOD, fuel, ETA
    }
}
```

**Impact on FMC:**
- Recalculates Top of Descent (TOD)
- Updates fuel predictions
- Optimizes cruise Mach
- Adjusts ETA estimates

### Datalink Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Datalink Manager                          │
├─────────────────────────────────────────────────────────────┤
│  CPDLC Handler │ ADS-C Handler │ FIS-B Handler │ AOC Handler│
├─────────────────────────────────────────────────────────────┤
│                    Message Queue                             │
├─────────────────────────────────────────────────────────────┤
│  VHF Datalink (VDL Mode 2) │ SATCOM │ HF Datalink          │
└─────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴──────────┐
                    │   Ground Networks   │
                    │  (ACARS/ATN/Satellite)│
                    └─────────────────────┘
```

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- Lombok support in IDE

### Build

```bash
cd fmc-simulator
mvn clean compile
```

### Run Tests

```bash
mvn test
```

### Package

```bash
mvn package
```

## Usage Examples

### Creating a Flight Plan Programmatically

```java
// Create airports
Airport kjfk = createAirport("KJFK", "JFK", 40.6413, -73.7781);
Airport kbos = createAirport("KBOS", "BOS", 42.3656, -71.0096);

// Create waypoints
Waypoint bosox = Waypoint.builder()
    .identifier("BOSOX")
    .type(Waypoint.WaypointType.ENROUTE_WAYPOINT)
    .coordinate(GeoCoordinate.builder()
        .latitude(42.3523)
        .longitude(-70.8904)
        .build())
    .build();

// Build flight plan
FlightPlan flightPlan = FlightPlan.builder()
    .flightNumber("AA1234")
    .aircraftType("A321")
    .departure(kjfk)
    .destination(kbos)
    .cruiseAltitude(Altitude.flightLevel(350))
    .trueAirspeed(450)
    .route(List.of(
        RouteElement.builder()
            .sequenceNumber(0)
            .type(RouteElement.ElementType.WAYPOINT)
            .waypoint(bosox)
            .distance(150.0)
            .build()
    ))
    .build();
```

### Using the FMC System

```java
// Initialize FMC
FmcSystem fmc = new FmcSystemImpl();
FmcSystem.InitializationResult initResult = fmc.initialize(
    new FmcSystem.FmcConfiguration(
        "A321",
        89000.0,  // MTOW kg
        75500.0,  // Max landing weight kg
        30000.0,  // Fuel capacity kg
        new FmcSystem.NavigationDatabase("2401", "2024-01-25", "2401"),
        false     // Use imperial units
    )
);

// Load and activate flight plan
FmcSystem.LoadResult loadResult = fmc.loadFlightPlan(flightPlan);
if (loadResult.success()) {
    FmcSystem.ActivationResult activation = fmc.activateFlightPlan();
    // Flight plan is now active
}

// Get guidance commands
GuidanceComputer.LateralCommand latCmd = fmc.getGuidanceComputer().getLateralCommand();
GuidanceComputer.VerticalCommand vertCmd = fmc.getGuidanceComputer().getVerticalCommand();
```

### Parsing an ICAO Flight Plan

```java
String fpl = """
    (FPL-UAL123-IS
     -B738/M-SDE2E3FGHIJ2J3J4J5M1RWY/LB1
     -KJFK1200
     -N0450F350 DCT BOSOX J123 ALB DCT
     -KBOS0200 KBDL)
    """;

FlightPlanParser parser = new IcaoFlightPlanParser();
ParserOutput output = parser.parse(fpl, navigationContext);

if (output.isSuccess()) {
    FlightPlan plan = output.getFlightPlan();
    System.out.println("Flight: " + plan.getFlightNumber());
    System.out.println("Route distance: " + plan.getTotalDistance() + " NM");
}
```

## Technical Specifications

### Coordinate System

- **Datum**: WGS-84
- **Latitude**: -90° to +90°
- **Longitude**: -180° to +180°
- **Precision**: 6 decimal places (~0.1m accuracy)

### Units

- **Distance**: Nautical Miles (NM)
- **Altitude**: Feet (ft)
- **Speed**: Knots (kts) or Mach
- **Heading/Track**: Degrees magnetic or true
- **Weight**: Kilograms (kg) or Pounds (lbs)
- **Temperature**: Celsius (°C)

### Validation Standards

- ICAO Annex 14 (Aerodromes)
- ARINC 424 (Navigation Database)
- ICAO Doc 4444 (PANS-ATM)
- RTCA DO-178C (Software Design)

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- Follow Java naming conventions
- Use Lombok for boilerplate reduction
- Include comprehensive Javadoc
- Write unit tests for new features
- Ensure all validation invariants are documented

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- ICAO for aviation standards and documentation
- ARINC for navigation database specifications
- Aviation industry professionals for domain expertise

## Disclaimer

This software is for simulation and educational purposes only. It is not certified for use in actual aircraft navigation systems. Always use certified avionics equipment for flight operations.
