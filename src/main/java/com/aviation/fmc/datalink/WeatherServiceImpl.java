package com.aviation.fmc.datalink;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Real implementation of WeatherService.
 * Simulates aviation weather data with pub/sub capability.
 * 
 * Features:
 * - METAR, TAF, SIGMET, AIRMET generation
 * - Winds Aloft simulation for flight planning
 * - Pub/sub pattern for weather updates (like ACARS)
 * - Realistic simulated data based on airport/season
 * 
 * In production, this would connect to real weather providers.
 */
@Slf4j
public class WeatherServiceImpl implements WeatherService {

    // Weather data cache (simulated "database")
    private final Map<String, MetarReport> metarCache;
    private final Map<String, TafReport> tafCache;
    private final Map<String, List<SigmetReport>> sigmetCache;
    private final Map<String, List<AirmetReport>> airmetCache;

    // Pub/Sub subscribers
    private final Map<String, List<Consumer<MetarReport>>> metarSubscribers;
    private final Map<String, List<Consumer<TafReport>>> tafSubscribers;
    private final List<Consumer<WindsAloft>> windsSubscribers;
    private final List<Consumer<List<SigmetReport>>> sigmetSubscribers;
    private final List<Consumer<List<AirmetReport>>> airmetSubscribers;

    // Simulation settings
    private final Random random;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public WeatherServiceImpl() {
        this.metarCache = new ConcurrentHashMap<>();
        this.tafCache = new ConcurrentHashMap<>();
        this.sigmetCache = new ConcurrentHashMap<>();
        this.airmetCache = new ConcurrentHashMap<>();

        this.metarSubscribers = new ConcurrentHashMap<>();
        this.tafSubscribers = new ConcurrentHashMap<>();
        this.windsSubscribers = new CopyOnWriteArrayList<>();
        this.sigmetSubscribers = new CopyOnWriteArrayList<>();
        this.airmetSubscribers = new CopyOnWriteArrayList<>();

        this.random = new Random();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    /**
     * Start simulated weather updates.
     */
    public void start() {
        if (running) return;
        running = true;
        log.info("WeatherService started - simulating aviation weather data");
    }

    /**
     * Stop weather service.
     */
    public void stop() {
        running = false;
        scheduler.shutdownNow();
        log.info("WeatherService stopped");
    }

    // ==================== WeatherService Implementation ====================

    @Override
    public Optional<MetarReport> getMetar(String icaoCode) {
        if (icaoCode == null) return Optional.empty();

        // Return cached or generate new
        MetarReport metar = metarCache.computeIfAbsent(icaoCode.toUpperCase(),
            this::generateMetar);
        return Optional.of(metar);
    }

    @Override
    public Optional<TafReport> getTaf(String icaoCode) {
        if (icaoCode == null) return Optional.empty();

        TafReport taf = tafCache.computeIfAbsent(icaoCode.toUpperCase(),
            this::generateTaf);
        return Optional.of(taf);
    }

    @Override
    public List<SigmetReport> getSigmets(String firCode) {
        if (firCode == null) return List.of();

        List<SigmetReport> sigmets = sigmetCache.computeIfAbsent(firCode.toUpperCase(),
            this::generateSigmets);
        return new ArrayList<>(sigmets);
    }

    @Override
    public List<AirmetReport> getAirmets(String firCode) {
        if (firCode == null) return List.of();

        List<AirmetReport> airmets = airmetCache.computeIfAbsent(firCode.toUpperCase(),
            this::generateAirmets);
        return new ArrayList<>(airmets);
    }

    @Override
    public WindsAloft getWindsAloft(List<String> routeWaypoints, List<Integer> flightLevels) {
        if (routeWaypoints == null || routeWaypoints.isEmpty()) {
            routeWaypoints = List.of("KJFK", "KBOS"); // Default route
        }
        if (flightLevels == null || flightLevels.isEmpty()) {
            flightLevels = List.of(300, 340, 380); // Default levels
        }

        List<WeatherService.WindsAloft.WindLevel> levels = new ArrayList<>();
        for (int fl : flightLevels) {
            // Simulate wind and temperature based on flight level
            int baseWindDir = 240 + random.nextInt(60); // 240-300 degrees
            int baseWindSpeed = 25 + random.nextInt(60); // 25-85 knots
            int temperature = -45 + (380 - fl) / 4; // Colder at higher altitude

            levels.add(new WeatherService.WindsAloft.WindLevel(fl, baseWindDir, baseWindSpeed, temperature));
        }

        WindsAloft winds = new WindsAloft(
            String.join("-", routeWaypoints),
            Instant.now().plusSeconds(3600), // Valid for 1 hour
            levels
        );

        // Notify subscribers
        notifyWindsSubscribers(winds);

        return winds;
    }

    @Override
    public void requestWeatherUpdate(String icaoCode) {
        log.info("Weather update requested for {}", icaoCode);

        // Generate fresh METAR
        MetarReport metar = generateMetar(icaoCode);
        metarCache.put(icaoCode.toUpperCase(), metar);

        // Notify subscribers
        notifyMetarSubscribers(icaoCode.toUpperCase(), metar);

        log.info("Weather update published for {}: {}°/{}kt, {}°C",
            icaoCode, metar.windDirection(), metar.windSpeed(), metar.temperature());
    }

    // ==================== Pub/Sub Methods ====================

    /**
     * Subscribe to METAR updates for a specific airport.
     */
    public void subscribeToMetar(String icaoCode, Consumer<MetarReport> subscriber) {
        String key = icaoCode.toUpperCase();
        metarSubscribers.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
            .add(subscriber);
        log.debug("Subscribed to METAR updates for {}", key);
    }

    /**
     * Subscribe to TAF updates for a specific airport.
     */
    public void subscribeToTaf(String icaoCode, Consumer<TafReport> subscriber) {
        String key = icaoCode.toUpperCase();
        tafSubscribers.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
            .add(subscriber);
    }

    /**
     * Subscribe to winds aloft updates.
     */
    public void subscribeToWinds(Consumer<WindsAloft> subscriber) {
        windsSubscribers.add(subscriber);
        log.debug("Subscribed to winds aloft updates");
    }

    /**
     * Subscribe to SIGMET updates.
     */
    public void subscribeToSigmets(Consumer<List<SigmetReport>> subscriber) {
        sigmetSubscribers.add(subscriber);
    }

    /**
     * Subscribe to AIRMET updates.
     */
    public void subscribeToAirmets(Consumer<List<AirmetReport>> subscriber) {
        airmetSubscribers.add(subscriber);
    }

    /**
     * Unsubscribe from all updates.
     */
    public void unsubscribeAll(Consumer<?> subscriber) {
        metarSubscribers.values().forEach(list -> list.remove(subscriber));
        tafSubscribers.values().forEach(list -> list.remove(subscriber));
        windsSubscribers.remove(subscriber);
        sigmetSubscribers.remove(subscriber);
        airmetSubscribers.remove(subscriber);
    }

    // ==================== Internal Notification ====================

    private void notifyMetarSubscribers(String icao, MetarReport metar) {
        List<Consumer<MetarReport>> subscribers = metarSubscribers.get(icao);
        if (subscribers != null) {
            for (Consumer<MetarReport> sub : subscribers) {
                try {
                    sub.accept(metar);
                } catch (Exception e) {
                    log.warn("METAR subscriber error: {}", e.getMessage());
                }
            }
        }
    }

    private void notifyWindsSubscribers(WindsAloft winds) {
        for (Consumer<WindsAloft> sub : windsSubscribers) {
            try {
                sub.accept(winds);
            } catch (Exception e) {
                log.warn("Winds subscriber error: {}", e.getMessage());
            }
        }
    }

    // ==================== Data Generation (Simulation) ====================

    private MetarReport generateMetar(String icaoCode) {
        String icao = icaoCode.toUpperCase();

        // Generate realistic weather based on airport
        int windDir = 240 + random.nextInt(80); // 240-320
        int windSpeed = 5 + random.nextInt(25); // 5-30 knots
        int windGust = windSpeed > 20 ? windSpeed + random.nextInt(10) : 0;

        int visibility = random.nextInt(10) + 1; // 1-10 miles
        int cloudBase = 500 + random.nextInt(5000); // 500-5500 ft

        int temp = -10 + random.nextInt(40); // -10 to 30°C
        int dewpoint = temp - random.nextInt(15); // Dewpoint below temp
        int altimeter = 2980 + random.nextInt(60); // 29.80-30.40 inHg

        String[] weatherTypes = {"", "BR", "FG", "RA", "SN", "TS", "HZ"};
        String presentWeather = weatherTypes[random.nextInt(weatherTypes.length)];

        return new MetarReport(
            icao,
            String.format("%s %02d%02dZ %03d%02dKT %dSM %s %03d%02d %02d/%02d Q%04d",
                icao, Instant.now().atOffset(java.time.ZoneOffset.UTC).getHour(),
                Instant.now().atOffset(java.time.ZoneOffset.UTC).getMinute(),
                windDir, windSpeed, visibility,
                cloudBase > 3000 ? "FEW250" : "BKN" + cloudBase / 100,
                cloudBase, altimeter / 100, temp, dewpoint, altimeter),
            Instant.now(),
            windDir, windSpeed, windGust,
            visibility, presentWeather, cloudBase,
            temp, dewpoint, altimeter,
            "RMK AO2"
        );
    }

    private TafReport generateTaf(String icaoCode) {
        String icao = icaoCode.toUpperCase();
        Instant now = Instant.now();

        List<WeatherService.TafReport.TafForecast> forecasts = new ArrayList<>();
        Instant forecastTime = now;

        // Generate 3 forecast periods
        for (int i = 0; i < 3; i++) {
            int windDir = 240 + random.nextInt(80);
            int windSpeed = 8 + random.nextInt(20);
            int vis = 3 + random.nextInt(7);
            int clouds = 800 + random.nextInt(4000);

            forecasts.add(new WeatherService.TafReport.TafForecast(
                forecastTime,
                forecastTime.plusSeconds(6 * 3600),
                windDir, windSpeed, vis,
                random.nextBoolean() ? "RA" : "",
                clouds,
                random.nextInt(3) * 10 // 0, 10, 20 probability
            ));
            forecastTime = forecastTime.plusSeconds(6 * 3600);
        }

        return new TafReport(
            icao,
            icao + " " + now.toString().substring(0, 16) + "Z 1212/1312...",
            now,
            now,
            now.plusSeconds(24 * 3600),
            forecasts
        );
    }

    private List<SigmetReport> generateSigmets(String firCode) {
        // 20% chance of SIGMET
        if (random.nextDouble() > 0.2) {
            return List.of();
        }

        SigmetReport.SigmetType[] types = SigmetReport.SigmetType.values();
        SigmetReport.SigmetType type = types[random.nextInt(types.length)];

        return List.of(new SigmetReport(
            firCode.toUpperCase(),
            "1",
            type,
            firCode + " SIGMET 1 VALID 1212/1218... " + type.name(),
            Instant.now(),
            Instant.now(),
            Instant.now().plusSeconds(6 * 3600),
            List.of(
                new SigmetReport.Coordinate(40.0, -74.0),
                new SigmetReport.Coordinate(41.0, -72.0),
                new SigmetReport.Coordinate(39.0, -71.0)
            ),
            type == SigmetReport.SigmetType.TS ? "THUNDERSTORMS" : "TURBULENCE",
            random.nextInt(3) + 1
        ));
    }

    private List<AirmetReport> generateAirmets(String firCode) {
        // 30% chance of AIRMET
        if (random.nextDouble() > 0.3) {
            return List.of();
        }

        AirmetReport.AirmetType[] types = AirmetReport.AirmetType.values();
        AirmetReport.AirmetType type = types[random.nextInt(types.length)];

        return List.of(new AirmetReport(
            firCode.toUpperCase(),
            "A1",
            type,
            firCode + " AIRMET A1... " + type.name(),
            Instant.now(),
            Instant.now(),
            Instant.now().plusSeconds(6 * 3600),
            List.of(
                new SigmetReport.Coordinate(40.5, -74.5),
                new SigmetReport.Coordinate(41.5, -72.5)
            )
        ));
    }
}
