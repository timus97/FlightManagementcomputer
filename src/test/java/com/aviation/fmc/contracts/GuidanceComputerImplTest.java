package com.aviation.fmc.contracts;

import com.aviation.fmc.common.GeoCoordinate;
import com.aviation.fmc.navdata.Waypoint;
import com.aviation.fmc.parser.FlightPlan;
import com.aviation.fmc.parser.RouteElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for GuidanceComputerImpl.
 */
class GuidanceComputerImplTest {

    private GuidanceComputerImpl guidanceComputer;
    private NavigationComputerImpl navigationComputer;

    private static final double CURRENT_ALTITUDE = 10000.0;
    private static final double CURRENT_SPEED = 250.0;
    private static final double CURRENT_HEADING = 90.0;

    @BeforeEach
    void setUp() {
        navigationComputer = new NavigationComputerImpl();
        guidanceComputer = new GuidanceComputerImpl(navigationComputer);
        guidanceComputer.updateState(CURRENT_ALTITUDE, CURRENT_SPEED, 0.0, CURRENT_HEADING);
    }

    @Test
    void testSetLnavEnabled() {
        guidanceComputer.setLnavEnabled(true);
        assertThat(guidanceComputer.isLnavActive()).isFalse(); // No flight plan yet

        loadSimpleFlightPlan();
        assertThat(guidanceComputer.isLnavActive()).isTrue();
    }

    @Test
    void testSetLnavEnabledWithoutNavigationComputer() {
        GuidanceComputerImpl standaloneGuidance = new GuidanceComputerImpl();
        standaloneGuidance.setLnavEnabled(true);
        assertThat(standaloneGuidance.isLnavActive()).isFalse();
    }

    @Test
    void testSetVnavEnabled() {
        guidanceComputer.setVnavEnabled(true);
        assertThat(guidanceComputer.isVnavActive()).isTrue();

        guidanceComputer.setVnavEnabled(false);
        assertThat(guidanceComputer.isVnavActive()).isFalse();
    }

    @Test
    void testIsLnavActiveWithoutFlightPlan() {
        guidanceComputer.setLnavEnabled(true);
        assertThat(guidanceComputer.isLnavActive()).isFalse();
    }

    @Test
    void testGetLateralCommandWithoutLnav() {
        guidanceComputer.setLnavEnabled(false);

        GuidanceComputer.LateralCommand command = guidanceComputer.getLateralCommand();

        assertThat(command.type()).isEqualTo(GuidanceComputer.LateralCommand.CommandType.HEADING);
        assertThat(command.targetHeading()).isEqualTo(CURRENT_HEADING);
        assertThat(command.crossTrackError()).isEqualTo(0.0);
        assertThat(command.rollCommand()).isEqualTo(0.0);
    }

    @Test
    void testGetLateralCommandWithLnav() {
        loadSimpleFlightPlan();
        updatePositionNearFirstWaypoint();

        guidanceComputer.setLnavEnabled(true);

        GuidanceComputer.LateralCommand command = guidanceComputer.getLateralCommand();

        assertThat(command.type()).isEqualTo(GuidanceComputer.LateralCommand.CommandType.LNAV);
        assertThat(command.crossTrackError()).isNotNull();
        assertThat(command.trackAngleError()).isNotNull();
    }

    @Test
    void testGetLateralCommandRollLimits() {
        loadSimpleFlightPlan();
        updatePositionNearFirstWaypoint();

        guidanceComputer.setLnavEnabled(true);

        GuidanceComputer.LateralCommand command = guidanceComputer.getLateralCommand();

        // Roll command should be limited to max bank angle
        assertThat(Math.abs(command.desiredBankAngle())).isLessThanOrEqualTo(25.0);
    }

    @Test
    void testGetVerticalCommandWithoutVnav() {
        guidanceComputer.setVnavEnabled(false);
        guidanceComputer.setTargetAltitude(15000.0);

        GuidanceComputer.VerticalCommand command = guidanceComputer.getVerticalCommand();

        assertThat(command.type()).isEqualTo(GuidanceComputer.VerticalCommand.CommandType.ALTITUDE);
        assertThat(command.targetAltitude()).isEqualTo(15000.0);
    }

    @Test
    void testGetVerticalCommandWithVnavAltitudeHold() {
        guidanceComputer.setVnavEnabled(true);
        guidanceComputer.setTargetAltitude(CURRENT_ALTITUDE);

        GuidanceComputer.VerticalCommand command = guidanceComputer.getVerticalCommand();

        assertThat(command.type()).isEqualTo(GuidanceComputer.VerticalCommand.CommandType.ALTITUDE);
        assertThat(Math.abs(command.altitudeError())).isLessThan(100.0);
    }

    @Test
    void testGetVerticalCommandWithVnavClimb() {
        guidanceComputer.setVnavEnabled(true);
        guidanceComputer.setTargetAltitude(20000.0); // Climb

        GuidanceComputer.VerticalCommand command = guidanceComputer.getVerticalCommand();

        assertThat(command.type()).isIn(
            GuidanceComputer.VerticalCommand.CommandType.VNAV_SPEED,
            GuidanceComputer.VerticalCommand.CommandType.VNAV_PATH
        );
        assertThat(command.targetVerticalSpeed()).isGreaterThan(0.0);
    }

    @Test
    void testGetVerticalCommandWithVnavDescent() {
        guidanceComputer.setVnavEnabled(true);
        guidanceComputer.setTargetAltitude(5000.0); // Descent

        GuidanceComputer.VerticalCommand command = guidanceComputer.getVerticalCommand();

        assertThat(command.type()).isIn(
            GuidanceComputer.VerticalCommand.CommandType.VNAV_SPEED,
            GuidanceComputer.VerticalCommand.CommandType.VNAV_PATH
        );
        assertThat(command.targetVerticalSpeed()).isLessThan(0.0);
    }

    @Test
    void testGetVerticalCommandWithVerticalSpeed() {
        guidanceComputer.setVnavEnabled(true);
        guidanceComputer.setTargetAltitude(20000.0);
        guidanceComputer.setVerticalSpeed(1000.0); // Specific VS

        GuidanceComputer.VerticalCommand command = guidanceComputer.getVerticalCommand();

        assertThat(command.type()).isEqualTo(GuidanceComputer.VerticalCommand.CommandType.VERTICAL_SPEED);
        assertThat(command.targetVerticalSpeed()).isEqualTo(1000.0);
    }

    @Test
    void testSetTargetAltitude() {
        guidanceComputer.setTargetAltitude(25000.0);

        GuidanceComputer.VerticalCommand command = guidanceComputer.getVerticalCommand();
        assertThat(command.targetAltitude()).isEqualTo(25000.0);
    }

    @Test
    void testSetTargetSpeedKnots() {
        guidanceComputer.setTargetSpeed(280.0, false);

        GuidanceComputer.VerticalCommand command = guidanceComputer.getVerticalCommand();
        assertThat(command.targetSpeed()).isEqualTo(280.0);
        assertThat(command.isMach()).isFalse();
    }

    @Test
    void testSetTargetSpeedMach() {
        guidanceComputer.setTargetSpeed(78.0, true); // M0.78

        GuidanceComputer.VerticalCommand command = guidanceComputer.getVerticalCommand();
        assertThat(command.targetSpeed()).isEqualTo(78.0);
        assertThat(command.isMach()).isTrue();
    }

    @Test
    void testArmApproach() {
        boolean result = guidanceComputer.armApproach("ILS");
        assertThat(result).isTrue();

        GuidanceComputer.GuidanceStatus status = guidanceComputer.getGuidanceStatus();
        assertThat(status.approachArmed()).isTrue();
        assertThat(status.approachActive()).isFalse();
    }

    @Test
    void testArmApproachInvalidType() {
        boolean result = guidanceComputer.armApproach("");
        assertThat(result).isFalse();

        result = guidanceComputer.armApproach(null);
        assertThat(result).isFalse();
    }

    @Test
    void testDisengageApproach() {
        guidanceComputer.armApproach("ILS");
        guidanceComputer.disengageApproach();

        assertThat(guidanceComputer.isApproachActive()).isFalse();

        GuidanceComputer.GuidanceStatus status = guidanceComputer.getGuidanceStatus();
        assertThat(status.approachArmed()).isFalse();
    }

    @Test
    void testIsApproachActive() {
        assertThat(guidanceComputer.isApproachActive()).isFalse();

        guidanceComputer.armApproach("ILS");
        assertThat(guidanceComputer.isApproachActive()).isFalse(); // Armed but not active
    }

    @Test
    void testSetFlapsPosition() {
        guidanceComputer.setFlapsPosition(15);
        // No direct assertion, but should not throw
    }

    @Test
    void testSetGearDown() {
        guidanceComputer.setGearDown(true);
        // No direct assertion, but should not throw
    }

    @Test
    void testGetFlightPathAngle() {
        // Need to set up navigation computer with position for FPA calculation
        GeoCoordinate currentPos = GeoCoordinate.builder()
            .latitude(41.9)
            .longitude(-87.8)
            .build();
        navigationComputer.updatePosition(currentPos, 10000.0, 90.0, 250.0);

        // Level flight
        guidanceComputer.updateState(10000.0, 250.0, 0.0, 90.0);
        double fpa = guidanceComputer.getFlightPathAngle();
        assertThat(fpa).isEqualTo(0.0);

        // Climbing at 1000 fpm at 250 kts groundspeed
        guidanceComputer.updateState(10000.0, 250.0, 1000.0, 90.0);
        fpa = guidanceComputer.getFlightPathAngle();
        assertThat(fpa).isGreaterThan(0.0);

        // Descending at 1000 fpm at 250 kts groundspeed
        guidanceComputer.updateState(10000.0, 250.0, -1000.0, 90.0);
        fpa = guidanceComputer.getFlightPathAngle();
        assertThat(fpa).isLessThan(0.0);
    }

    @Test
    void testGetFlightPathAngleWithoutNavigationComputer() {
        GuidanceComputerImpl standalone = new GuidanceComputerImpl();
        double fpa = standalone.getFlightPathAngle();
        assertThat(fpa).isEqualTo(0.0);
    }

    @Test
    void testGetRequiredFlightPathAngle() {
        // Level flight target
        guidanceComputer.setTargetAltitude(CURRENT_ALTITUDE);
        double requiredFpa = guidanceComputer.getRequiredFlightPathAngle();
        assertThat(requiredFpa).isEqualTo(0.0);

        // Climb required
        guidanceComputer.setTargetAltitude(CURRENT_ALTITUDE + 5000.0);
        requiredFpa = guidanceComputer.getRequiredFlightPathAngle();
        assertThat(requiredFpa).isGreaterThan(0.0);

        // Descent required
        guidanceComputer.setTargetAltitude(CURRENT_ALTITUDE - 5000.0);
        requiredFpa = guidanceComputer.getRequiredFlightPathAngle();
        assertThat(requiredFpa).isLessThan(0.0);
    }

    @Test
    void testUpdateState() {
        guidanceComputer.updateState(15000.0, 300.0, 500.0, 180.0);

        GuidanceComputer.LateralCommand lateralCmd = guidanceComputer.getLateralCommand();
        assertThat(lateralCmd.targetHeading()).isEqualTo(180.0);
    }

    @Test
    void testSetNavigationComputer() {
        NavigationComputerImpl newNavComputer = new NavigationComputerImpl();
        guidanceComputer.setNavigationComputer(newNavComputer);

        // Should use the new navigation computer
        guidanceComputer.setLnavEnabled(true);
        assertThat(guidanceComputer.isLnavActive()).isFalse(); // No flight plan in new nav computer
    }

    @Test
    void testGetGuidanceStatus() {
        GuidanceComputer.GuidanceStatus status = guidanceComputer.getGuidanceStatus();

        assertThat(status).isNotNull();
        assertThat(status.lnavActive()).isFalse();
        assertThat(status.vnavActive()).isFalse();
        assertThat(status.altHold()).isTrue(); // Default when VNAV off
        assertThat(status.speedHold()).isTrue();
    }

    @Test
    void testGetGuidanceStatusWithModesActive() {
        loadSimpleFlightPlan();
        guidanceComputer.setLnavEnabled(true);
        guidanceComputer.setVnavEnabled(true);

        GuidanceComputer.GuidanceStatus status = guidanceComputer.getGuidanceStatus();

        assertThat(status.lnavActive()).isTrue();
        assertThat(status.vnavActive()).isTrue();
    }

    @Test
    void testPitchLimits() {
        guidanceComputer.setVnavEnabled(true);
        guidanceComputer.setTargetAltitude(CURRENT_ALTITUDE + 50000.0); // Very high target

        GuidanceComputer.VerticalCommand command = guidanceComputer.getVerticalCommand();

        // Pitch should be limited
        assertThat(Math.abs(command.pitchCommand())).isLessThanOrEqualTo(10.0);
    }

    @Test
    void testThrottleLimits() {
        guidanceComputer.setVnavEnabled(true);
        guidanceComputer.setTargetSpeed(500.0, false); // Very high speed target

        GuidanceComputer.VerticalCommand command = guidanceComputer.getVerticalCommand();

        // Throttle should be limited to 95%
        assertThat(command.throttleCommand()).isLessThanOrEqualTo(95.0);
    }

    @Test
    void testThrottleMinimum() {
        guidanceComputer.setVnavEnabled(true);
        guidanceComputer.setTargetSpeed(50.0, false); // Very low speed target

        GuidanceComputer.VerticalCommand command = guidanceComputer.getVerticalCommand();

        // Throttle should be at least 20%
        assertThat(command.throttleCommand()).isGreaterThanOrEqualTo(20.0);
    }

    // ==================== Helper Methods ====================

    private void loadSimpleFlightPlan() {
        GeoCoordinate ordCoordinate = GeoCoordinate.builder()
            .latitude(41.9742)
            .longitude(-87.9073)
            .build();
        GeoCoordinate denCoordinate = GeoCoordinate.builder()
            .latitude(39.8561)
            .longitude(-104.6737)
            .build();

        Waypoint ord = Waypoint.builder()
            .identifier("ORD")
            .coordinate(ordCoordinate)
            .type(Waypoint.WaypointType.ENROUTE_WAYPOINT)
            .build();

        Waypoint den = Waypoint.builder()
            .identifier("DEN")
            .coordinate(denCoordinate)
            .type(Waypoint.WaypointType.ENROUTE_WAYPOINT)
            .build();

        FlightPlan flightPlan = FlightPlan.builder()
            .route(Arrays.asList(
                RouteElement.builder()
                    .sequenceNumber(0)
                    .type(RouteElement.ElementType.WAYPOINT)
                    .waypoint(ord)
                    .distance(100.0)
                    .estimatedTimeMinutes(15.0)
                    .build(),
                RouteElement.builder()
                    .sequenceNumber(1)
                    .type(RouteElement.ElementType.WAYPOINT)
                    .waypoint(den)
                    .distance(150.0)
                    .estimatedTimeMinutes(20.0)
                    .build()
            ))
            .build();

        navigationComputer.loadFlightPlan(flightPlan);
    }

    private void updatePositionNearFirstWaypoint() {
        GeoCoordinate nearOrd = GeoCoordinate.builder()
            .latitude(41.9)
            .longitude(-87.8)
            .build();
        navigationComputer.updatePosition(nearOrd, CURRENT_ALTITUDE, CURRENT_HEADING, CURRENT_SPEED);
    }
}
