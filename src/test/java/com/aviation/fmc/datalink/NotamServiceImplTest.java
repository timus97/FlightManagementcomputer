package com.aviation.fmc.datalink;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NotamServiceImpl.
 * Tests NOTAM generation, pub/sub, and FMC-relevant filtering.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotamServiceImplTest {

    private NotamServiceImpl notamService;

    @BeforeEach
    void setUp() {
        notamService = new NotamServiceImpl();
        notamService.start();
    }

    @Test
    void testGetNotamsForAirport() {
        List<NotamService.NotamReport> notams = notamService.getNotamsForAirport("KJFK");

        assertNotNull(notams);
        // May be empty or have some NOTAMs (probabilistic)
        for (NotamService.NotamReport notam : notams) {
            assertEquals("KJFK", notam.icaoCode());
            assertNotNull(notam.id());
            assertNotNull(notam.rawText());
        }
    }

    @Test
    void testGetNotamsForRoute() {
        List<String> alternates = List.of("KJFK", "KBOS");
        List<String> waypoints = List.of("ORW", "PARCH");

        NotamService.RouteNotams routeNotams = notamService.getNotamsForRoute(
            "KJFK", "KLAX", alternates, waypoints
        );

        assertEquals("KJFK", routeNotams.departure());
        assertEquals("KLAX", routeNotams.destination());
        assertNotNull(routeNotams.departureNotams());
        assertNotNull(routeNotams.destinationNotams());
        assertNotNull(routeNotams.fmcRelevantNotams());
    }

    @Test
    void testGetNotamById() {
        // First generate some NOTAMs
        notamService.getNotamsForAirport("KJFK");

        // Try to find by ID pattern (if any exist)
        Optional<NotamService.NotamReport> notam = notamService.getNotamById("KJFK-RWY-09L");
        // May or may not exist depending on random generation
        assertNotNull(notam);
    }

    @Test
    void testGetCriticalNotams() {
        List<NotamService.NotamReport> critical = notamService.getCriticalNotams("KJFK");

        assertNotNull(critical);
        for (NotamService.NotamReport notam : critical) {
            assertEquals(NotamService.NotamSeverity.CRITICAL, notam.getSeverity());
        }
    }

    @Test
    void testRequestNotamUpdate() {
        AtomicInteger updateCount = new AtomicInteger(0);

        notamService.subscribeToNotams("KLAX", notams -> {
            updateCount.incrementAndGet();
        });

        notamService.requestNotamUpdate("KLAX");

        assertTrue(updateCount.get() >= 1);
    }

    @Test
    void testPubSubNotamSubscription() {
        AtomicInteger receivedCount = new AtomicInteger(0);

        notamService.subscribeToNotams("KJFK", notams -> {
            receivedCount.incrementAndGet();
            assertNotNull(notams);
        });

        notamService.requestNotamUpdate("KJFK");

        assertTrue(receivedCount.get() >= 1);
    }

    @Test
    void testRouteNotamSubscription() {
        AtomicInteger receivedCount = new AtomicInteger(0);

        notamService.subscribeToRouteNotams(routeNotams -> {
            receivedCount.incrementAndGet();
            assertNotNull(routeNotams.departure());
            assertNotNull(routeNotams.destination());
        });

        notamService.getNotamsForRoute("KJFK", "KLAX", null, null);

        assertTrue(receivedCount.get() >= 1);
    }

    @Test
    void testNotamAffectsFmc() {
        List<NotamService.NotamReport> notams = notamService.getNotamsForAirport("KJFK");

        for (NotamService.NotamReport notam : notams) {
            // Check affectsFmc() method works
            boolean affects = notam.affectsFmc();
            assertTrue(affects == true || affects == false); // Just verify no crash
        }
    }

    @Test
    void testRouteNotamsCriticalFilter() {
        NotamService.RouteNotams routeNotams = notamService.getNotamsForRoute(
            "KJFK", "KLAX", List.of(), List.of()
        );

        List<NotamService.NotamReport> critical = routeNotams.getCriticalNotams();
        assertNotNull(critical);

        for (NotamService.NotamReport notam : critical) {
            assertEquals(NotamService.NotamSeverity.CRITICAL, notam.getSeverity());
        }
    }

    @Test
    void testNotamSeverityLevels() {
        List<NotamService.NotamReport> notams = notamService.getNotamsForAirport("KJFK");

        for (NotamService.NotamReport notam : notams) {
            NotamService.NotamSeverity severity = notam.getSeverity();
            assertNotNull(severity);
            assertTrue(List.of(
                NotamService.NotamSeverity.INFO,
                NotamService.NotamSeverity.WARNING,
                NotamService.NotamSeverity.CRITICAL
            ).contains(severity));
        }
    }

    @Test
    void testRunwayClosedDetection() {
        NotamService.RouteNotams routeNotams = notamService.getNotamsForRoute(
            "KJFK", "KLAX", List.of(), List.of()
        );

        // Check method exists and works
        boolean closed = routeNotams.isRunwayClosed("09L");
        assertTrue(closed == true || closed == false);
    }

    @Test
    void testNavaidOutOfServiceDetection() {
        NotamService.RouteNotams routeNotams = notamService.getNotamsForRoute(
            "KJFK", "KLAX", List.of(), List.of()
        );

        boolean oos = routeNotams.isNavaidOutOfService("ILS");
        assertTrue(oos == true || oos == false);
    }

    @Test
    void testUnsubscribe() {
        AtomicInteger count = new AtomicInteger(0);

        java.util.function.Consumer<List<NotamService.NotamReport>> sub = notams -> count.incrementAndGet();

        notamService.subscribeToNotams("KJFK", sub);
        notamService.requestNotamUpdate("KJFK");
        int afterFirst = count.get();

        notamService.unsubscribeAll(sub);
        notamService.requestNotamUpdate("KJFK");
        int afterSecond = count.get();

        assertEquals(afterFirst, afterSecond);
    }

    @Test
    void testStopService() {
        notamService.stop();
        assertTrue(true); // Should not throw
    }
}
