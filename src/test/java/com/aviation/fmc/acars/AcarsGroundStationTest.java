package com.aviation.fmc.acars;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ACARS Ground Station.
 */
class AcarsGroundStationTest {

    private AcarsGroundStation groundStation;
    private List<AcarsMessage> receivedMessages;

    @BeforeEach
    void setUp() {
        groundStation = new AcarsGroundStation(
                "TEST-GS",
                "Test Ground Station",
                new AcarsGroundStation.GeoCoverage(40.0, -74.0, 100, "Test Area")
        );
        receivedMessages = new ArrayList<>();
        groundStation.start();
    }

    @AfterEach
    void tearDown() {
        groundStation.stop();
    }

    @Test
    void testStartAndStop() {
        // Already started in setUp, just verify it doesn't throw
        assertThatNoException().isThrownBy(() -> {
            groundStation.stop();
            groundStation.start();
        });
    }

    @Test
    void testReceiveFromAircraft() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        groundStation.subscribeToAll(msg -> {
            receivedMessages.add(msg);
            latch.countDown();
        });

        AcarsMessage message = AcarsMessage.createFreeText(
                "N123AA", "TEST-GS", "Test message", AcarsMessage.Direction.DOWNLINK);

        boolean received = groundStation.receiveFromAircraft(message);
        assertThat(received).isTrue();

        // Wait for async processing
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedMessages).hasSize(1);
        assertThat(receivedMessages.get(0).getMessageText()).isEqualTo("Test message");
    }

    @Test
    void testSendToAircraftInCoverage() throws InterruptedException {
        // First register aircraft as in-coverage by receiving a message
        AcarsMessage contactMsg = AcarsMessage.createFreeText(
                "N123AA", "TEST-GS", "Contact", AcarsMessage.Direction.DOWNLINK);
        groundStation.receiveFromAircraft(contactMsg);

        Thread.sleep(100); // Allow processing

        AcarsMessage uplink = AcarsMessage.builder()
                .aircraftRegistration("N123AA")
                .label(AcarsMessage.Label.FREE_TEXT)
                .messageText("Uplink message")
                .direction(AcarsMessage.Direction.UPLINK)
                .build();

        boolean sent = groundStation.sendToAircraft(uplink);
        assertThat(sent).isTrue();
    }

    @Test
    void testSendToAircraftOutOfCoverage() {
        // Aircraft not in coverage - message should be stored
        AcarsMessage uplink = AcarsMessage.builder()
                .aircraftRegistration("N999XX")
                .label(AcarsMessage.Label.FREE_TEXT)
                .messageText("Stored message")
                .direction(AcarsMessage.Direction.UPLINK)
                .build();

        boolean sent = groundStation.sendToAircraft(uplink);
        assertThat(sent).isTrue(); // Stored successfully

        List<AcarsMessage> pending = groundStation.getPendingMessages("N999XX");
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getMessageText()).isEqualTo("Stored message");
    }

    @Test
    void testSubscribeToLabel() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        groundStation.subscribeToLabel(AcarsMessage.Label.OOOI_EVENT, msg -> {
            receivedMessages.add(msg);
            latch.countDown();
        });

        // Send OOOI event
        AcarsMessage oooiMsg = AcarsMessage.builder()
                .aircraftRegistration("N123AA")
                .label(AcarsMessage.Label.OOOI_EVENT)
                .messageText("OUT")
                .direction(AcarsMessage.Direction.DOWNLINK)
                .build();

        groundStation.receiveFromAircraft(oooiMsg);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedMessages).hasSize(1);
        assertThat(receivedMessages.get(0).getLabel()).isEqualTo(AcarsMessage.Label.OOOI_EVENT);
    }

    @Test
    void testLabelSubscriptionNotTriggeredForOtherLabels() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        groundStation.subscribeToLabel(AcarsMessage.Label.OOOI_EVENT, msg -> {
            receivedMessages.add(msg);
            latch.countDown();
        });

        // Send different label
        AcarsMessage freeTextMsg = AcarsMessage.createFreeText(
                "N123AA", "TEST-GS", "Test", AcarsMessage.Direction.DOWNLINK);

        groundStation.receiveFromAircraft(freeTextMsg);

        // Should timeout - no message received
        assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(receivedMessages).isEmpty();
    }

    @Test
    void testUnsubscribe() throws InterruptedException {
        java.util.function.Consumer<AcarsMessage> handler = msg -> receivedMessages.add(msg);

        groundStation.subscribeToAll(handler);
        groundStation.unsubscribe(handler);

        AcarsMessage message = AcarsMessage.createFreeText(
                "N123AA", "TEST-GS", "Test", AcarsMessage.Direction.DOWNLINK);

        groundStation.receiveFromAircraft(message);

        Thread.sleep(500);
        assertThat(receivedMessages).isEmpty();
    }

    @Test
    void testIsAircraftInCoverage() {
        // Initially not in coverage
        assertThat(groundStation.isAircraftInCoverage("N123AA")).isFalse();

        // Register aircraft by receiving message
        AcarsMessage message = AcarsMessage.createFreeText(
                "N123AA", "TEST-GS", "Test", AcarsMessage.Direction.DOWNLINK);
        groundStation.receiveFromAircraft(message);

        assertThat(groundStation.isAircraftInCoverage("N123AA")).isTrue();
    }

    @Test
    void testGetActiveAircraft() {
        // Initially empty
        assertThat(groundStation.getActiveAircraft()).isEmpty();

        // Register multiple aircraft
        groundStation.receiveFromAircraft(AcarsMessage.createFreeText(
                "N123AA", "TEST-GS", "Test", AcarsMessage.Direction.DOWNLINK));
        groundStation.receiveFromAircraft(AcarsMessage.createFreeText(
                "N456UA", "TEST-GS", "Test", AcarsMessage.Direction.DOWNLINK));

        List<String> active = groundStation.getActiveAircraft();
        assertThat(active).containsExactlyInAnyOrder("N123AA", "N456UA");
    }

    @Test
    void testStatistics() {
        AcarsGroundStation.AcarsStatistics stats = groundStation.getStatistics();
        assertThat(stats.messagesReceived).isZero();
        assertThat(stats.messagesSent).isZero();

        // Receive a message
        groundStation.receiveFromAircraft(AcarsMessage.createFreeText(
                "N123AA", "TEST-GS", "Test", AcarsMessage.Direction.DOWNLINK));

        assertThat(stats.messagesReceived).isEqualTo(1);
    }

    @Test
    void testGeoCoverage() {
        AcarsGroundStation.GeoCoverage coverage = new AcarsGroundStation.GeoCoverage(
                40.6413, -73.7781, 200, "JFK Area");

        assertThat(coverage.centerLatitude()).isEqualTo(40.6413);
        assertThat(coverage.centerLongitude()).isEqualTo(-73.7781);
        assertThat(coverage.radiusNm()).isEqualTo(200);
        assertThat(coverage.description()).isEqualTo("JFK Area");
    }

    @Test
    void testReceiveWhenNotRunning() {
        groundStation.stop();

        AcarsMessage message = AcarsMessage.createFreeText(
                "N123AA", "TEST-GS", "Test", AcarsMessage.Direction.DOWNLINK);

        boolean received = groundStation.receiveFromAircraft(message);
        assertThat(received).isFalse();
    }

    @Test
    void testSendWhenNotRunning() {
        groundStation.stop();

        AcarsMessage message = AcarsMessage.builder()
                .aircraftRegistration("N123AA")
                .label(AcarsMessage.Label.FREE_TEXT)
                .build();

        boolean sent = groundStation.sendToAircraft(message);
        assertThat(sent).isFalse();
    }
}
