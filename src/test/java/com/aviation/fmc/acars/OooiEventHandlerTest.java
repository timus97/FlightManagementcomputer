package com.aviation.fmc.acars;

import com.aviation.fmc.common.GeoCoordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for OooiEventHandler.
 */
class OooiEventHandlerTest {

    private OooiEventHandler handler;
    private static final String AC_REGISTRATION = "N123AA";

    @BeforeEach
    void setUp() {
        handler = new OooiEventHandler();
    }

    @Test
    void testProcessOutEvent() {
        Instant now = Instant.now();
        GeoCoordinate position = GeoCoordinate.builder()
                .latitude(40.6413)
                .longitude(-73.7781)
                .build();

        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OUT,
                position, 0.0, now);

        var session = handler.getActiveFlight(AC_REGISTRATION);
        assertThat(session).isPresent();
        assertThat(session.get().getCurrentPhase()).isEqualTo(OooiEventHandler.OooiEventType.OUT);
        assertThat(session.get().getAircraftRegistration()).isEqualTo(AC_REGISTRATION);
    }

    @Test
    void testCompleteFlightSequence() {
        Instant baseTime = Instant.now();

        // OUT event
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OUT,
                position(40.6413, -73.7781), 0.0, baseTime);

        // OFF event (8 minutes later)
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OFF,
                position(40.6500, -73.7600), 150.0, baseTime.plusSeconds(480));

        // ON event (2 hours later)
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.ON,
                position(41.9742, -87.9073), 0.0, baseTime.plusSeconds(7200));

        // IN event (10 minutes later - flight complete)
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.IN,
                position(41.9800, -87.9000), 0.0, baseTime.plusSeconds(7800));

        // Flight should no longer be active
        assertThat(handler.getActiveFlight(AC_REGISTRATION)).isEmpty();

        // Check completed flights
        List<OooiEventHandler.FlightSession> completed = handler.getCompletedFlights(AC_REGISTRATION);
        assertThat(completed).hasSize(1);

        OooiEventHandler.FlightSummary summary = completed.get(0).generateSummary();
        assertThat(summary).isNotNull();
        assertThat(summary.getBlockTime()).isEqualTo(Duration.ofSeconds(7800));
        assertThat(summary.getFlightTime()).isEqualTo(Duration.ofSeconds(6720)); // 7200 - 480
        assertThat(summary.getTaxiOutTime()).isEqualTo(Duration.ofSeconds(480));
        assertThat(summary.getTaxiInTime()).isEqualTo(Duration.ofSeconds(600));
        assertThat(summary.isComplete()).isTrue();
    }

    @Test
    void testListenerNotification() {
        List<String> eventsReceived = new ArrayList<>();
        List<OooiEventHandler.FlightSummary> completedSummaries = new ArrayList<>();

        OooiEventHandler.OooiEventListener listener = new OooiEventHandler.OooiEventListener() {
            @Override
            public void onOooiEvent(String aircraftRegistration,
                                     OooiEventHandler.OooiEvent event,
                                     OooiEventHandler.FlightSession session) {
                eventsReceived.add(event.getType().name());
            }

            @Override
            public void onFlightComplete(String aircraftRegistration,
                                          OooiEventHandler.FlightSession session,
                                          OooiEventHandler.FlightSummary summary) {
                completedSummaries.add(summary);
            }
        };

        handler.addListener(listener);

        Instant now = Instant.now();
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OUT,
                position(40.6413, -73.7781), 0.0, now);
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OFF,
                position(40.6500, -73.7600), 150.0, now.plusSeconds(480));
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.ON,
                position(41.9742, -87.9073), 0.0, now.plusSeconds(7200));
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.IN,
                position(41.9800, -87.9000), 0.0, now.plusSeconds(7800));

        assertThat(eventsReceived).containsExactly("OUT", "OFF", "ON", "IN");
        assertThat(completedSummaries).hasSize(1);
        assertThat(completedSummaries.get(0).getAircraftRegistration()).isEqualTo(AC_REGISTRATION);
    }

    @Test
    void testRemoveListener() {
        AtomicInteger count = new AtomicInteger(0);

        OooiEventHandler.OooiEventListener listener = new OooiEventHandler.OooiEventListener() {
            @Override
            public void onOooiEvent(String aircraftRegistration,
                                     OooiEventHandler.OooiEvent event,
                                     OooiEventHandler.FlightSession session) {
                count.incrementAndGet();
            }

            @Override
            public void onFlightComplete(String aircraftRegistration,
                                          OooiEventHandler.FlightSession session,
                                          OooiEventHandler.FlightSummary summary) {
            }
        };

        handler.addListener(listener);
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OUT,
                position(40.6413, -73.7781), 0.0, Instant.now());

        assertThat(count.get()).isEqualTo(1);

        handler.removeListener(listener);
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OFF,
                position(40.6500, -73.7600), 150.0, Instant.now());

        assertThat(count.get()).isEqualTo(1); // No additional increment
    }

    @Test
    void testGetAllActiveFlights() {
        Instant now = Instant.now();

        // Create flights for multiple aircraft
        handler.processOooiEvent("N123AA", OooiEventHandler.OooiEventType.OUT,
                position(40.6413, -73.7781), 0.0, now);
        handler.processOooiEvent("N456UA", OooiEventHandler.OooiEventType.OUT,
                position(41.9742, -87.9073), 0.0, now);
        handler.processOooiEvent("N789DL", OooiEventHandler.OooiEventType.OFF,
                position(33.9425, -118.4081), 150.0, now);

        List<OooiEventHandler.FlightSession> activeFlights = handler.getAllActiveFlights();

        assertThat(activeFlights).hasSize(3);
        assertThat(activeFlights)
                .extracting(OooiEventHandler.FlightSession::getAircraftRegistration)
                .containsExactlyInAnyOrder("N123AA", "N456UA", "N789DL");
    }

    @Test
    void testGenerateSummaryForActiveFlight() {
        Instant now = Instant.now();

        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OUT,
                position(40.6413, -73.7781), 0.0, now);
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OFF,
                position(40.6500, -73.7600), 150.0, now.plusSeconds(600));

        OooiEventHandler.FlightSummary summary = handler.generateSummary(AC_REGISTRATION);

        assertThat(summary).isNotNull();
        assertThat(summary.getAircraftRegistration()).isEqualTo(AC_REGISTRATION);
        assertThat(summary.getTaxiOutTime()).isEqualTo(Duration.ofSeconds(600));
        assertThat(summary.isComplete()).isFalse();
    }

    @Test
    void testGenerateSummaryForNonExistentFlight() {
        OooiEventHandler.FlightSummary summary = handler.generateSummary("NONEXISTENT");
        assertThat(summary).isNull();
    }

    @Test
    void testClearHistory() {
        Instant now = Instant.now();

        // Complete a flight
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OUT,
                position(40.6413, -73.7781), 0.0, now);
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.IN,
                position(41.9742, -87.9073), 0.0, now.plusSeconds(3600));

        assertThat(handler.getCompletedFlights(AC_REGISTRATION)).hasSize(1);

        handler.clearHistory(AC_REGISTRATION);

        assertThat(handler.getCompletedFlights(AC_REGISTRATION)).isEmpty();
    }

    @Test
    void testFlightSessionGetEvents() {
        Instant now = Instant.now();

        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OUT,
                position(40.6413, -73.7781), 0.0, now);
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OFF,
                position(40.6500, -73.7600), 150.0, now.plusSeconds(600));

        var session = handler.getActiveFlight(AC_REGISTRATION).orElseThrow();
        var events = session.getEvents();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getType()).isEqualTo(OooiEventHandler.OooiEventType.OUT);
        assertThat(events.get(1).getType()).isEqualTo(OooiEventHandler.OooiEventType.OFF);
    }

    @Test
    void testFlightSessionGetLastEvent() {
        Instant now = Instant.now();

        var session = handler.getActiveFlight(AC_REGISTRATION).orElse(null);
        assertThat(session).isNull();

        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OUT,
                position(40.6413, -73.7781), 0.0, now);

        session = handler.getActiveFlight(AC_REGISTRATION).orElseThrow();
        assertThat(session.getLastEvent()).isPresent();
        assertThat(session.getLastEvent().get().getType()).isEqualTo(OooiEventHandler.OooiEventType.OUT);
    }

    @Test
    void testMultipleFlightsSameAircraft() {
        Instant now = Instant.now();

        // First flight
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OUT,
                position(40.6413, -73.7781), 0.0, now);
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.IN,
                position(41.9742, -87.9073), 0.0, now.plusSeconds(3600));

        // Second flight
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OUT,
                position(41.9742, -87.9073), 0.0, now.plusSeconds(7200));
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.IN,
                position(40.6413, -73.7781), 0.0, now.plusSeconds(10800));

        List<OooiEventHandler.FlightSession> completed = handler.getCompletedFlights(AC_REGISTRATION);
        assertThat(completed).hasSize(2);
    }

    @Test
    void testProcessAcarsMessage() {
        String messageText = "OUT 40.6413/-73.7781 ALT0 1430";

        AcarsMessage message = AcarsMessage.builder()
                .direction(AcarsMessage.Direction.DOWNLINK)
                .label(AcarsMessage.Label.OOOI_EVENT)
                .aircraftRegistration(AC_REGISTRATION)
                .messageText(messageText)
                .createdAt(Instant.now())
                .build();

        handler.processAcarsMessage(message);

        var session = handler.getActiveFlight(AC_REGISTRATION);
        assertThat(session).isPresent();
        assertThat(session.get().getCurrentPhase()).isEqualTo(OooiEventHandler.OooiEventType.OUT);
    }

    @Test
    void testProcessNonOooiMessageIgnored() {
        AcarsMessage message = AcarsMessage.builder()
                .direction(AcarsMessage.Direction.DOWNLINK)
                .label(AcarsMessage.Label.FREE_TEXT)
                .aircraftRegistration(AC_REGISTRATION)
                .messageText("Test message")
                .createdAt(Instant.now())
                .build();

        handler.processAcarsMessage(message);

        assertThat(handler.getActiveFlight(AC_REGISTRATION)).isEmpty();
    }

    @Test
    void testInvalidOooiMessageFormat() {
        AcarsMessage message = AcarsMessage.builder()
                .direction(AcarsMessage.Direction.DOWNLINK)
                .label(AcarsMessage.Label.OOOI_EVENT)
                .aircraftRegistration(AC_REGISTRATION)
                .messageText("INVALID MESSAGE")
                .createdAt(Instant.now())
                .build();

        // Should not throw, just log error
        assertThatNoException().isThrownBy(() -> handler.processAcarsMessage(message));
        assertThat(handler.getActiveFlight(AC_REGISTRATION)).isEmpty();
    }

    @Test
    void testFlightSummaryCalculations() {
        Instant baseTime = Instant.now();

        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OUT,
                position(40.6413, -73.7781), 0.0, baseTime);
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.OFF,
                position(40.6500, -73.7600), 1000.0, baseTime.plusSeconds(600)); // 10 min taxi out
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.ON,
                position(41.9742, -87.9073), 500.0, baseTime.plusSeconds(7200)); // 110 min flight
        handler.processOooiEvent(AC_REGISTRATION, OooiEventHandler.OooiEventType.IN,
                position(41.9800, -87.9000), 0.0, baseTime.plusSeconds(7800)); // 10 min taxi in

        List<OooiEventHandler.FlightSession> completed = handler.getCompletedFlights(AC_REGISTRATION);
        OooiEventHandler.FlightSummary summary = completed.get(0).generateSummary();

        // Verify helper methods
        assertThat(summary.getBlockTimeMinutes()).isEqualTo(130); // 7800 seconds = 130 minutes
        assertThat(summary.getFlightTimeMinutes()).isEqualTo(110); // 6600 seconds = 110 minutes

        // Verify location strings
        assertThat(summary.getDepartureLocation()).isEqualTo("40.6413/-73.7781");
        assertThat(summary.getArrivalLocation()).isEqualTo("41.9800/-87.9000");
    }

    // ==================== Helper Methods ====================

    private GeoCoordinate position(double lat, double lon) {
        return GeoCoordinate.builder()
                .latitude(lat)
                .longitude(lon)
                .build();
    }
}
