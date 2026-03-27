package com.aviation.fmc.parser;

import com.aviation.fmc.common.Altitude;
import com.aviation.fmc.navdata.Airport;
import com.aviation.fmc.navdata.Waypoint;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for ICAO flight plan format (FPL).
 * Implements ICAO Doc 4444 flight plan specification.
 * 
 * Example format:
 * (FPL-UAL123-IS
 *  -B738/M-SDE2E3FGHIJ2J3J4J5M1RWY/LB1
 *  -KJFK1200
 *  -N0450F350 DCT BOSOX J123 ALB DCT
 *  -KBOS0200 KBDL
 *  -PBN/A1B1C1D1 NAV/GBAS DOF/240101 EET/KZBW0020 REG/N123UA)
 */
public class IcaoFlightPlanParser implements FlightPlanParser {

    private static final Pattern FPL_PATTERN = Pattern.compile(
        "\\(FPL-([A-Z0-9]+)-([A-Z]+)\\s*" +
        "-([A-Z0-9]+)/([A-Z])-([A-Z0-9]+)\\s*" +
        "-([A-Z0-9]{4})(\\d{4})\\s*" +
        "-(.+?)\\s*" +
        "-([A-Z0-9]{4})(\\d{4})\\s*" +
        "(?:-(.+?))?\\)"
    );

    @Override
    public ParserOutput parse(String input, NavigationContext context) {
        List<ParserOutput.ParseError> errors = new ArrayList<>();
        List<ParserOutput.ParseWarning> warnings = new ArrayList<>();

        // Normalize input
        String normalized = input.replaceAll("\\s+", " ").trim();

        if (!normalized.startsWith("(FPL-")) {
            return ParserOutput.failure(input, getFormatName(), List.of(
                ParserOutput.ParseError.of("FPL_INVALID_FORMAT", 
                    "Input does not appear to be a valid ICAO FPL message")
            ));
        }

        try {
            // Extract basic fields
            String flightNumber = extractField(normalized, "FPL-", "-");
            String flightRules = extractField(normalized, flightNumber + "-", "-");
            String aircraftType = extractBetween(normalized, "-", "/");
            
            // Parse departure
            String depString = extractPattern(normalized, "-[A-Z]{4}\\d{4}\\s*-");
            String departureIcao = depString.substring(1, 5);
            String depTime = depString.substring(5, 9);
            
            Airport departure = context.findAirport(departureIcao);
            if (departure == null) {
                warnings.add(ParserOutput.ParseWarning.of("FPL_DEP_NOT_FOUND",
                    String.format("Departure airport %s not found in navigation database", departureIcao)));
            }

            // Parse route
            String routeSection = extractRouteSection(normalized);
            List<RouteElement> route = parseRoute(routeSection, context);

            // Parse destination
            String destString = extractPattern(normalized, "-[A-Z]{4}\\d{4}(?:\\s|\\))");
            String destinationIcao = destString.substring(1, 5);
            String arrTime = destString.substring(5, 9);
            
            Airport destination = context.findAirport(destinationIcao);
            if (destination == null) {
                warnings.add(ParserOutput.ParseWarning.of("FPL_DEST_NOT_FOUND",
                    String.format("Destination airport %s not found in navigation database", destinationIcao)));
            }

            // Build flight plan
            FlightPlan.FlightPlanBuilder builder = FlightPlan.builder()
                .flightNumber(flightNumber)
                .aircraftType(aircraftType != null ? aircraftType : "A320")
                .flightPlanType(parseFlightRules(flightRules))
                .departure(departure)
                .destination(destination)
                .estimatedDepartureTime(parseTime(depTime))
                .route(route);

            // Parse cruise altitude if present in route
            Altitude cruiseAlt = extractCruiseAltitude(routeSection);
            if (cruiseAlt != null) {
                builder.cruiseAltitude(cruiseAlt);
            }

            FlightPlan flightPlan = builder.build();

            if (errors.isEmpty()) {
                if (warnings.isEmpty()) {
                    return ParserOutput.success(flightPlan, input, getFormatName());
                } else {
                    return ParserOutput.successWithWarnings(flightPlan, input, getFormatName(), warnings);
                }
            } else {
                return ParserOutput.failure(input, getFormatName(), errors);
            }

        } catch (Exception e) {
            errors.add(ParserOutput.ParseError.of("FPL_PARSE_ERROR",
                "Failed to parse flight plan: " + e.getMessage()));
            return ParserOutput.failure(input, getFormatName(), errors);
        }
    }

    @Override
    public boolean canParse(String input) {
        return input != null && input.trim().startsWith("(FPL-");
    }

    @Override
    public String getFormatName() {
        return "ICAO";
    }

    @Override
    public String getFormatVersion() {
        return "Doc 4444 16th Edition";
    }

    // Helper methods
    private String extractField(String input, String prefix, String suffix) {
        int start = input.indexOf(prefix);
        if (start < 0) return null;
        start += prefix.length();
        int end = input.indexOf(suffix, start);
        if (end < 0) return input.substring(start);
        return input.substring(start, end);
    }

    private String extractBetween(String input, String start, String end) {
        int s = input.indexOf(start);
        if (s < 0) return null;
        s += start.length();
        int e = input.indexOf(end, s);
        if (e < 0) return null;
        return input.substring(s, e);
    }

    private String extractPattern(String input, String pattern) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(input);
        return m.find() ? m.group() : "";
    }

    private String extractRouteSection(String input) {
        // Route is between departure and destination
        String[] parts = input.split("\\s+-\\s*");
        if (parts.length >= 4) {
            return parts[3].trim();
        }
        return "";
    }

    private List<RouteElement> parseRoute(String routeSection, NavigationContext context) {
        List<RouteElement> elements = new ArrayList<>();
        String[] tokens = routeSection.split("\\s+");
        
        int seq = 0;
        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty()) continue;

            // Skip speed/altitude indicators like "N0450F350"
            if (token.matches("[NMK]\\d{4}[FAWM]\\d{3}")) {
                continue;
            }

            RouteElement.RouteElementBuilder elementBuilder = RouteElement.builder()
                .sequenceNumber(seq++);

            // Check if it's an airway
            if (token.matches("[JVUTLQMNPY]\\d{1,4}")) {
                elementBuilder.type(RouteElement.ElementType.AIRWAY);
                // Would need to look up airway in context
            } else if (token.equals("DCT")) {
                elementBuilder.type(RouteElement.ElementType.DIRECT);
            } else {
                // Assume it's a waypoint
                elementBuilder.type(RouteElement.ElementType.WAYPOINT);
                Waypoint wp = context.findWaypoint(token);
                if (wp != null) {
                    elementBuilder.waypoint(wp);
                }
            }

            elements.add(elementBuilder.build());
        }

        return elements;
    }

    private FlightPlan.FlightPlanType parseFlightRules(String rules) {
        if (rules == null) return FlightPlan.FlightPlanType.IFR;
        return switch (rules.charAt(0)) {
            case 'V' -> FlightPlan.FlightPlanType.VFR;
            case 'Y' -> FlightPlan.FlightPlanType.IFR_VFR;
            case 'Z' -> FlightPlan.FlightPlanType.VFR_IFR;
            default -> FlightPlan.FlightPlanType.IFR;
        };
    }

    private LocalDateTime parseTime(String time) {
        // Parse HHMM format
        if (time == null || time.length() != 4) {
            return LocalDateTime.now();
        }
        try {
            int hour = Integer.parseInt(time.substring(0, 2));
            int minute = Integer.parseInt(time.substring(2, 4));
            return LocalDateTime.now()
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0);
        } catch (NumberFormatException e) {
            return LocalDateTime.now();
        }
    }

    private Altitude extractCruiseAltitude(String routeSection) {
        // Look for altitude in route (e.g., "F350" = FL350)
        Pattern pattern = Pattern.compile("[FAWM](\\d{3})");
        Matcher matcher = pattern.matcher(routeSection);
        if (matcher.find()) {
            int level = Integer.parseInt(matcher.group(1));
            return Altitude.flightLevel(level);
        }
        return Altitude.msl(30000); // Default
    }
}
