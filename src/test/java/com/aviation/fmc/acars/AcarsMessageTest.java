package com.aviation.fmc.acars;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ACARS Message model.
 */
class AcarsMessageTest {

    @Test
    void testCreateFreeTextMessage() {
        AcarsMessage message = AcarsMessage.createFreeText(
                "N123AA",
                "JFK-GS",
                "TEST MESSAGE",
                AcarsMessage.Direction.DOWNLINK
        );

        assertThat(message).isNotNull();
        assertThat(message.getAircraftRegistration()).isEqualTo("N123AA");
        assertThat(message.getGroundStationId()).isEqualTo("JFK-GS");
        assertThat(message.getMessageText()).isEqualTo("TEST MESSAGE");
        assertThat(message.getDirection()).isEqualTo(AcarsMessage.Direction.DOWNLINK);
        assertThat(message.getLabel()).isEqualTo(AcarsMessage.Label.FREE_TEXT);
        assertThat(message.getPriority()).isEqualTo(AcarsMessage.Priority.NORMAL);
    }

    @Test
    void testMessageLabelFromCode() {
        Optional<AcarsMessage.Label> label = AcarsMessage.Label.fromCode("10");
        assertThat(label).isPresent();
        assertThat(label.get()).isEqualTo(AcarsMessage.Label.OOOI_EVENT);

        Optional<AcarsMessage.Label> unknownLabel = AcarsMessage.Label.fromCode("XX");
        assertThat(unknownLabel).isEmpty();
    }

    @Test
    void testLabelProperties() {
        AcarsMessage.Label label = AcarsMessage.Label.POSITION_REPORT;
        assertThat(label.getCode()).isEqualTo("12");
        assertThat(label.getDescription()).isEqualTo("Position Report");
    }

    @Test
    void testMessageRequiresAck() {
        AcarsMessage emergencyMsg = AcarsMessage.builder()
                .priority(AcarsMessage.Priority.EMERGENCY)
                .build();
        assertThat(emergencyMsg.requiresAck()).isTrue();

        AcarsMessage normalMsg = AcarsMessage.builder()
                .priority(AcarsMessage.Priority.NORMAL)
                .build();
        assertThat(normalMsg.requiresAck()).isFalse();
    }

    @Test
    void testIsOooiEvent() {
        AcarsMessage oooiMsg = AcarsMessage.builder()
                .label(AcarsMessage.Label.OOOI_EVENT)
                .build();
        assertThat(oooiMsg.isOooiEvent()).isTrue();

        AcarsMessage otherMsg = AcarsMessage.builder()
                .label(AcarsMessage.Label.FREE_TEXT)
                .build();
        assertThat(otherMsg.isOooiEvent()).isFalse();
    }

    @Test
    void testIsPositionReport() {
        AcarsMessage posMsg = AcarsMessage.builder()
                .label(AcarsMessage.Label.POSITION_REPORT)
                .build();
        assertThat(posMsg.isPositionReport()).isTrue();

        AcarsMessage adsMsg = AcarsMessage.builder()
                .label(AcarsMessage.Label.ADS_REPORT)
                .build();
        assertThat(adsMsg.isPositionReport()).isTrue();

        AcarsMessage otherMsg = AcarsMessage.builder()
                .label(AcarsMessage.Label.FREE_TEXT)
                .build();
        assertThat(otherMsg.isPositionReport()).isFalse();
    }

    @Test
    void testMessageAge() throws InterruptedException {
        AcarsMessage message = AcarsMessage.builder()
                .createdAt(Instant.now())
                .build();

        Thread.sleep(100); // Wait 100ms

        long age = message.getAgeSeconds();
        assertThat(age).isGreaterThanOrEqualTo(0);
    }

    @Test
    void testMessageBuilder() {
        Instant now = Instant.now();
        AcarsMessage message = AcarsMessage.builder()
                .direction(AcarsMessage.Direction.DOWNLINK)
                .label(AcarsMessage.Label.ENGINE_REPORT)
                .priority(AcarsMessage.Priority.HIGH)
                .medium(AcarsMessage.Medium.VHF)
                .aircraftRegistration("N456UA")
                .aircraftIcao24("A67890")
                .groundStationId("ORD-GS")
                .messageText("ENG1 N185.5 EGT650")
                .structuredData(Map.of("n1", "85.5", "egt", "650"))
                .createdAt(now)
                .blockId("A")
                .messageNumber(1)
                .acknowledged(false)
                .delivered(false)
                .retryCount(0)
                .build();

        assertThat(message.getDirection()).isEqualTo(AcarsMessage.Direction.DOWNLINK);
        assertThat(message.getLabel()).isEqualTo(AcarsMessage.Label.ENGINE_REPORT);
        assertThat(message.getPriority()).isEqualTo(AcarsMessage.Priority.HIGH);
        assertThat(message.getMedium()).isEqualTo(AcarsMessage.Medium.VHF);
        assertThat(message.getAircraftRegistration()).isEqualTo("N456UA");
        assertThat(message.getAircraftIcao24()).isEqualTo("A67890");
        assertThat(message.getGroundStationId()).isEqualTo("ORD-GS");
        assertThat(message.getMessageText()).isEqualTo("ENG1 N185.5 EGT650");
        assertThat(message.getStructuredData()).containsEntry("n1", "85.5");
        assertThat(message.getCreatedAt()).isEqualTo(now);
        assertThat(message.getBlockId()).isEqualTo("A");
        assertThat(message.getMessageNumber()).isEqualTo(1);
        assertThat(message.isAcknowledged()).isFalse();
        assertThat(message.isDelivered()).isFalse();
        assertThat(message.getRetryCount()).isZero();
    }

    @Test
    void testToBuilder() {
        AcarsMessage original = AcarsMessage.builder()
                .aircraftRegistration("N123AA")
                .label(AcarsMessage.Label.FREE_TEXT)
                .build();

        AcarsMessage modified = original.toBuilder()
                .label(AcarsMessage.Label.WEATHER_REQUEST)
                .build();

        assertThat(modified.getAircraftRegistration()).isEqualTo("N123AA");
        assertThat(modified.getLabel()).isEqualTo(AcarsMessage.Label.WEATHER_REQUEST);
    }

    @Test
    void testAllLabelsHaveCodes() {
        for (AcarsMessage.Label label : AcarsMessage.Label.values()) {
            assertThat(label.getCode()).isNotNull();
            assertThat(label.getCode()).hasSize(2);
            assertThat(label.getDescription()).isNotNull();
        }
    }

    @Test
    void testPriorityLevels() {
        assertThat(AcarsMessage.Priority.EMERGENCY.getLevel()).isEqualTo(0);
        assertThat(AcarsMessage.Priority.HIGH.getLevel()).isEqualTo(1);
        assertThat(AcarsMessage.Priority.NORMAL.getLevel()).isEqualTo(2);
        assertThat(AcarsMessage.Priority.LOW.getLevel()).isEqualTo(3);
    }
}
