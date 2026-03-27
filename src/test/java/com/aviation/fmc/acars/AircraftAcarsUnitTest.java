package com.aviation.fmc.acars;

import com.aviation.fmc.common.GeoCoordinate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Aircraft ACARS Unit.
 */
class AircraftAcarsUnitTest {

    private AircraftAcarsUnit acarsUnit;
    private AcarsGroundStation groundStation;

    @BeforeEach
    void setUp() {
        AircraftAcarsUnit.AircraftIdentity identity = AircraftAcarsUnit.AircraftIdentity.builder()
                .registration("N123AA")
                .icao24("A12345")
                .airlineCode("AAL")
                .aircraftType("B738")
                .build();

        acarsUnit = new AircraftAcarsUnit(identity);

        groundStation = new AcarsGroundStation(
                "TEST-GS", "Test Station",
                new AcarsGroundStation.GeoCoverage(40.0, -74.0, 100, "Test Area")
        );

        groundStation.start();
        acarsUnit.registerGroundStation(groundStation);
        acarsUnit.start();
    }

    @AfterEach
    void tearDown() {
        acarsUnit.stop();
        groundStation.stop();
    }

    @Test
    void testIdentity() {
        assertThat(acarsUnit.getIdentity().registration()).isEqualTo("N123AA");
        assertThat(acarsUnit.getIdentity().icao24()).isEqualTo("A12345");
        assertThat(acarsUnit.getIdentity().airlineCode()).isEqualTo("AAL");
        assertThat(acarsUnit.getIdentity().aircraftType()).isEqualTo("B738");
    }

    @Test
    void testStartAndStop() {
        assertThatNoException().isThrownBy(() -> {
            acarsUnit.stop();
            acarsUnit.start();
        });
    }

    @Test
    void testSendFreeText() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AcarsMessage> received = new AtomicReference<>();

        groundStation.subscribeToAll(msg -> {
            received.set(msg);
            latch.countDown();
        });

        acarsUnit.sendFreeText("Hello Ground", "OPS");

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().getMessageText()).isEqualTo("Hello Ground");
        assertThat(received.get().getDirection()).isEqualTo(AcarsMessage.Direction.DOWNLINK);
        assertThat(received.get().getLabel()).isEqualTo(AcarsMessage.Label.FREE_TEXT);
    }

    @Test
    void testRequestWeather() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AcarsMessage> received = new AtomicReference<>();

        groundStation.subscribeToLabel(AcarsMessage.Label.WEATHER_REQUEST, msg -> {
            received.set(msg);
            latch.countDown();
        });

        acarsUnit.requestWeather("KJFK", response -> {});

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().getLabel()).isEqualTo(AcarsMessage.Label.WEATHER_REQUEST);
        assertThat(received.get().getMessageText()).contains("REQ WX KJFK");
    }

    @Test
    void testRequestRouteUpload() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AcarsMessage> received = new AtomicReference<>();

        groundStation.subscribeToLabel(AcarsMessage.Label.ROUTE_REQUEST, msg -> {
            received.set(msg);
            latch.countDown();
        });

        acarsUnit.requestRouteUpload("KJFK", "KLAX", response -> {});

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().getLabel()).isEqualTo(AcarsMessage.Label.ROUTE_REQUEST);
        assertThat(received.get().getMessageText()).contains("REQ RTE KJFK-KLAX");
    }

    @Test
    void testSendOooiEvent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AcarsMessage> received = new AtomicReference<>();

        groundStation.subscribeToLabel(AcarsMessage.Label.OOOI_EVENT, msg -> {
            received.set(msg);
            latch.countDown();
        });

        GeoCoordinate pos = GeoCoordinate.builder()
                .latitude(40.6413)
                .longitude(-73.7781)
                .build();

        acarsUnit.sendOooiEvent(new AircraftAcarsUnit.OooiEvent(
                AircraftAcarsUnit.OooiEvent.Type.OUT,
                pos,
                0,
                java.time.Instant.now()
        ));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().getLabel()).isEqualTo(AcarsMessage.Label.OOOI_EVENT);
        assertThat(received.get().getMessageText()).contains("OUT");
    }

    @Test
    void testSendEngineReport() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AcarsMessage> received = new AtomicReference<>();

        groundStation.subscribeToLabel(AcarsMessage.Label.ENGINE_REPORT, msg -> {
            received.set(msg);
            latch.countDown();
        });

        AircraftAcarsUnit.EngineData engineData = new AircraftAcarsUnit.EngineData(
                1, 85.5, 92.0, 650, 2500, 45, 80, 1.2
        );

        acarsUnit.sendEngineReport(engineData);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().getLabel()).isEqualTo(AcarsMessage.Label.ENGINE_REPORT);
        assertThat(received.get().getMessageText()).contains("ENG1");
    }

    @Test
    void testUpdatePosition() {
        GeoCoordinate pos = GeoCoordinate.builder()
                .latitude(40.6413)
                .longitude(-73.7781)
                .build();

        acarsUnit.updatePosition(pos, 35000, 90, 450);

        // Position updates don't return values, just verify no exception
        assertThatNoException().isThrownBy(() ->
                acarsUnit.updatePosition(pos, 35000, 90, 450));
    }

    @Test
    void testUpdateFlightPhaseTriggersOooi() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2); // Expect 2 OOOI events (OUT and OFF)

        groundStation.subscribeToLabel(AcarsMessage.Label.OOOI_EVENT, msg -> latch.countDown());

        GeoCoordinate pos = GeoCoordinate.builder()
                .latitude(40.6413)
                .longitude(-73.7781)
                .build();

        acarsUnit.updatePosition(pos, 0, 0, 0);
        acarsUnit.updateFlightPhase(AircraftAcarsUnit.FlightPhase.PREFLIGHT);
        acarsUnit.updateFlightPhase(AircraftAcarsUnit.FlightPhase.TAXI);
        acarsUnit.updateFlightPhase(AircraftAcarsUnit.FlightPhase.TAKEOFF);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void testStatistics() {
        AircraftAcarsUnit.Statistics stats = acarsUnit.getStatistics();
        assertThat(stats.messagesQueued).isZero();
        assertThat(stats.messagesTransmitted).isZero();

        acarsUnit.sendFreeText("Test", "OPS");

        assertThat(stats.messagesQueued).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testOooiEventToString() {
        GeoCoordinate pos = GeoCoordinate.builder()
                .latitude(40.6413)
                .longitude(-73.7781)
                .build();

        AircraftAcarsUnit.OooiEvent event = new AircraftAcarsUnit.OooiEvent(
                AircraftAcarsUnit.OooiEvent.Type.OUT,
                pos,
                1000,
                java.time.Instant.parse("2024-01-01T12:00:00Z")
        );

        String acarsString = event.toAcarsString();
        assertThat(acarsString).contains("OUT");
        assertThat(acarsString).contains("ALT1000");

        var map = event.toMap();
        assertThat(map).containsKey("eventType");
        assertThat(map).containsKey("latitude");
        assertThat(map).containsKey("longitude");
    }

    @Test
    void testEngineDataToString() {
        AircraftAcarsUnit.EngineData data = new AircraftAcarsUnit.EngineData(
                1, 85.5, 92.0, 650, 2500, 45, 80, 1.2
        );

        String acarsString = data.toAcarsString();
        assertThat(acarsString).contains("ENG1");
        assertThat(acarsString).contains("N185.5");

        var map = data.toMap();
        assertThat(map).containsKey("n1");
        assertThat(map).containsKey("egt");
    }

    @Test
    void testAllFlightPhases() {
        // Set up position first to avoid NPE when OOOI events are triggered
        GeoCoordinate pos = GeoCoordinate.builder()
                .latitude(40.6413)
                .longitude(-73.7781)
                .build();
        acarsUnit.updatePosition(pos, 0, 0, 0);

        // Verify all phases can be set without exception
        for (AircraftAcarsUnit.FlightPhase phase : AircraftAcarsUnit.FlightPhase.values()) {
            assertThatNoException().isThrownBy(() ->
                    acarsUnit.updateFlightPhase(phase));
        }
    }

    @Test
    void testEventReporter() {
        assertThat(acarsUnit.getEventReporter()).isNotNull();
    }
}
