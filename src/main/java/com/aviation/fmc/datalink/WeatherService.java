package com.aviation.fmc.datalink;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Weather Service interface for retrieving aviation weather data.
 * 
 * In a real implementation, this would connect to:
 * - NOAA Aviation Weather Center (US)
 * - MeteoSwiss, MetOffice (Europe)
 * - Commercial providers (ARINC, SITA, Jeppesen)
 * - Flight planning systems (Lido, JetPlanner)
 */
public interface WeatherService {

    /**
     * Get current METAR for an airport.
     * 
     * @param icaoCode 4-letter ICAO code (e.g., "KJFK")
     * @return METAR report or empty if not available
     */
    Optional<MetarReport> getMetar(String icaoCode);

    /**
     * Get TAF (Terminal Aerodrome Forecast) for an airport.
     * 
     * @param icaoCode 4-letter ICAO code
     * @return TAF report or empty if not available
     */
    Optional<TafReport> getTaf(String icaoCode);

    /**
     * Get SIGMETs (Significant Meteorological Information) for a region.
     * SIGMETs indicate severe weather: thunderstorms, turbulence, volcanic ash, etc.
     * 
     * @param firCode Flight Information Region code (e.g., "KZBW")
     * @return List of active SIGMETs
     */
    List<SigmetReport> getSigmets(String firCode);

    /**
     * Get AIRMETs (Airmen's Meteorological Information).
     * AIRMETs indicate moderate weather: icing, turbulence, low visibility.
     * 
     * @param firCode Flight Information Region code
     * @return List of active AIRMETs
     */
    List<AirmetReport> getAirmets(String firCode);

    /**
     * Get winds aloft for a route.
     * Critical for FMC performance calculations.
     * 
     * @param routeWaypoints List of waypoints along route
     * @param flightLevels Flight levels to get winds for (e.g., [300, 340, 380])
     * @return Wind data for the route
     */
    WindsAloft getWindsAloft(List<String> routeWaypoints, List<Integer> flightLevels);

    /**
     * Request weather update via datalink.
     * Aircraft sends request, ground station responds.
     */
    void requestWeatherUpdate(String icaoCode);

    /**
     * METAR Report.
     */
    record MetarReport(
        String icaoCode,
        String rawText,
        Instant observationTime,
        int windDirection,
        int windSpeed,
        int windGust,
        int visibility,
        String presentWeather,
        int cloudBase,
        int temperature,
        int dewpoint,
        int altimeter,
        String remarks
    ) {
        /**
         * Parse flight category from METAR.
         */
        public FlightCategory getFlightCategory() {
            if (visibility >= 5 && cloudBase >= 3000) {
                return FlightCategory.VFR;
            } else if (visibility >= 3 && cloudBase >= 1000) {
                return FlightCategory.MVFR;
            } else if (visibility >= 1 && cloudBase >= 500) {
                return FlightCategory.IFR;
            } else {
                return FlightCategory.LIFR;
            }
        }

        public enum FlightCategory {
            VFR,   // Visual Flight Rules
            MVFR,  // Marginal VFR
            IFR,   // Instrument Flight Rules
            LIFR   // Low IFR
        }
    }

    /**
     * TAF Report.
     */
    record TafReport(
        String icaoCode,
        String rawText,
        Instant issueTime,
        Instant validFrom,
        Instant validTo,
        List<TafForecast> forecasts
    ) {
        public record TafForecast(
            Instant from,
            Instant to,
            int windDirection,
            int windSpeed,
            int visibility,
            String weather,
            int cloudBase,
            int probability  // For PROB30/PROB40 forecasts
        ) {}
    }

    /**
     * SIGMET Report.
     */
    record SigmetReport(
        String firCode,
        String sequenceNumber,
        SigmetType type,
        String rawText,
        Instant issueTime,
        Instant validFrom,
        Instant validTo,
        List<Coordinate> affectedArea,
        String phenomenon,
        int severity
    ) {
        public enum SigmetType {
            TS,     // Thunderstorm
            TB,     // Turbulence
            ICE,    // Icing
            VA,     // Volcanic Ash
            SS,     // Sandstorm/Duststorm
            DS,
            FC,     // Tropical Cyclone
            RDOACT, // Radioactive cloud
            MTW     // Mountain waves
        }

        public record Coordinate(double lat, double lon) {}
    }

    /**
     * AIRMET Report.
     */
    record AirmetReport(
        String firCode,
        String sequenceNumber,
        AirmetType type,
        String rawText,
        Instant issueTime,
        Instant validFrom,
        Instant validTo,
        List<SigmetReport.Coordinate> affectedArea
    ) {
        public enum AirmetType {
            SI,  // Moderate icing
            TI,  // Moderate turbulence
            MT,  // Mountain obscuration
            IF   // Instrument flight rules (low visibility)
        }
    }

    /**
     * Winds Aloft data.
     */
    record WindsAloft(
        String routeId,
        Instant validTime,
        List<WindLevel> windLevels
    ) {
        public record WindLevel(
            int flightLevel,
            int windDirection,
            int windSpeed,
            int temperature
        ) {}
    }
}
