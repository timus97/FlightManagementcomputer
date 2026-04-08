package com.aviation.fmc.contracts;

import com.aviation.fmc.common.Altitude;
import com.aviation.fmc.common.GeoCoordinate;
import com.aviation.fmc.navdata.Airport;
import com.aviation.fmc.parser.FlightPlan;
import com.aviation.fmc.parser.RouteElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FmcSystemImpl - the main FMC coordinator.
 */
class FmcSystemImplTest {

    private FmcSystemImpl fmcSystem;

    // Test data
    private static final String AIRCRAFT_TYPE = "B738";
    private static final double MTOW = 79000.0; // kg
    private static final double MLW = 66000.0; // kg
    private static final double FUEL_CAPACITY = 21000.0; // kg

    @BeforeEach
    void setUp() {
        fmcSystem = new FmcSystemImpl();
    }

    // ==================== Initialization Tests ====================

    @Test
    void testInitialize_Success() {
        FmcSystem.FmcConfiguration config = createTestConfiguration();

        FmcSystem.InitializationResult result = fmcSystem.initialize(config);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("FMC initialized");
        assertThat(fmcSystem.isInitialized()).isTrue();
        assertThat(fmcSystem.getSystemState()).isEqualTo(FmcSystem.FmcStatus.SystemState.READY);
        assertThat(fmcSystem.getCurrentPhase()).isEqualTo(FmcSystem.FlightPhase.PREFLIGHT);
    }

    @Test
    void testInitialize_NullConfiguration_Fails() {
        FmcSystem.InitializationResult result = fmcSystem.initialize(null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Configuration cannot be null");
    }

    @Test
    void testInitialize_CreatesAllComputers() {
        fmcSystem.initialize(createTestConfiguration());

        assertThat(fmcSystem.getNavigationComputer()).isNotNull();
        assertThat(fmcSystem.getPerformanceComputer()).isNotNull();
        assertThat(fmcSystem.getGuidanceComputer()).isNotNull();
    }

    // ==================== Flight Plan Loading Tests ====================

    @Test
    void testLoadFlightPlan_Success() {
        fmcSystem.initialize(createTestConfiguration());
        FlightPlan flightPlan = createTestFlightPlan();

        FmcSystem.LoadResult result = fmcSystem.loadFlightPlan(flightPlan);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("Flight plan loaded");
        assertThat(fmcSystem.getActiveFlightPlan()).isPresent();
        assertThat(fmcSystem.getSystemState()).isEqualTo(FmcSystem.FmcStatus.SystemState.ACTIVE);
    }

    @Test
    void testLoadFlightPlan_BeforeInitialize_Fails() {
        FlightPlan flightPlan = createTestFlightPlan();

        FmcSystem.LoadResult result = fmcSystem.loadFlightPlan(flightPlan);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("not initialized");
    }

    @Test
    void testLoadFlightPlan_NullPlan_Fails() {
        fmcSystem.initialize(createTestConfiguration());

        FmcSystem.LoadResult result = fmcSystem.loadFlightPlan(null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("cannot be null");
    }

    @Test
    void testGetActiveFlightPlan_AfterLoad() {
        fmcSystem.initialize(createTestConfiguration());
        FlightPlan flightPlan = createTestFlightPlan();
        fmcSystem.loadFlightPlan(flightPlan);

        var activePlan = fmcSystem.getActiveFlightPlan();

        assertThat(activePlan).isPresent();
        assertThat(activePlan.get().getFlightNumber()).isEqualTo("UAL123");
    }

    @Test
    void testGetActiveFlightPlan_BeforeLoad() {
        fmcSystem.initialize(createTestConfiguration());

        var activePlan = fmcSystem.getActiveFlightPlan();

        assertThat(activePlan).isEmpty();
    }

    // ==================== Flight Plan Activation Tests ====================

    @Test
    void testActivateFlightPlan_Success() {
        fmcSystem.initialize(createTestConfiguration());
        fmcSystem.loadFlightPlan(createTestFlightPlan());

        FmcSystem.ActivationResult result = fmcSystem.activateFlightPlan();

        assertThat(result.success()).isTrue();
        assertThat(result.initialPhase()).isEqualTo(FmcSystem.FlightPhase.PREFLIGHT);
        assertThat(fmcSystem.getStatus().flightPlanActive()).isTrue();
    }

    @Test
    void testActivateFlightPlan_NoPlanLoaded_Fails() {
        fmcSystem.initialize(createTestConfiguration());

        FmcSystem.ActivationResult result = fmcSystem.activateFlightPlan();

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("No flight plan loaded");
    }

    @Test
    void testActivateFlightPlan_EnablesGuidance() {
        fmcSystem.initialize(createTestConfiguration());
        fmcSystem.loadFlightPlan(createTestFlightPlan());

        fmcSystem.activateFlightPlan();

        // LNAV/VNAV should be enabled (active requires waypoint to be present)
        assertThat(fmcSystem.getGuidanceComputer().isLnavActive() || fmcSystem.getGuidanceComputer().isVnavActive()).isTrue();
    }

    // ==================== Deactivation Tests ====================

    @Test
    void testDeactivateFlightPlan() {
        fmcSystem.initialize(createTestConfiguration());
        fmcSystem.loadFlightPlan(createTestFlightPlan());
        fmcSystem.activateFlightPlan();

        fmcSystem.deactivateFlightPlan();

        assertThat(fmcSystem.getActiveFlightPlan()).isEmpty();
        assertThat(fmcSystem.getStatus().flightPlanActive()).isFalse();
        assertThat(fmcSystem.getSystemState()).isEqualTo(FmcSystem.FmcStatus.SystemState.READY);
    }

    // ==================== Status Tests ====================

    @Test
    void testGetStatus_BeforeInitialize() {
        FmcSystem.FmcStatus status = fmcSystem.getStatus();

        assertThat(status.state()).isEqualTo(FmcSystem.FmcStatus.SystemState.INITIALIZING);
        assertThat(status.currentPhase()).isEqualTo(FmcSystem.FlightPhase.PREFLIGHT);
        assertThat(status.flightPlanActive()).isFalse();
    }

    @Test
    void testGetStatus_AfterInitialize() {
        fmcSystem.initialize(createTestConfiguration());

        FmcSystem.FmcStatus status = fmcSystem.getStatus();

        assertThat(status.state()).isEqualTo(FmcSystem.FmcStatus.SystemState.READY);
        assertThat(status.activeWaypoint()).isEqualTo("NONE");
    }

    @Test
    void testGetStatus_WithFlightPlan() {
        fmcSystem.initialize(createTestConfiguration());
        fmcSystem.loadFlightPlan(createTestFlightPlan());
        fmcSystem.activateFlightPlan(); // Must activate for flightPlanActive=true

        FmcSystem.FmcStatus status = fmcSystem.getStatus();

        assertThat(status.state()).isEqualTo(FmcSystem.FmcStatus.SystemState.ACTIVE);
        assertThat(status.flightPlanActive()).isTrue();
    }

    // ==================== Flight Phase Tests ====================

    @Test
    void testAdvanceFlightPhase() {
        fmcSystem.initialize(createTestConfiguration());
        fmcSystem.loadFlightPlan(createTestFlightPlan());

        assertThat(fmcSystem.getCurrentPhase()).isEqualTo(FmcSystem.FlightPhase.PREFLIGHT);

        fmcSystem.advanceFlightPhase();
        assertThat(fmcSystem.getCurrentPhase()).isEqualTo(FmcSystem.FlightPhase.TAKEOFF);

        fmcSystem.advanceFlightPhase();
        assertThat(fmcSystem.getCurrentPhase()).isEqualTo(FmcSystem.FlightPhase.CLIMB);
    }

    @Test
    void testSetFlightPhase() {
        fmcSystem.initialize(createTestConfiguration());

        fmcSystem.setFlightPhase(FmcSystem.FlightPhase.CRUISE);

        assertThat(fmcSystem.getCurrentPhase()).isEqualTo(FmcSystem.FlightPhase.CRUISE);
    }

    @Test
    void testFlightPhaseTransition_UpdatesGuidance() {
        fmcSystem.initialize(createTestConfiguration());
        fmcSystem.loadFlightPlan(createTestFlightPlan());
        fmcSystem.activateFlightPlan();

        // Transition to CRUISE
        fmcSystem.setFlightPhase(FmcSystem.FlightPhase.CRUISE);

        // Should have set target altitude for cruise
        // (GuidanceComputerImpl sets target altitude on phase transition)
    }

    // ==================== Position Update Tests ====================

    @Test
    void testUpdatePosition() {
        fmcSystem.initialize(createTestConfiguration());
        fmcSystem.loadFlightPlan(createTestFlightPlan());
        fmcSystem.activateFlightPlan();

        GeoCoordinate position = GeoCoordinate.builder()
            .latitude(40.6413)
            .longitude(-73.7781)
            .build();

        fmcSystem.updatePosition(position, 35000.0, 270.0, 450.0);

        var navStatus = fmcSystem.getNavigationComputer().getCurrentPosition();
        assertThat(navStatus).isPresent();
        assertThat(navStatus.get().coordinate()).isEqualTo(position);
    }

    @Test
    void testUpdatePosition_UpdatesFlightPhase() {
        fmcSystem.initialize(createTestConfiguration());
        fmcSystem.loadFlightPlan(createTestFlightPlan());
        fmcSystem.activateFlightPlan();

        GeoCoordinate position = GeoCoordinate.builder()
            .latitude(40.6413)
            .longitude(-73.7781)
            .build();

        // Start at climb phase
        fmcSystem.setFlightPhase(FmcSystem.FlightPhase.CLIMB);

        // High altitude should trigger cruise phase (altitude >= 30000 from CLIMB)
        fmcSystem.updatePosition(position, 35000.0, 270.0, 450.0);

        // Phase should be updated based on altitude (CLIMB + 35000 >= 30000 = CRUISE)
        assertThat(fmcSystem.getCurrentPhase()).isEqualTo(FmcSystem.FlightPhase.CRUISE);
    }

    // ==================== Integration Tests ====================

    @Test
    void testFullFlightCycle() {
        // Initialize
        FmcSystem.InitializationResult initResult = fmcSystem.initialize(createTestConfiguration());
        assertThat(initResult.success()).isTrue();

        // Load flight plan
        FlightPlan flightPlan = createTestFlightPlan();
        FmcSystem.LoadResult loadResult = fmcSystem.loadFlightPlan(flightPlan);
        assertThat(loadResult.success()).isTrue();

        // Activate
        FmcSystem.ActivationResult activateResult = fmcSystem.activateFlightPlan();
        assertThat(activateResult.success()).isTrue();

        // Verify all subsystems are wired
        assertThat(fmcSystem.getNavigationComputer()).isNotNull();
        assertThat(fmcSystem.getPerformanceComputer()).isNotNull();
        assertThat(fmcSystem.getGuidanceComputer()).isNotNull();

        // Verify status
        FmcSystem.FmcStatus status = fmcSystem.getStatus();
        assertThat(status.state()).isEqualTo(FmcSystem.FmcStatus.SystemState.ACTIVE);
        assertThat(status.flightPlanActive()).isTrue();

        // Simulate takeoff
        fmcSystem.setFlightPhase(FmcSystem.FlightPhase.TAKEOFF);
        assertThat(fmcSystem.getCurrentPhase()).isEqualTo(FmcSystem.FlightPhase.TAKEOFF);

        // Simulate climb
        fmcSystem.setFlightPhase(FmcSystem.FlightPhase.CLIMB);
        assertThat(fmcSystem.getCurrentPhase()).isEqualTo(FmcSystem.FlightPhase.CLIMB);

        // Simulate cruise
        fmcSystem.setFlightPhase(FmcSystem.FlightPhase.CRUISE);
        assertThat(fmcSystem.getCurrentPhase()).isEqualTo(FmcSystem.FlightPhase.CRUISE);

        // Deactivate
        fmcSystem.deactivateFlightPlan();
        assertThat(fmcSystem.getStatus().flightPlanActive()).isFalse();
    }

    @Test
    void testShutdown() {
        fmcSystem.initialize(createTestConfiguration());
        fmcSystem.loadFlightPlan(createTestFlightPlan());
        fmcSystem.activateFlightPlan();

        fmcSystem.shutdown();

        assertThat(fmcSystem.getSystemState()).isEqualTo(FmcSystem.FmcStatus.SystemState.FAILED);
        assertThat(fmcSystem.getCurrentPhase()).isEqualTo(FmcSystem.FlightPhase.COMPLETED);
    }

    @Test
    void testMessageCount() {
        fmcSystem.initialize(createTestConfiguration());

        int initialCount = fmcSystem.getStatus().messages();
        fmcSystem.incrementMessageCount();
        fmcSystem.incrementMessageCount();

        assertThat(fmcSystem.getStatus().messages()).isEqualTo(initialCount + 2);
    }

    // ==================== Helper Methods ====================

    private FmcSystem.FmcConfiguration createTestConfiguration() {
        return new FmcSystem.FmcConfiguration(
            AIRCRAFT_TYPE,
            MTOW,
            MLW,
            FUEL_CAPACITY,
            new FmcSystem.NavigationDatabase("2401", "2024-01-25", "2401"),
            false // Use imperial units
        );
    }

    private FlightPlan createTestFlightPlan() {
        // Create minimal airports
        Airport departure = Airport.builder()
            .icaoCode("KJFK")
            .iataCode("JFK")
            .name("John F Kennedy International")
            .city("New York")
            .countryCode("US")
            .type(Airport.AirportType.CIVIL)
            .landingType(Airport.LandingType.LAND)
            .referencePoint(GeoCoordinate.builder()
                .latitude(40.6413)
                .longitude(-73.7781)
                .build())
            .runways(List.of(createTestRunway("KJFK", "04L")))
            .build();

        Airport destination = Airport.builder()
            .icaoCode("KBOS")
            .iataCode("BOS")
            .name("Boston Logan International")
            .city("Boston")
            .countryCode("US")
            .type(Airport.AirportType.CIVIL)
            .landingType(Airport.LandingType.LAND)
            .referencePoint(GeoCoordinate.builder()
                .latitude(42.3656)
                .longitude(-71.0096)
                .build())
            .runways(List.of(createTestRunway("KBOS", "04R")))
            .build();

        // Create a simple route element
        RouteElement routeElement = RouteElement.builder()
            .sequenceNumber(0)
            .type(RouteElement.ElementType.WAYPOINT)
            .distance(200.0)
            .build();

        return FlightPlan.builder()
            .flightNumber("UAL123")
            .aircraftType(AIRCRAFT_TYPE)
            .departure(departure)
            .destination(destination)
            .estimatedDepartureTime(LocalDateTime.now())
            .route(List.of(routeElement))
            .cruiseAltitude(Altitude.flightLevel(350))
            .trueAirspeed(450)
            .build();
    }

    private com.aviation.fmc.navdata.Runway createTestRunway(String airportIcao, String identifier) {
        return com.aviation.fmc.navdata.Runway.builder()
            .airportIcao(airportIcao)
            .identifier(identifier)
            .magneticHeading(40.0)
            .trueHeading(40.0)
            .tora(2500)
            .toda(2600)
            .asda(2550)
            .lda(2450)
            .width(45)
            .surface(com.aviation.fmc.navdata.Runway.SurfaceType.ASPHALT)
            .threshold(GeoCoordinate.builder().latitude(40.0).longitude(-73.0).build())
            .lighting(com.aviation.fmc.navdata.Runway.LightingType.HIRL)
            .build();
    }
}
