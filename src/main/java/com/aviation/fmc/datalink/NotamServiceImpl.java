package com.aviation.fmc.datalink;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Real implementation of NotamService.
 * Simulates NOTAM data with pub/sub capability.
 * 
 * Features:
 * - Airport, enroute, and airspace NOTAM generation
 * - FMC-relevant NOTAM filtering (runway closures, navaid outages)
 * - Pub/sub pattern for NOTAM updates (like ACARS)
 * - Realistic NOTAM text and categorization
 * 
 * In production, this would connect to FAA NOTAM system or commercial providers.
 */
@Slf4j
public class NotamServiceImpl implements NotamService {

    // NOTAM cache (simulated "database")
    private final Map<String, List<NotamReport>> notamCache;
    private final Map<String, NotamReport> notamById;

    // Pub/Sub subscribers
    private final Map<String, List<Consumer<List<NotamReport>>>> notamSubscribers;
    private final List<Consumer<RouteNotams>> routeNotamSubscribers;

    // Simulation settings
    private final Random random;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public NotamServiceImpl() {
        this.notamCache = new ConcurrentHashMap<>();
        this.notamById = new ConcurrentHashMap<>();
        this.notamSubscribers = new ConcurrentHashMap<>();
        this.routeNotamSubscribers = new CopyOnWriteArrayList<>();
        this.random = new Random();
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    /**
     * Start simulated NOTAM updates.
     */
    public void start() {
        if (running) return;
        running = true;
        log.info("NotamService started - simulating aviation NOTAMs");
    }

    /**
     * Stop NOTAM service.
     */
    public void stop() {
        running = false;
        scheduler.shutdownNow();
        log.info("NotamService stopped");
    }

    // ==================== NotamService Implementation ====================

    @Override
    public List<NotamReport> getNotamsForAirport(String icaoCode) {
        if (icaoCode == null) return List.of();

        List<NotamReport> notams = notamCache.computeIfAbsent(icaoCode.toUpperCase(),
            this::generateNotamsForAirport);
        return new ArrayList<>(notams);
    }

    @Override
    public RouteNotams getNotamsForRoute(
            String departure,
            String destination,
            List<String> alternates,
            List<String> routeWaypoints) {

        List<NotamReport> depNotams = getNotamsForAirport(departure);
        List<NotamReport> destNotams = getNotamsForAirport(destination);

        List<NotamReport> altNotams = new ArrayList<>();
        if (alternates != null) {
            for (String alt : alternates) {
                altNotams.addAll(getNotamsForAirport(alt));
            }
        }

        // Generate some enroute NOTAMs for waypoints
        List<NotamReport> enrouteNotams = new ArrayList<>();
        if (routeWaypoints != null) {
            for (String wp : routeWaypoints) {
                // 10% chance of enroute NOTAM per waypoint
                if (random.nextDouble() < 0.1) {
                    enrouteNotams.addAll(generateEnrouteNotam(wp));
                }
            }
        }

        // Collect all FMC-relevant NOTAMs
        List<NotamReport> fmcRelevant = new ArrayList<>();
        fmcRelevant.addAll(depNotams.stream().filter(NotamReport::fmcRelevant).toList());
        fmcRelevant.addAll(destNotams.stream().filter(NotamReport::fmcRelevant).toList());
        fmcRelevant.addAll(altNotams.stream().filter(NotamReport::fmcRelevant).toList());
        fmcRelevant.addAll(enrouteNotams.stream().filter(NotamReport::fmcRelevant).toList());

        RouteNotams routeNotams = new RouteNotams(
            departure, destination,
            depNotams, destNotams, altNotams, enrouteNotams, fmcRelevant
        );

        // Notify subscribers
        notifyRouteNotamSubscribers(routeNotams);

        return routeNotams;
    }

    @Override
    public Optional<NotamReport> getNotamById(String notamId) {
        return Optional.ofNullable(notamById.get(notamId));
    }

    @Override
    public List<NotamReport> getCriticalNotams(String icaoCode) {
        return getNotamsForAirport(icaoCode).stream()
            .filter(n -> n.getSeverity() == NotamSeverity.CRITICAL)
            .toList();
    }

    @Override
    public void requestNotamUpdate(String icaoCode) {
        log.info("NOTAM update requested for {}", icaoCode);

        List<NotamReport> notams = generateNotamsForAirport(icaoCode);
        notamCache.put(icaoCode.toUpperCase(), notams);

        // Store by ID
        for (NotamReport notam : notams) {
            notamById.put(notam.id(), notam);
        }

        // Notify subscribers
        notifyNotamSubscribers(icaoCode.toUpperCase(), notams);

        log.info("NOTAM update published for {}: {} NOTAMs ({} critical)",
            icaoCode, notams.size(),
            notams.stream().filter(n -> n.getSeverity() == NotamSeverity.CRITICAL).count());
    }

    // ==================== Pub/Sub Methods ====================

    /**
     * Subscribe to NOTAM updates for a specific airport.
     */
    public void subscribeToNotams(String icaoCode, Consumer<List<NotamReport>> subscriber) {
        String key = icaoCode.toUpperCase();
        notamSubscribers.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
            .add(subscriber);
        log.debug("Subscribed to NOTAM updates for {}", key);
    }

    /**
     * Subscribe to route NOTAM updates.
     */
    public void subscribeToRouteNotams(Consumer<RouteNotams> subscriber) {
        routeNotamSubscribers.add(subscriber);
        log.debug("Subscribed to route NOTAM updates");
    }

    /**
     * Unsubscribe from all NOTAM updates.
     */
    public void unsubscribeAll(Consumer<?> subscriber) {
        notamSubscribers.values().forEach(list -> list.remove(subscriber));
        routeNotamSubscribers.remove(subscriber);
    }

    // ==================== Internal Notification ====================

    private void notifyNotamSubscribers(String icao, List<NotamReport> notams) {
        List<Consumer<List<NotamReport>>> subscribers = notamSubscribers.get(icao);
        if (subscribers != null) {
            for (Consumer<List<NotamReport>> sub : subscribers) {
                try {
                    sub.accept(notams);
                } catch (Exception e) {
                    log.warn("NOTAM subscriber error: {}", e.getMessage());
                }
            }
        }
    }

    private void notifyRouteNotamSubscribers(RouteNotams routeNotams) {
        for (Consumer<RouteNotams> sub : routeNotamSubscribers) {
            try {
                sub.accept(routeNotams);
            } catch (Exception e) {
                log.warn("Route NOTAM subscriber error: {}", e.getMessage());
            }
        }
    }

    // ==================== Data Generation (Simulation) ====================

    private List<NotamReport> generateNotamsForAirport(String icaoCode) {
        String icao = icaoCode.toUpperCase();
        List<NotamReport> notams = new ArrayList<>();
        Instant now = Instant.now();

        // 40% chance of runway NOTAM
        if (random.nextDouble() < 0.4) {
            String runway = (random.nextInt(4) + 1) * 9 + (random.nextBoolean() ? "L" : "R");
            NotamReport runwayNotam = createNotam(
                icao + "-RWY-" + runway,
                icao,
                NotamType.NOTAMN,
                NotamScope.AERODROME,
                "RWY " + runway + " CLSD DUE MAINT",
                now.minusSeconds(3600),
                now,
                now.plusSeconds(48 * 3600),
                "RWY",
                "RWY " + runway,
                "CLSD DUE MAINT",
                true
            );
            notams.add(runwayNotam);
            notamById.put(runwayNotam.id(), runwayNotam);
        }

        // 20% chance of navaid NOTAM
        if (random.nextDouble() < 0.2) {
            String navaid = icao.substring(1) + random.nextInt(10);
            NotamReport navNotam = createNotam(
                icao + "-NAV-" + navaid,
                icao,
                NotamType.NOTAMN,
                NotamScope.AERODROME,
                "ILS " + navaid + " OOS",
                now.minusSeconds(1800),
                now,
                now.plusSeconds(24 * 3600),
                "NAV",
                "ILS " + navaid,
                "OUT OF SERVICE",
                true
            );
            notams.add(navNotam);
            notamById.put(navNotam.id(), navNotam);
        }

        // 15% chance of procedure NOTAM
        if (random.nextDouble() < 0.15) {
            String proc = random.nextBoolean() ? "SID" : "STAR";
            NotamReport procNotam = createNotam(
                icao + "-" + proc + "-" + random.nextInt(100),
                icao,
                NotamType.NOTAMN,
                NotamScope.AERODROME,
                proc + " " + proc + random.nextInt(5) + " REVISED",
                now.minusSeconds(7200),
                now,
                now.plusSeconds(72 * 3600),
                proc,
                proc + " procedures",
                "REVISED",
                true
            );
            notams.add(procNotam);
            notamById.put(procNotam.id(), procNotam);
        }

        // 10% chance of airspace NOTAM
        if (random.nextDouble() < 0.1) {
            NotamReport airspaceNotam = createNotam(
                icao + "-AIRSPACE",
                icao,
                NotamType.NOTAMN,
                NotamScope.AERODROME,
                "AIRSPACE RESTRICTIONS IN EFFECT",
                now.minusSeconds(3600),
                now,
                now.plusSeconds(12 * 3600),
                "AIRSPACE",
                "Airspace",
                "RESTRICTIONS IN EFFECT",
                true
            );
            notams.add(airspaceNotam);
            notamById.put(airspaceNotam.id(), airspaceNotam);
        }

        return notams;
    }

    private List<NotamReport> generateEnrouteNotam(String waypoint) {
        List<NotamReport> notams = new ArrayList<>();
        Instant now = Instant.now();

        // Generate a waypoint-related NOTAM
        NotamReport wpNotam = createNotam(
            "ENR-" + waypoint + "-" + random.nextInt(1000),
            waypoint,
            NotamType.NOTAMN,
            NotamScope.ENROUTE,
            "WAYPOINT " + waypoint + " TEMPORARILY MOVED",
            now.minusSeconds(3600),
            now,
            now.plusSeconds(24 * 3600),
            "WAYPOINT",
            "Waypoint " + waypoint,
            "TEMPORARILY MOVED",
            true
        );
        notams.add(wpNotam);
        notamById.put(wpNotam.id(), wpNotam);

        return notams;
    }

    private NotamReport createNotam(
            String id,
            String icaoCode,
            NotamType type,
            NotamScope scope,
            String rawText,
            Instant issueTime,
            Instant validFrom,
            Instant validTo,
            String keyword,
            String subject,
            String condition,
            boolean fmcRelevant) {

        return new NotamReport(
            id,
            icaoCode.toUpperCase(),
            type,
            scope,
            rawText,
            issueTime,
            validFrom,
            validTo,
            null,  // cancellationTime
            keyword,
            subject,
            condition,
            1,     // classification: Domestic
            fmcRelevant
        );
    }
}
