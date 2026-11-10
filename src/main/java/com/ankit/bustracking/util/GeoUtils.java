package com.ankit.bustracking.util;

import com.ankit.bustracking.model.RouteStop;
import com.ankit.bustracking.model.Stop;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * GPS maths: distance, speed, bearing and live route progress.
 * Route progress rules (single source of truth for stops crossed, distance and ETA):
 *  1. Nothing is ticked until the bus reaches the first stop (NOT_STARTED).
 *  2. Stop radius adapts automatically to stop spacing (min(100m, max(4m, 35% of adjacent leg))) so both
 *     city bus routes (km apart) and custom walking/gali demo routes (10m–50m apart) tick one stop at a time.
 *  3. If the bus is far from the line between its last and next stop it is OFF_ROUTE: no ETA is shown.
 *  4. ETA = remaining distance / live speed (uses live walking/driving speed >= 1 km/h, or 25 km/h fallback when stationary).
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double MAX_REALISTIC_SPEED_KMH = 120.0;
    private static final double JITTER_METERS = 1.5;
    public static final double DEFAULT_CITY_SPEED_KMH = 25.0;
    public static final double ROAD_WINDING_FACTOR = 1.22;
    public static final double MIN_MOVING_SPEED_KMH = 1.0;
    /** Maximum radius for standard city stops; automatically scaled down when stops are closely spaced. */
    public static final double STOP_RADIUS_METERS = 100.0;
    public static final double MIN_STOP_RADIUS_METERS = 4.0;
    /** Minimum allowed distance from the stop-to-stop line before the bus is called off-route. */
    public static final double MIN_CORRIDOR_METERS = 300.0;
    /** Maximum distance from the origin stop allowed when starting a trip. */
    public static final double START_TRIP_GEOFENCE_METERS = 300.0;

    private GeoUtils() {
    }

    /** Great-circle distance between two coordinates in meters (Haversine formula). */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);

        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Compass bearing in degrees (0..360) from point 1 to point 2. */
    public static Double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        if (distanceMeters(lat1, lon1, lat2, lon2) < JITTER_METERS) {
            return null;
        }
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(dLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);

        double theta = Math.toDegrees(Math.atan2(y, x));
        return round1((theta + 360.0) % 360.0);
    }

    /** Speed in km/h derived from two consecutive GPS fixes. */
    public static Double speedKmh(double lat1, double lon1, Instant time1,
                                  double lat2, double lon2, Instant time2) {
        if (time1 == null || time2 == null) {
            return null;
        }

        double seconds = Duration.between(time1, time2).toMillis() / 1000.0;
        if (seconds < 0.5 || seconds > 300.0) {
            return null;
        }

        double meters = distanceMeters(lat1, lon1, lat2, lon2);
        if (meters < JITTER_METERS) {
            return 0.0;
        }

        double kmh = (meters / seconds) * 3.6;
        if (kmh > MAX_REALISTIC_SPEED_KMH) {
            return null;
        }
        return kmh;
    }

    /** Exponential moving average (EMA) filter for vehicle/walking speed. */
    public static double smoothSpeedKmh(Double measuredKmh, Double previousKmh) {
        if (measuredKmh == null || !Double.isFinite(measuredKmh) || measuredKmh < 0) {
            return previousKmh != null && Double.isFinite(previousKmh) ? round1(previousKmh) : 0.0;
        }
        if (measuredKmh < 0.5) {
            return 0.0;
        }
        if (previousKmh == null || !Double.isFinite(previousKmh) || previousKmh <= 0.5) {
            return round1(Math.min(measuredKmh, MAX_REALISTIC_SPEED_KMH));
        }
        double blended = (0.65 * measuredKmh) + (0.35 * previousKmh);
        return round1(Math.min(blended, MAX_REALISTIC_SPEED_KMH));
    }

    public static double round1(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.round(value * 10.0) / 10.0;
    }

    public static double roundCoordinate(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String ON_ROUTE = "ON_ROUTE";
    public static final String OFF_ROUTE = "OFF_ROUTE";
    public static final String ARRIVED = "ARRIVED";

    public static class RouteProgress {
        public String state = NOT_STARTED;
        /** Number of stops already crossed; the next stop is index {@code stopsCrossed}. */
        public int stopsCrossed;
        public String previousStop;
        public String currentStop;
        public String nextStop;
        public String destinationStop;
        public double totalMeters;
        public double travelledMeters;
        public double remainingMeters;
        public double toNextStopMeters;
        public double distanceFromRouteMeters;
        public double progressPercent;
        public Double etaMinutes;
        public Double nextStopEtaMinutes;
        /** ETA per stop index (null for stops already crossed, or when no honest ETA exists). */
        public Double[] stopEtaMinutes;
    }

    /** {t, offMeters}: t = position on a-b (0 at a, 1 at b, unclamped); offMeters = distance from the point to segment a-b. */
    private static double[] project(Stop a, Stop b, double lat, double lon) {
        double k = Math.cos(Math.toRadians(a.getLatitude()));
        double dLon = b.getLongitude() - a.getLongitude();
        double dLat = b.getLatitude() - a.getLatitude();
        double len2 = dLon * k * dLon * k + dLat * dLat;
        double t = len2 == 0.0 ? 0.0
                : ((lon - a.getLongitude()) * k * dLon * k + (lat - a.getLatitude()) * dLat) / len2;
        double c = Math.max(0.0, Math.min(1.0, t));
        double off = distanceMeters(lat, lon, a.getLatitude() + dLat * c, a.getLongitude() + dLon * c);
        return new double[]{t, off};
    }

    private static double meters(Stop a, Stop b) {
        return distanceMeters(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
    }

    private static double corridor(Stop a, Stop b) {
        return Math.max(MIN_CORRIDOR_METERS, 0.3 * meters(a, b));
    }

    /** Adaptive stop capture radius: up to 100m on city routes, or 35% of shortest adjacent leg (min 4m) on closely spaced stops. */
    public static double stopRadius(List<Stop> stops, int idx) {
        double minLeg = Double.POSITIVE_INFINITY;
        if (idx > 0) minLeg = Math.min(minLeg, meters(stops.get(idx - 1), stops.get(idx)));
        if (idx + 1 < stops.size()) minLeg = Math.min(minLeg, meters(stops.get(idx), stops.get(idx + 1)));
        if (!Double.isFinite(minLeg)) return STOP_RADIUS_METERS;
        return Math.min(STOP_RADIUS_METERS, Math.max(MIN_STOP_RADIUS_METERS, minLeg * 0.35));
    }

    private static String name(List<Stop> stops, int i) {
        return i >= 0 && i < stops.size() ? stops.get(i).getStopName() : null;
    }

    public static RouteProgress computeRouteProgress(List<RouteStop> orderedStops, Double busLat, Double busLon,
                                                     Double speedKmh, Integer stopsCrossedBefore) {
        RouteProgress p = new RouteProgress();
        if (orderedStops == null || orderedStops.isEmpty()) {
            return p;
        }
        List<Stop> stops = orderedStops.stream().map(RouteStop::getStop).toList();
        int n = stops.size();

        double[] rawCum = new double[n];
        for (int i = 1; i < n; i++) {
            rawCum[i] = rawCum[i - 1] + meters(stops.get(i - 1), stops.get(i));
        }
        // For short custom routes (< 500m), use exact straight-line distance (1.0x); for city road routes use 1.22x winding factor.
        double winding = rawCum[n - 1] < 500.0 ? 1.0 : ROAD_WINDING_FACTOR;
        double[] cum = new double[n];
        for (int i = 1; i < n; i++) {
            cum[i] = rawCum[i] * winding;
        }
        p.totalMeters = round1(cum[n - 1]);
        p.destinationStop = name(stops, n - 1);

        int crossed = stopsCrossedBefore == null ? 0 : Math.max(0, Math.min(n, stopsCrossedBefore));
        boolean hasFix = busLat != null && busLon != null;

        // 1. Cross stops in order: reached (within adaptive radius) or clearly passed on the way to the following stop.
        while (hasFix && crossed < n) {
            Stop stop = stops.get(crossed);
            double radius = stopRadius(stops, crossed);
            boolean reached = distanceMeters(busLat, busLon, stop.getLatitude(), stop.getLongitude()) <= radius;
            boolean passed = false;
            if (crossed >= 1 && crossed + 1 < n) {
                Stop before = stops.get(crossed - 1);
                Stop after = stops.get(crossed + 1);
                double[] ahead = project(stop, after, busLat, busLon);
                double[] arriving = project(before, stop, busLat, busLon);
                boolean stillArriving = arriving[0] < 1.0 && arriving[1] <= corridor(before, stop);
                passed = !stillArriving && ahead[0] > 0.0 && ahead[0] < 1.0 && ahead[1] <= corridor(stop, after);
            }
            if (!reached && !passed) {
                break;
            }
            crossed++;
        }
        p.stopsCrossed = crossed;
        p.previousStop = name(stops, crossed - 1);
        p.nextStop = name(stops, crossed);
        if (hasFix && crossed >= 1 && distanceMeters(busLat, busLon, stops.get(crossed - 1).getLatitude(),
                stops.get(crossed - 1).getLongitude()) <= stopRadius(stops, crossed - 1)) {
            p.currentStop = p.previousStop;
        }

        // 2. State, distances and ETA.
        if (crossed >= n) {
            p.state = ARRIVED;
            p.travelledMeters = p.totalMeters;
            p.progressPercent = 100.0;
            p.etaMinutes = 0.0;
            p.nextStopEtaMinutes = 0.0;
            return p;
        }
        if (!hasFix) {
            p.remainingMeters = p.totalMeters;
            return p;
        }

        Stop next = stops.get(crossed);
        p.toNextStopMeters = round1(distanceMeters(busLat, busLon, next.getLatitude(), next.getLongitude()) * winding);
        if (crossed == 0) {
            // Bus has not reached the first stop yet: nothing is ticked, no route ETA is promised.
            p.state = NOT_STARTED;
            p.distanceFromRouteMeters = round1(distanceMeters(busLat, busLon, next.getLatitude(), next.getLongitude()));
            p.toNextStopMeters = p.distanceFromRouteMeters;
            p.remainingMeters = p.totalMeters;
            return p;
        }

        Stop prev = stops.get(crossed - 1);
        p.distanceFromRouteMeters = round1(project(prev, next, busLat, busLon)[1]);
        boolean onRoute = p.distanceFromRouteMeters <= corridor(prev, next);
        p.state = onRoute ? ON_ROUTE : OFF_ROUTE;
        double remaining = onRoute ? p.toNextStopMeters + (cum[n - 1] - cum[crossed]) : cum[n - 1] - cum[crossed - 1];
        p.remainingMeters = round1(remaining);
        p.travelledMeters = round1(Math.max(0.0, p.totalMeters - remaining));
        p.progressPercent = p.totalMeters > 0 ? round1(Math.min(100.0, p.travelledMeters / p.totalMeters * 100.0)) : 0.0;
        if (!onRoute) {
            return p;
        }
        double speed = speedKmh != null && speedKmh >= MIN_MOVING_SPEED_KMH ? speedKmh : DEFAULT_CITY_SPEED_KMH;
        p.stopEtaMinutes = new Double[n];
        for (int j = crossed; j < n; j++) {
            p.stopEtaMinutes[j] = round1((p.toNextStopMeters + cum[j] - cum[crossed]) / 1000.0 / speed * 60.0);
        }
        p.nextStopEtaMinutes = p.stopEtaMinutes[crossed];
        p.etaMinutes = p.stopEtaMinutes[n - 1];
        return p;
    }
}
