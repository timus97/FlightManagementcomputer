package com.aviation.fmc.acars;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Represents an ACARS (Aircraft Communications Addressing and Reporting System) message.
 * ACARS is a digital datalink system for transmission of short messages between aircraft and ground.
 *
 * Message Format:
 * - SOH (Start of Header): 0x01
 * - Mode: 2 chars (e.g., "2" for downlink, "A" for uplink)
 * - Aircraft Address: 7 chars (ICAO 24-bit address or airline tail number)
 * - ACK/NAK: 1 char
 * - Label: 2 chars (message type identifier)
 * - Block ID: 1 char
 * - STX (Start of Text): 0x02
 * - Message Text: Variable length
 * - ETX (End of Text): 0x03
 * - BCS (Block Check Sequence): 2 chars (checksum)
 */
@Value
@Builder(toBuilder = true)
public class AcarsMessage {

    /**
     * Direction of message transmission.
     */
    public enum Direction {
        UPLINK,     // Ground to Air
        DOWNLINK    // Air to Ground
    }

    /**
     * ACARS message labels (2-character codes).
     * Standard labels define the message type and routing.
     */
    public enum Label {
        // Out/Off/On/In (OOOI) events
        OOOI_EVENT("10", "OOOI Event Report"),

        // Engine data
        ENGINE_REPORT("11", "Engine Report"),

        // Position reports
        POSITION_REPORT("12", "Position Report"),
        ADS_REPORT("13", "ADS-C Report"),

        // Flight data
        FLIGHT_DATA("14", "Flight Data"),
        WEIGHT_BALANCE("15", "Weight & Balance"),

        // Maintenance
        MAINTENANCE("16", "Maintenance Message"),
        FAULT_REPORT("17", "Fault Report"),

        // Load/ manifest
        LOAD_MANIFEST("18", "Load Manifest"),
        PASSENGER_DATA("19", "Passenger Data"),

        // Communication
        FREE_TEXT("80", "Free Text Message"),
        CPDLC_MESSAGE("81", "CPDLC Message"),
        ATIS_REQUEST("82", "ATIS Request"),
        ATIS_REPORT("83", "ATIS Report"),

        // Weather
        WEATHER_REQUEST("84", "Weather Request"),
        WEATHER_REPORT("85", "Weather Report"),

        // Navigation
        CLEARANCE_REQUEST("86", "Clearance Request"),
        CLEARANCE_DELIVERY("87", "Clearance Delivery"),

        // FMC/Route
        ROUTE_REQUEST("90", "Route Request"),
        ROUTE_UPLOAD("91", "Route Upload"),
        WIND_DATA("92", "Wind Data"),

        // System
        PING("Q0", "System Ping"),
        ACK("Q1", "Acknowledgment"),
        NAK("Q2", "Negative Acknowledgment"),

        // Custom/Private
        CUSTOM("99", "Custom Message");

        private final String code;
        private final String description;

        Label(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getDescription() { return description; }

        public static Optional<Label> fromCode(String code) {
            for (Label label : values()) {
                if (label.code.equals(code)) {
                    return Optional.of(label);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Message priority levels.
     */
    public enum Priority {
        EMERGENCY(0),   // Mayday/Pan-Pan
        HIGH(1),        // Safety critical
        NORMAL(2),      // Standard operational
        LOW(3);         // Non-critical

        private final int level;

        Priority(int level) { this.level = level; }
        public int getLevel() { return level; }
    }

    /**
     * Transmission medium used.
     */
    public enum Medium {
        VHF,        // Very High Frequency (line of sight)
        HF,         // High Frequency (long range)
        SATCOM,     // Satellite communication
        VDL2        // VHF Data Link Mode 2
    }

    // Message metadata
    Direction direction;
    Label label;
    Priority priority;
    Medium medium;

    // Addressing
    String aircraftRegistration;    // Aircraft tail number (e.g., "N123AA")
    String aircraftIcao24;          // ICAO 24-bit address (hex)
    String groundStationId;         // Ground station identifier
    String destinationAddress;      // Target ground system

    // Message content
    String messageText;
    Map<String, String> structuredData;  // Parsed key-value pairs

    // Timestamps
    Instant createdAt;
    Instant transmittedAt;
    Instant receivedAt;

    // Sequence info
    String blockId;                 // Message sequence identifier
    int messageNumber;              // Sequential message number

    // Status
    boolean acknowledged;
    boolean delivered;
    int retryCount;

    // Raw data for debugging
    byte[] rawFrame;

    /**
     * Check if this is an OOOI (Out/Off/On/In) event message.
     */
    public boolean isOooiEvent() {
        return label == Label.OOOI_EVENT;
    }

    /**
     * Check if this is a position report.
     */
    public boolean isPositionReport() {
        return label == Label.POSITION_REPORT || label == Label.ADS_REPORT;
    }

    /**
     * Check if this message requires acknowledgment.
     */
    public boolean requiresAck() {
        return priority == Priority.EMERGENCY || priority == Priority.HIGH;
    }

    /**
     * Get message age in seconds.
     */
    public long getAgeSeconds() {
        return java.time.Duration.between(createdAt, Instant.now()).getSeconds();
    }

    /**
     * Create a simple free text message.
     */
    public static AcarsMessage createFreeText(
            String aircraftReg,
            String groundStation,
            String text,
            Direction direction) {
        return AcarsMessage.builder()
                .direction(direction)
                .label(Label.FREE_TEXT)
                .priority(Priority.NORMAL)
                .medium(Medium.VHF)
                .aircraftRegistration(aircraftReg)
                .groundStationId(groundStation)
                .messageText(text)
                .createdAt(Instant.now())
                .acknowledged(false)
                .delivered(false)
                .retryCount(0)
                .build();
    }
}
