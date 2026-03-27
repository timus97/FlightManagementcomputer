package com.aviation.fmc.parser;

import com.aviation.fmc.navdata.Airport;
import com.aviation.fmc.navdata.Airway;
import com.aviation.fmc.navdata.Waypoint;

import java.util.List;
import java.util.Map;

/**
 * Interface for flight plan parsers.
 * Implementations parse various flight plan formats into the canonical FlightPlan model.
 */
public interface FlightPlanParser {

    /**
     * Parse a flight plan from string input.
     * 
     * @param input The raw flight plan text
     * @param context Navigation context containing airports, waypoints, airways
     * @return ParserOutput containing the parsed flight plan or errors
     */
    ParserOutput parse(String input, NavigationContext context);

    /**
     * Parse a flight plan with format auto-detection.
     * 
     * @param input The raw flight plan text
     * @param context Navigation context
     * @return ParserOutput containing the parsed flight plan or errors
     */
    default ParserOutput parse(String input) {
        return parse(input, NavigationContext.empty());
    }

    /**
     * Check if this parser can handle the given input format.
     * 
     * @param input Sample input to check
     * @return true if this parser can parse the input
     */
    boolean canParse(String input);

    /**
     * Get the format name this parser handles.
     * 
     * @return Format name (e.g., "ICAO", "FAA", "ARINC424")
     */
    String getFormatName();

    /**
     * Get the format version this parser supports.
     * 
     * @return Format version string
     */
    String getFormatVersion();

    /**
     * Navigation context for flight plan parsing.
     * Provides access to navigation data needed for resolving references.
     */
    class NavigationContext {
        private final Map<String, Airport> airports;
        private final Map<String, Waypoint> waypoints;
        private final Map<String, Airway> airways;

        public NavigationContext(
                Map<String, Airport> airports,
                Map<String, Waypoint> waypoints,
                Map<String, Airway> airways) {
            this.airports = airports;
            this.waypoints = waypoints;
            this.airways = airways;
        }

        public static NavigationContext empty() {
            return new NavigationContext(Map.of(), Map.of(), Map.of());
        }

        public Airport findAirport(String icaoCode) {
            return airports.get(icaoCode);
        }

        public Waypoint findWaypoint(String identifier) {
            return waypoints.get(identifier);
        }

        public Airway findAirway(String designator) {
            return airways.get(designator);
        }

        public boolean hasAirport(String icaoCode) {
            return airports.containsKey(icaoCode);
        }

        public boolean hasWaypoint(String identifier) {
            return waypoints.containsKey(identifier);
        }

        public boolean hasAirway(String designator) {
            return airways.containsKey(designator);
        }
    }
}
