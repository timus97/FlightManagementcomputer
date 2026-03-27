package com.aviation.fmc.contracts;

import com.aviation.fmc.common.GeoCoordinate;
import com.aviation.fmc.common.MagneticVariation;
import com.aviation.fmc.navdata.Waypoint;
import com.aviation.fmc.parser.FlightPlan;
import com.aviation.fmc.parser.RouteElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for NavigationComputerImpl.
 */
class NavigationComputerImplTest {

    private NavigationComputerImpl navigationComputer;
    private GeoCoordinate jfkCoordinate;
    private GeoCoordinate ordCoordinate;
    private GeoCoordinate denCoordinate;

    @BeforeEach
    void setUp() {
        navigationComputer = new NavigationComputerImpl();
        jfkCoordinate = GeoCoordinate.builder()
            .latitude(40.6413)
            .longitude(-73.7781)
            .build();
        ordCoordinate = GeoCoordinate.builder()
            .latitude(41.9742)
            .longitude(-87.9073)
            .build();
        denCoordinate = GeoCoordinate.builder()
            .latitude(39.8561)
            .longitude(-104.6737)
            .build();
    }

    @Test
    void testUpdatePosition() {
        navigationComputer.updatePosition(jfkCoordinate, 1000.0, 90.0, 250.0);

        Optional<NavigationComputer.Position> position = navigationComputer.getCurrentPosition();
        assertThat(position).isPresent();
        assertThat(position.get().coordinate()).isEqualTo(jfkCoordinate);
        assertThat(position.get().altitude()).isEqualTo(1000.0);
        assertThat(position.get().heading()).isEqualTo(90.0);
        assertThat(position.get().groundspeed()).isEqualTo(250.0);
    }

    @Test
    void testGetCurrentPositionEmpty() {
        Optional<NavigationComputer.Position> position = navigationComputer.getCurrentPosition();
        assertThat(position).isEmpty();
    }

    @Test
    void testLoadFlightPlan() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        List<Waypoint> upcoming = navigationComputer.getUpcomingWaypoints();
        assertThat(upcoming).hasSize(2);
        assertThat(upcoming.get(0).getIdentifier()).isEqualTo("ORD");
        assertThat(upcoming.get(1).getIdentifier()).isEqualTo("DEN");
    }

    @Test
    void testGetActiveWaypoint() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        Optional<Waypoint> active = navigationComputer.getActiveWaypoint();
        assertThat(active).isPresent();
        assertThat(active.get().getIdentifier()).isEqualTo("ORD");
    }

    @Test
    void testGetActiveWaypointEmpty() {
        Optional<Waypoint> active = navigationComputer.getActiveWaypoint();
        assertThat(active).isEmpty();
    }

    @Test
    void testGetNextWaypoint() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        Optional<Waypoint> next = navigationComputer.getNextWaypoint();
        assertThat(next).isPresent();
        assertThat(next.get().getIdentifier()).isEqualTo("DEN");
    }

    @Test
    void testGetNextWaypointEmpty() {
        FlightPlan flightPlan = createSingleWaypointFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        Optional<Waypoint> next = navigationComputer.getNextWaypoint();
        assertThat(next).isEmpty();
    }

    @Test
    void testSequenceWaypoint() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        boolean hasMore = navigationComputer.sequenceWaypoint();
        assertThat(hasMore).isTrue();

        List<Waypoint> passed = navigationComputer.getPassedWaypoints();
        List<Waypoint> upcoming = navigationComputer.getUpcomingWaypoints();

        assertThat(passed).hasSize(1);
        assertThat(passed.get(0).getIdentifier()).isEqualTo("ORD");
        assertThat(upcoming).hasSize(1);
        assertThat(upcoming.get(0).getIdentifier()).isEqualTo("DEN");
    }

    @Test
    void testSequenceWaypointLast() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);
        navigationComputer.sequenceWaypoint(); // Sequence ORD

        boolean hasMore = navigationComputer.sequenceWaypoint(); // Sequence DEN
        assertThat(hasMore).isFalse();

        List<Waypoint> upcoming = navigationComputer.getUpcomingWaypoints();
        assertThat(upcoming).isEmpty();
    }

    @Test
    void testSequenceWaypointEmpty() {
        boolean result = navigationComputer.sequenceWaypoint();
        assertThat(result).isFalse();
    }

    @Test
    void testDistanceToActiveWaypoint() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);
        navigationComputer.updatePosition(jfkCoordinate, 1000.0, 90.0, 250.0);

        double distance = navigationComputer.distanceToActiveWaypoint();
        double expectedDistance = jfkCoordinate.distanceTo(ordCoordinate);

        assertThat(distance).isCloseTo(expectedDistance, within(0.1));
    }

    @Test
    void testDistanceToActiveWaypointNoPosition() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        double distance = navigationComputer.distanceToActiveWaypoint();
        assertThat(distance).isEqualTo(Double.MAX_VALUE);
    }

    @Test
    void testBearingToActiveWaypoint() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);
        navigationComputer.updatePosition(jfkCoordinate, 1000.0, 90.0, 250.0);

        double bearing = navigationComputer.bearingToActiveWaypoint();
        double expectedBearing = jfkCoordinate.bearingTo(ordCoordinate);

        assertThat(bearing).isCloseTo(expectedBearing, within(0.5));
    }

    @Test
    void testBearingToActiveWaypointWithMagneticVariation() {
        MagneticVariation variation = MagneticVariation.builder()
            .variationDegrees(-10.0)
            .annualChangeMinutes(0.0)
            .referenceYear(Year.of(2020))
            .build();
        navigationComputer.setMagneticVariation(variation);

        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);
        navigationComputer.updatePosition(jfkCoordinate, 1000.0, 90.0, 250.0);

        double bearing = navigationComputer.bearingToActiveWaypoint();
        double trueBearing = jfkCoordinate.bearingTo(ordCoordinate);
        double magneticBearing = variation.trueToMagnetic(trueBearing, Year.now());

        assertThat(bearing).isCloseTo(magneticBearing, within(0.5));
    }

    @Test
    void testGetCrossTrackError() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        // Position slightly off the direct track
        GeoCoordinate offTrack = GeoCoordinate.builder()
            .latitude(42.0)
            .longitude(-85.0)
            .build();
        navigationComputer.updatePosition(offTrack, 1000.0, 90.0, 250.0);

        double xtk = navigationComputer.getCrossTrackError();
        assertThat(Math.abs(xtk)).isGreaterThan(0.0);
    }

    @Test
    void testGetCrossTrackErrorNoPosition() {
        double xtk = navigationComputer.getCrossTrackError();
        assertThat(xtk).isEqualTo(0.0);
    }

    @Test
    void testGetRequiredTrack() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        double requiredTrack = navigationComputer.getRequiredTrack();
        double expectedTrack = ordCoordinate.bearingTo(denCoordinate);

        assertThat(requiredTrack).isCloseTo(expectedTrack, within(0.5));
    }

    @Test
    void testEstimateTimeToWaypoint() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);
        navigationComputer.updatePosition(jfkCoordinate, 1000.0, 90.0, 450.0);

        double distance = jfkCoordinate.distanceTo(ordCoordinate);
        double expectedTime = (distance / 450.0) * 60.0;

        double ete = navigationComputer.estimateTimeToWaypoint();
        assertThat(ete).isCloseTo(expectedTime, within(0.5));
    }

    @Test
    void testEstimateTimeToWaypointZeroSpeed() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);
        navigationComputer.updatePosition(jfkCoordinate, 1000.0, 90.0, 0.0);

        double ete = navigationComputer.estimateTimeToWaypoint();
        assertThat(ete).isEqualTo(Double.MAX_VALUE);
    }

    @Test
    void testGetRemainingDistance() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);
        navigationComputer.updatePosition(jfkCoordinate, 1000.0, 90.0, 250.0);

        double remainingDistance = navigationComputer.getRemainingDistance();
        double expectedDistance = jfkCoordinate.distanceTo(ordCoordinate) + 
                                  ordCoordinate.distanceTo(denCoordinate);

        assertThat(remainingDistance).isCloseTo(expectedDistance, within(0.5));
    }

    @Test
    void testDirectTo() {
        FlightPlan flightPlan = createMultiWaypointFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        boolean result = navigationComputer.directTo("DEN");
        assertThat(result).isTrue();

        Optional<Waypoint> active = navigationComputer.getActiveWaypoint();
        assertThat(active).isPresent();
        assertThat(active.get().getIdentifier()).isEqualTo("DEN");

        List<Waypoint> passed = navigationComputer.getPassedWaypoints();
        assertThat(passed).extracting(Waypoint::getIdentifier).contains("ORD");
    }

    @Test
    void testDirectToNotFound() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        boolean result = navigationComputer.directTo("XYZ");
        assertThat(result).isFalse();
    }

    @Test
    void testDirectToNoFlightPlan() {
        boolean result = navigationComputer.directTo("ORD");
        assertThat(result).isFalse();
    }

    @Test
    void testEnterHolding() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        boolean result = navigationComputer.enterHolding(null);
        assertThat(result).isTrue();
        assertThat(navigationComputer.isInHolding()).isTrue();
    }

    @Test
    void testEnterHoldingAtFix() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        boolean result = navigationComputer.enterHolding("DEN");
        assertThat(result).isTrue();
        assertThat(navigationComputer.isInHolding()).isTrue();
    }

    @Test
    void testEnterHoldingAtInvalidFix() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        boolean result = navigationComputer.enterHolding("XYZ");
        assertThat(result).isFalse();
        assertThat(navigationComputer.isInHolding()).isFalse();
    }

    @Test
    void testExitHolding() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);
        navigationComputer.enterHolding(null);

        boolean result = navigationComputer.exitHolding();
        assertThat(result).isTrue();
        assertThat(navigationComputer.isInHolding()).isFalse();
    }

    @Test
    void testExitHoldingNotInHolding() {
        boolean result = navigationComputer.exitHolding();
        assertThat(result).isFalse();
    }

    @Test
    void testInterceptCourseTo() {
        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);

        boolean result = navigationComputer.interceptCourseTo("DEN", 270.0);
        assertThat(result).isTrue();

        Optional<Waypoint> active = navigationComputer.getActiveWaypoint();
        assertThat(active).isPresent();
        assertThat(active.get().getIdentifier()).isEqualTo("DEN");
    }

    @Test
    void testGetNavigationStatus() {
        NavigationComputer.NavigationStatus status = navigationComputer.getNavigationStatus();
        assertThat(status.positionValid()).isFalse();
        assertThat(status.waypointActive()).isFalse();
        assertThat(status.navSource()).isEqualTo("NONE");

        FlightPlan flightPlan = createSimpleFlightPlan();
        navigationComputer.loadFlightPlan(flightPlan);
        navigationComputer.updatePosition(jfkCoordinate, 1000.0, 90.0, 250.0);

        status = navigationComputer.getNavigationStatus();
        assertThat(status.positionValid()).isTrue();
        assertThat(status.waypointActive()).isTrue();
        assertThat(status.navSource()).isEqualTo("GPS/DME");
        assertThat(status.accuracy()).isEqualTo(0.1);
        assertThat(status.satellites()).isEqualTo(8);
    }

    @Test
    void testGetPassedWaypointsEmpty() {
        List<Waypoint> passed = navigationComputer.getPassedWaypoints();
        assertThat(passed).isEmpty();
    }

    @Test
    void testGetUpcomingWaypointsEmpty() {
        List<Waypoint> upcoming = navigationComputer.getUpcomingWaypoints();
        assertThat(upcoming).isEmpty();
    }

    // ==================== Helper Methods ====================

    private FlightPlan createSimpleFlightPlan() {
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

        return FlightPlan.builder()
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
    }

    private FlightPlan createSingleWaypointFlightPlan() {
        Waypoint ord = Waypoint.builder()
            .identifier("ORD")
            .coordinate(ordCoordinate)
            .type(Waypoint.WaypointType.ENROUTE_WAYPOINT)
            .build();

        return FlightPlan.builder()
            .route(Collections.singletonList(
                RouteElement.builder()
                    .sequenceNumber(0)
                    .type(RouteElement.ElementType.WAYPOINT)
                    .waypoint(ord)
                    .distance(100.0)
                    .estimatedTimeMinutes(15.0)
                    .build()
            ))
            .build();
    }

    private FlightPlan createMultiWaypointFlightPlan() {
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

        Waypoint lax = Waypoint.builder()
            .identifier("LAX")
            .coordinate(GeoCoordinate.builder()
                .latitude(33.9416)
                .longitude(-118.4085)
                .build())
            .type(Waypoint.WaypointType.ENROUTE_WAYPOINT)
            .build();

        return FlightPlan.builder()
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
                    .build(),
                RouteElement.builder()
                    .sequenceNumber(2)
                    .type(RouteElement.ElementType.WAYPOINT)
                    .waypoint(lax)
                    .distance(200.0)
                    .estimatedTimeMinutes(25.0)
                    .build()
            ))
            .build();
    }
}
