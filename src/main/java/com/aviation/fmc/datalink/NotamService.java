package com.aviation.fmc.datalink;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * NOTAM Service interface for retrieving Notice To Air Missions.
 * 
 * NOTAMs are distributed through:
 * - FAA NOTAM System (US)
 * - Eurocontrol EAD (Europe)
 * - ICAO (International)
 * - Commercial providers (ARINC, SITA, Jeppesen)
 * 
 * NOTAMs relevant to FMC operations include:
 * - Runway closures
 * - Navigation aid outages
 * - Procedure changes
 * - Airspace restrictions
 */
public interface NotamService {

    /**
     * Get all active NOTAMs for an airport.
     * 
     * @param icaoCode 4-letter ICAO code
     * @return List of active NOTAMs
     */
    List<NotamReport> getNotamsForAirport(String icaoCode);

    /**
     * Get NOTAMs along a route.
     * Returns NOTAMs for airports, navaids, and airspace along the flight path.
     * 
     * @param departure Departure airport
     * @param destination Destination airport
     * @param alternates Alternate airports
     * @param routeWaypoints Waypoints along route
     * @return Route-affecting NOTAMs
     */
    RouteNotams getNotamsForRoute(
        String departure,
        String destination,
        List<String> alternates,
        List<String> routeWaypoints
    );

    /**
     * Get specific NOTAM by ID.
     */
    Optional<NotamReport> getNotamById(String notamId);

    /**
     * Check if there are critical NOTAMs affecting FMC operation.
     * Critical NOTAMs include runway closures and nav aid outages.
     */
    List<NotamReport> getCriticalNotams(String icaoCode);

    /**
     * Request NOTAM update via datalink.
     * Aircraft requests, ground station sends relevant NOTAMs.
     */
    void requestNotamUpdate(String icaoCode);

    /**
     * NOTAM Report.
     */
    record NotamReport(
        String id,
        String icaoCode,
        NotamType type,
        NotamScope scope,
        String rawText,
        Instant issueTime,
        Instant validFrom,
        Instant validTo,
        Instant cancellationTime,  // If cancelled
        String keyword,            // RWY, NAV, SID, STAR, etc.
        String subject,
        String condition,
        int classification,        // International, Domestic, Military
        boolean fmcRelevant        // Whether this affects FMC operation
    ) {
        /**
         * Check if this NOTAM affects FMC navigation.
         */
        public boolean affectsFmc() {
            return switch (keyword) {
                case "RWY", "NAV", "SID", "STAR", "APP", "WAYPOINT",
                     "AIRSPACE", "OBST", "COM" -> true;
                default -> false;
            };
        }

        /**
         * Get severity level for FMC operations.
         */
        public NotamSeverity getSeverity() {
            if (condition != null) {
                if (condition.contains("CLSD") || condition.contains("OUT OF SERVICE")) {
                    return NotamSeverity.CRITICAL;
                }
                if (condition.contains("DEGRADED") || condition.contains("LIMITATIONS")) {
                    return NotamSeverity.WARNING;
                }
            }
            return NotamSeverity.INFO;
        }
    }

    /**
     * NOTAM Type.
     */
    enum NotamType {
        NOTAMN,  // New NOTAM
        NOTAMR,  // Replace previous NOTAM
        NOTAMC   // Cancel previous NOTAM
    }

    /**
     * NOTAM Scope.
     */
    enum NotamScope {
        AERODROME,
        ENROUTE,
        NAV_WARNING,
        CHECKLIST
    }

    /**
     * NOTAM Severity for FMC operations.
     */
    enum NotamSeverity {
        INFO,      // Informational only
        WARNING,   // Degraded capability
        CRITICAL   // Must be addressed
    }

    /**
     * Collection of NOTAMs for a route.
     */
    record RouteNotams(
        String departure,
        String destination,
        List<NotamReport> departureNotams,
        List<NotamReport> destinationNotams,
        List<NotamReport> alternateNotams,
        List<NotamReport> enrouteNotams,
        List<NotamReport> fmcRelevantNotams
    ) {
        /**
         * Get all critical NOTAMs affecting flight.
         */
        public List<NotamReport> getCriticalNotams() {
            return fmcRelevantNotams.stream()
                .filter(n -> n.getSeverity() == NotamSeverity.CRITICAL)
                .toList();
        }

        /**
         * Check if runway is closed.
         */
        public boolean isRunwayClosed(String runwayId) {
            return fmcRelevantNotams.stream()
                .anyMatch(n -> 
                    n.keyword().equals("RWY") &&
                    n.condition().contains(runwayId) &&
                    n.condition().contains("CLSD")
                );
        }

        /**
         * Check if navaid is out of service.
         */
        public boolean isNavaidOutOfService(String navaidId) {
            return fmcRelevantNotams.stream()
                .anyMatch(n ->
                    n.keyword().equals("NAV") &&
                    n.condition().contains(navaidId) &&
                    (n.condition().contains("OOS") || n.condition().contains("OUT OF SERVICE"))
                );
        }
    }

    /**
     * NOTAM keywords relevant to FMC.
     */
    List<String> FMC_RELEVANT_KEYWORDS = List.of(
        "RWY",      // Runway closures/changes
        "NAV",      // Navaid outages
        "SID",      // SID changes
        "STAR",     // STAR changes
        "APP",      // Approach changes
        "WAYPOINT", // Waypoint changes
        "AIRSPACE", // Airspace restrictions
        "COM",      // Communication changes
        "OBST"      // Obstructions
    );
}
