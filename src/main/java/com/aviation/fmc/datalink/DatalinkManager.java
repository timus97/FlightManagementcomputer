package com.aviation.fmc.datalink;

import com.aviation.fmc.parser.FlightPlan;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Datalink Manager for FMC integration.
 * Handles CPDLC, ADS-C, FIS-B, and AOC communications.
 * 
 * In a real aircraft, this would interface with:
 * - ACARS (Aircraft Communications Addressing and Reporting System)
 * - ATN (Aeronautical Telecommunications Network)
 * - SATCOM/VHF/HF datalink radios
 */
public interface DatalinkManager {

    /**
     * Initialize datalink connection.
     */
    void initialize();

    /**
     * Check if datalink is available and connected.
     */
    boolean isConnected();

    /**
     * Get current datalink status.
     */
    DatalinkStatus getStatus();

    // ==================== CPDLC Operations ====================

    /**
     * Request departure clearance (DCL).
     */
    void requestDepartureClearance(String airport, String stand);

    /**
     * Send CPDLC message to ATC.
     */
    void sendCpldcMessage(CpdlcMessage message);

    /**
     * Get pending CPDLC messages from ATC.
     */
    List<CpdlcMessage> getPendingMessages();

    /**
     * Accept a clearance/instruction.
     */
    void acceptMessage(int messageId);

    /**
     * Reject a clearance with reason.
     */
    void rejectMessage(int messageId, String reason);

    /**
     * Standby (delay response).
     */
    void standbyMessage(int messageId);

    // ==================== Flight Plan Uplink ====================

    /**
     * Request flight plan from ground.
     */
    void requestFlightPlan(String flightNumber);

    /**
     * Get flight plan uplink if available.
     */
    Optional<FlightPlan> getUplinkedFlightPlan();

    /**
     * Accept flight plan uplink.
     */
    void acceptFlightPlanUplink();

    /**
     * Reject flight plan uplink.
     */
    void rejectFlightPlanUplink(String reason);

    // ==================== Wind/Weather Data ====================

    /**
     * Request winds aloft for route.
     */
    void requestWindData();

    /**
     * Get latest wind uplink.
     */
    Optional<WindUplink> getWindUplink();

    /**
     * Request weather (METAR/TAF) for airport.
     */
    void requestWeather(String icaoCode);

    /**
     * Request NOTAM update for airport.
     */
    void requestNotamUpdate(String icaoCode);

    /**
     * Get pending weather reports.
     */
    List<WeatherReport> getPendingWeather();

    // ==================== ADS-C (Surveillance) ====================

    /**
     * Start ADS-C reporting to ATC.
     */
    void startAdsCReporting(String atcCenter);

    /**
     * Stop ADS-C reporting.
     */
    void stopAdsCReporting();

    /**
     * Send position report (manual or automatic).
     */
    void sendPositionReport(PositionReport report);

    // ==================== AOC (Airline) ====================

    /**
     * Send message to airline operations.
     */
    void sendAocMessage(AocMessage message);

    /**
     * Get pending AOC messages.
     */
    List<AocMessage> getPendingAocMessages();

    /**
     * Datalink status.
     */
    record DatalinkStatus(
        boolean connected,
        String currentAtc,
        ConnectionType connectionType,
        int pendingMessages,
        Instant lastContact
    ) {
        public enum ConnectionType {
            VHF,        // VHF Datalink (VDL)
            SATCOM,     // Satellite communication
            HF,         // High Frequency datalink
            NONE        // No connection
        }
    }

    /**
     * CPDLC Message structure.
     */
    record CpdlcMessage(
        int messageId,
        String from,
        String to,
        MessageType type,
        String content,
        Instant timestamp,
        boolean requiresResponse
    ) {
        public enum MessageType {
            CLEARANCE,      // Route/altitude clearances
            INSTRUCTION,    // Turn, climb, descend
            INFORMATION,    // Traffic, weather
            REQUEST,        // Pilot requests
            EMERGENCY,      // Mayday/Panpan
            SYSTEM          // Logon/logoff
        }
    }

    /**
     * Wind data uplink.
     */
    record WindUplink(
        String routeId,
        List<WindLevel> windLevels,
        Instant validFrom,
        Instant validTo
    ) {
        public record WindLevel(
            int flightLevel,
            int windDirection,
            int windSpeed,
            int temperature  // Celsius
        ) {}
    }

    /**
     * Weather report.
     */
    record WeatherReport(
        String airport,
        String metar,
        String taf,
        List<String> sigmets,
        Instant received
    ) {}

    /**
     * Position report for ADS-C.
     */
    record PositionReport(
        double latitude,
        double longitude,
        int altitude,
        int groundspeed,
        String nextWaypoint,
        int distanceToNext,
        Instant etaNext,
        Instant timestamp
    ) {}

    /**
     * AOC Message.
     */
    record AocMessage(
        String type,
        String content,
        Instant timestamp
    ) {}
}
