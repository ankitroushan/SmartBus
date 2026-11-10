package com.ankit.bustracking;

import com.ankit.bustracking.model.RouteStop;
import com.ankit.bustracking.model.Stop;
import com.ankit.bustracking.util.GeoUtils;
import com.ankit.bustracking.util.GeoUtils.RouteProgress;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoRouteProgressTests {

    private static final double[][] KOLKATA = {
            {22.4425, 88.4263}, {22.4660, 88.3841}, {22.4730, 88.3770}, {22.4960, 88.3705}, {22.5100, 88.3650}
    };
    private static final String[] NAMES = {"Sonarpur Station", "Garia", "Kavi Nazrul", "Jadavpur 8B", "New Garia"};

    private static List<RouteStop> route(double[][] points) {
        List<RouteStop> list = new ArrayList<>();
        for (int i = 0; i < points.length; i++) {
            Stop stop = new Stop();
            stop.setStopName(NAMES[i]);
            stop.setLatitude(points[i][0]);
            stop.setLongitude(points[i][1]);
            RouteStop rs = new RouteStop();
            rs.setStop(stop);
            rs.setSequenceNo((long) i + 1);
            list.add(rs);
        }
        return list;
    }

    private static double[] between(double[] a, double[] b, double f) {
        return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f};
    }

    private static RouteProgress at(double[] p, double speed, int crossed) {
        return GeoUtils.computeRouteProgress(route(KOLKATA), p[0], p[1], speed, crossed);
    }

    @Test
    void busInAnotherStateTicksNothingAndPromisesNoEta() {
        RouteProgress p = at(new double[]{24.4835, 86.7002}, 0.0, 0);
        assertEquals(GeoUtils.NOT_STARTED, p.state);
        assertEquals(0, p.stopsCrossed);
        assertNull(p.previousStop);
        assertNull(p.currentStop);
        assertEquals("Sonarpur Station", p.nextStop);
        assertNull(p.etaMinutes);
        assertNull(p.nextStopEtaMinutes);
        assertTrue(p.distanceFromRouteMeters > 200_000);
    }

    @Test
    void reachingTheFirstStopStartsTheJourney() {
        RouteProgress p = at(KOLKATA[0], 0.0, 0);
        assertEquals(1, p.stopsCrossed);
        assertEquals(GeoUtils.ON_ROUTE, p.state);
        assertEquals("Sonarpur Station", p.currentStop);
        assertEquals("Garia", p.nextStop);
        assertNotNull(p.etaMinutes);
    }

    @Test
    void etaUsesSpeedWhenMovingAndCityDefaultWhenStationary() {
        double[] mid = between(KOLKATA[0], KOLKATA[1], 0.5);
        RouteProgress still = at(mid, 0.0, 1);
        RouteProgress fast = at(mid, 50.0, 1);
        assertEquals(1, still.stopsCrossed);
        assertEquals(still.remainingMeters / 1000.0 / GeoUtils.DEFAULT_CITY_SPEED_KMH * 60.0, still.etaMinutes, 0.2);
        assertEquals(fast.remainingMeters / 1000.0 / 50.0 * 60.0, fast.etaMinutes, 0.2);
        assertTrue(fast.etaMinutes < still.etaMinutes);
    }

    @Test
    void everyUpcomingStopGetsAnIncreasingEtaAndCrossedStopsGetNone() {
        RouteProgress p = at(between(KOLKATA[1], KOLKATA[2], 0.5), 30.0, 2);
        assertNull(p.stopEtaMinutes[0]);
        assertNull(p.stopEtaMinutes[1]);
        assertEquals(p.nextStopEtaMinutes, p.stopEtaMinutes[2]);
        assertEquals(p.etaMinutes, p.stopEtaMinutes[4]);
        assertTrue(p.stopEtaMinutes[2] < p.stopEtaMinutes[3] && p.stopEtaMinutes[3] < p.stopEtaMinutes[4]);
        assertNull(at(new double[]{24.4835, 86.7002}, 0.0, 0).stopEtaMinutes);
    }

    @Test
    void leavingTheRouteGivesOffRouteAndNoEta() {
        double[] mid = between(KOLKATA[0], KOLKATA[1], 0.5);
        RouteProgress p = at(new double[]{mid[0] + 0.03, mid[1] + 0.03}, 30.0, 1);
        assertEquals(GeoUtils.OFF_ROUTE, p.state);
        assertEquals(1, p.stopsCrossed);
        assertNull(p.etaMinutes);
        assertEquals(0.0, p.travelledMeters, 0.0);
    }

    @Test
    void stopPassedBetweenSparseFixesIsStillCrossed() {
        RouteProgress p = at(between(KOLKATA[1], KOLKATA[2], 0.5), 30.0, 1);
        assertEquals(2, p.stopsCrossed);
        assertEquals("Garia", p.previousStop);
        assertNull(p.currentStop);
        assertEquals("Kavi Nazrul", p.nextStop);
    }

    @Test
    void stopsAreNeverSkippedAndNeverUncrossed() {
        RouteProgress jump = at(KOLKATA[4], 30.0, 1);
        assertEquals(1, jump.stopsCrossed);
        assertEquals(GeoUtils.OFF_ROUTE, jump.state);

        RouteProgress back = at(KOLKATA[0], 0.0, 3);
        assertEquals(3, back.stopsCrossed);
    }

    @Test
    void lastStopEndsTheJourney() {
        RouteProgress p = at(KOLKATA[4], 0.0, 4);
        assertEquals(5, p.stopsCrossed);
        assertEquals(GeoUtils.ARRIVED, p.state);
        assertEquals(0.0, p.etaMinutes, 0.0);
        assertEquals(100.0, p.progressPercent, 0.0);
    }

    @Test
    void drivingTheWholeRouteCrossesEveryStopInOrder() {
        int crossed = 0;
        for (int leg = 0; leg < KOLKATA.length - 1; leg++) {
            for (int step = 0; step <= 40; step++) {
                RouteProgress p = GeoUtils.computeRouteProgress(route(KOLKATA),
                        between(KOLKATA[leg], KOLKATA[leg + 1], step / 40.0)[0],
                        between(KOLKATA[leg], KOLKATA[leg + 1], step / 40.0)[1], 35.0, crossed);
                assertTrue(p.stopsCrossed >= crossed && p.stopsCrossed <= crossed + 1);
                assertTrue(p.state.equals(GeoUtils.ON_ROUTE) || p.state.equals(GeoUtils.ARRIVED));
                crossed = p.stopsCrossed;
            }
        }
        assertEquals(KOLKATA.length, crossed);
    }

    @Test
    void aNewRouteAroundTheUsersCurrentLocationTracksCorrectly() {
        double[][] local = {{24.48500, 86.69480}, {24.48725, 86.69480}, {24.48950, 86.69480}};
        List<RouteStop> r = route(local);
        RouteProgress p = GeoUtils.computeRouteProgress(r, 24.48500, 86.69480, 0.0, 0);
        assertEquals(1, p.stopsCrossed);
        p = GeoUtils.computeRouteProgress(r, 24.48600, 86.69482, 20.0, p.stopsCrossed);
        assertEquals(1, p.stopsCrossed);
        assertEquals(GeoUtils.ON_ROUTE, p.state);
        p = GeoUtils.computeRouteProgress(r, 24.48720, 86.69480, 20.0, p.stopsCrossed);
        assertEquals(2, p.stopsCrossed);
        assertEquals(NAMES[1], p.currentStop);
    }

    @Test
    void outAndBackRoadDoesNotTickTheTerminusEarly() {
        double[][] outAndBack = {{22.50, 88.30}, {22.52, 88.30}, {22.54, 88.30}, {22.5201, 88.3001}, {22.5001, 88.3001}};
        List<RouteStop> r = route(outAndBack);
        RouteProgress p = GeoUtils.computeRouteProgress(r, 22.532, 88.30, 30.0, 2);
        assertEquals(2, p.stopsCrossed);
        assertEquals(GeoUtils.ON_ROUTE, p.state);
    }

    @Test
    void customShortGaliRouteWith10To50MeterStopsTicksOneStopAtATimeWhileWalking() {
        double[][] gali = {
            {24.48500, 86.69480},
            {24.48522, 86.69480},
            {24.48545, 86.69480},
            {24.48567, 86.69480},
            {24.48590, 86.69480}
        };
        List<RouteStop> r = route(gali);

        RouteProgress p = GeoUtils.computeRouteProgress(r, 24.48500, 86.69480, 4.5, 0);
        assertEquals(1, p.stopsCrossed);
        assertEquals(GeoUtils.ON_ROUTE, p.state);
        assertEquals(NAMES[0], p.currentStop);
        assertEquals(NAMES[1], p.nextStop);
        assertNotNull(p.nextStopEtaMinutes);

        p = GeoUtils.computeRouteProgress(r, 24.48511, 86.69480, 4.5, p.stopsCrossed);
        assertEquals(1, p.stopsCrossed);
        assertNull(p.currentStop);
        assertTrue(p.travelledMeters > 10.0 && p.travelledMeters < 15.0);

        p = GeoUtils.computeRouteProgress(r, 24.48522, 86.69480, 4.5, p.stopsCrossed);
        assertEquals(2, p.stopsCrossed);
        assertEquals(NAMES[1], p.currentStop);
        assertEquals(NAMES[2], p.nextStop);
    }
}
