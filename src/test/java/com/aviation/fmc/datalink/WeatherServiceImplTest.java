package com.aviation.fmc.datalink;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WeatherServiceImpl.
 * Tests pub/sub, data generation, and realistic weather simulation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WeatherServiceImplTest {

    private WeatherServiceImpl weatherService;

    @BeforeEach
    void setUp() {
        weatherService = new WeatherServiceImpl();
        weatherService.start();
    }

    @Test
    void testGetMetarReturnsData() {
        Optional<WeatherService.MetarReport> metar = weatherService.getMetar("KJFK");

        assertTrue(metar.isPresent());
        assertEquals("KJFK", metar.get().icaoCode());
        assertNotNull(metar.get().rawText());
        assertTrue(metar.get().windSpeed() >= 0);
        assertTrue(metar.get().temperature() > -50 && metar.get().temperature() < 50);
    }

    @Test
    void testGetTafReturnsData() {
        Optional<WeatherService.TafReport> taf = weatherService.getTaf("KLAX");

        assertTrue(taf.isPresent());
        assertEquals("KLAX", taf.get().icaoCode());
        assertNotNull(taf.get().rawText());
        assertNotNull(taf.get().forecasts());
        assertTrue(taf.get().forecasts().size() > 0);
    }

    @Test
    void testGetSigmets() {
        List<WeatherService.SigmetReport> sigmets = weatherService.getSigmets("KZBW");

        assertNotNull(sigmets);
        // SIGMETs are probabilistic, just verify no crash
    }

    @Test
    void testGetAirmets() {
        List<WeatherService.AirmetReport> airmets = weatherService.getAirmets("KZNY");

        assertNotNull(airmets);
    }

    @Test
    void testGetWindsAloft() {
        List<String> waypoints = List.of("KJFK", "KBOS", "KORD");
        List<Integer> levels = List.of(300, 340, 380);

        WeatherService.WindsAloft winds = weatherService.getWindsAloft(waypoints, levels);

        assertNotNull(winds);
        assertEquals("KJFK-KBOS-KORD", winds.routeId());
        assertNotNull(winds.windLevels());
        assertEquals(3, winds.windLevels().size());

        for (WeatherService.WindsAloft.WindLevel level : winds.windLevels()) {
            assertTrue(level.flightLevel() >= 300);
            assertTrue(level.windDirection() >= 0 && level.windDirection() < 360);
            assertTrue(level.windSpeed() >= 0);
        }
    }

    @Test
    void testRequestWeatherUpdate() {
        AtomicInteger updateCount = new AtomicInteger(0);

        weatherService.subscribeToMetar("KJFK", metar -> {
            updateCount.incrementAndGet();
        });

        // Request update
        weatherService.requestWeatherUpdate("KJFK");

        // Should have received at least one update
        assertTrue(updateCount.get() >= 1);
    }

    @Test
    void testPubSubMetarSubscription() {
        AtomicInteger receivedCount = new AtomicInteger(0);
        WeatherService.MetarReport[] lastMetar = new WeatherService.MetarReport[1];

        weatherService.subscribeToMetar("KLAX", metar -> {
            receivedCount.incrementAndGet();
            lastMetar[0] = metar;
        });

        // Trigger update
        weatherService.requestWeatherUpdate("KLAX");

        assertTrue(receivedCount.get() >= 1);
        assertNotNull(lastMetar[0]);
        assertEquals("KLAX", lastMetar[0].icaoCode());
    }

    @Test
    void testPubSubWindsSubscription() {
        AtomicInteger receivedCount = new AtomicInteger(0);

        weatherService.subscribeToWinds(winds -> {
            receivedCount.incrementAndGet();
            assertNotNull(winds.routeId());
        });

        // Get winds (triggers notification)
        weatherService.getWindsAloft(List.of("KJFK", "KORD"), List.of(340));

        assertTrue(receivedCount.get() >= 1);
    }

    @Test
    void testMetarFlightCategory() {
        Optional<WeatherService.MetarReport> metar = weatherService.getMetar("KJFK");
        assertTrue(metar.isPresent());

        WeatherService.MetarReport.FlightCategory category = metar.get().getFlightCategory();
        assertNotNull(category);
        // Category should be one of the enum values
        assertTrue(List.of(
            WeatherService.MetarReport.FlightCategory.VFR,
            WeatherService.MetarReport.FlightCategory.MVFR,
            WeatherService.MetarReport.FlightCategory.IFR,
            WeatherService.MetarReport.FlightCategory.LIFR
        ).contains(category));
    }

    @Test
    void testUnsubscribe() {
        AtomicInteger count = new AtomicInteger(0);

        java.util.function.Consumer<WeatherService.MetarReport> sub = metar -> count.incrementAndGet();

        weatherService.subscribeToMetar("KJFK", sub);
        weatherService.requestWeatherUpdate("KJFK");
        int afterFirst = count.get();

        weatherService.unsubscribeAll(sub);
        weatherService.requestWeatherUpdate("KJFK");
        int afterSecond = count.get();

        // After unsubscribe, count should not increase
        assertEquals(afterFirst, afterSecond);
    }

    @Test
    void testStopService() {
        weatherService.stop();
        // Should not throw
        assertTrue(true);
    }
}
