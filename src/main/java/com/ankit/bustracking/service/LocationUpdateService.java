package com.ankit.bustracking.service;

import com.ankit.bustracking.dto.LocationResponseDTO;
import com.ankit.bustracking.dto.LocationUpdateRequestDTO;
import com.ankit.bustracking.enums.TripStatus;
import com.ankit.bustracking.exception.DriverNotMatchedException;
import com.ankit.bustracking.exception.TripNotFoundException;
import com.ankit.bustracking.exception.TripStatusException;
import com.ankit.bustracking.model.Route;
import com.ankit.bustracking.model.RouteStop;
import com.ankit.bustracking.model.Trip;
import com.ankit.bustracking.repository.TripRepository;
import com.ankit.bustracking.util.GeoUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class LocationUpdateService {

    public static final double MAX_ALLOWED_ACCURACY_METERS = 150.0;
    private static final double MAX_ALLOWED_SPEED_MPS = 100.0;
    private static final long MAX_FUTURE_SKEW_SECONDS = 30L;
    private static final long MAX_PAST_FIX_AGE_SECONDS = 300L;

    private final TripRepository tripRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public LocationUpdateService(TripRepository tripRepository, SimpMessagingTemplate messagingTemplate) {
        this.tripRepository = tripRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public void updateLocation(LocationUpdateRequestDTO location, String driverId) {
        if (driverId == null || driverId.isBlank()) {
            throw new DriverNotMatchedException("Authenticated driver identity is required");
        }

        if (location == null || location.getLatitude() == null || location.getLongitude() == null
                || !Double.isFinite(location.getLatitude()) || !Double.isFinite(location.getLongitude())
                || location.getLatitude() < -90.0 || location.getLatitude() > 90.0
                || location.getLongitude() < -180.0 || location.getLongitude() > 180.0
                || (Math.abs(location.getLatitude()) < 1e-6 && Math.abs(location.getLongitude()) < 1e-6)) {
            throw new IllegalArgumentException("Invalid GPS coordinates");
        }

        if (location.getAccuracy() != null) {
            if (!Double.isFinite(location.getAccuracy())
                    || location.getAccuracy() < 0.0
                    || location.getAccuracy() > MAX_ALLOWED_ACCURACY_METERS) {
                throw new IllegalArgumentException("GPS accuracy is invalid or too inaccurate");
            }
        }

        if (location.getSpeed() != null) {
            if (!Double.isFinite(location.getSpeed())
                    || location.getSpeed() < 0.0
                    || location.getSpeed() > MAX_ALLOWED_SPEED_MPS) {
                throw new IllegalArgumentException("Invalid GPS speed");
            }
        }

        if (location.getHeading() != null) {
            if (!Double.isFinite(location.getHeading())
                    || location.getHeading() < 0.0
                    || location.getHeading() > 360.0) {
                throw new IllegalArgumentException("Invalid GPS heading");
            }
        }

        if (location.getCapturedAt() == null) {
            throw new IllegalArgumentException("GPS fix timestamp (capturedAt) is required");
        }

        Instant now = Instant.now();
        Instant fixTime = location.getCapturedAt();
        if (fixTime.isAfter(now.plusSeconds(MAX_FUTURE_SKEW_SECONDS))) {
            throw new IllegalArgumentException("GPS fix timestamp cannot be in the future");
        }
        if (fixTime.isBefore(now.minusSeconds(MAX_PAST_FIX_AGE_SECONDS))) {
            throw new IllegalArgumentException("GPS fix is too old");
        }

        Trip current;
        if (location.getTripId() != null && !location.getTripId().isBlank()) {
            String requestedTripId = location.getTripId().trim();
            Optional<Trip> locked = tripRepository.findByTripIdForUpdate(requestedTripId);
            if (locked.isEmpty()) {
                locked = tripRepository.findByTripId(requestedTripId);
            }
            Trip target = locked.orElseThrow(() -> new TripNotFoundException("Trip Not Found"));
            validateDriverOwnershipAndRunningStatus(target, driverId, requestedTripId);
            current = target;
        } else {
            current = resolveRunningTripForDriver(driverId);
        }

        if (current.getActualStartTime() != null
                && fixTime.isBefore(current.getActualStartTime().minusSeconds(60))) {
            throw new IllegalArgumentException("GPS fix timestamp precedes trip start time");
        }

        Double prevLat = current.getCurrentLatitude();
        Double prevLon = current.getCurrentLongitude();
        Instant prevTime = current.getLocationUpdatedAt();

        // Reject out-of-order or duplicate fixes that are not strictly newer than the last accepted fix
        if (prevTime != null && !fixTime.isAfter(prevTime)) {
            throw new IllegalArgumentException("Out-of-order GPS fix rejected");
        }

        double roundedLat = GeoUtils.roundCoordinate(location.getLatitude());
        double roundedLon = GeoUtils.roundCoordinate(location.getLongitude());

        // Compute server-side speed from accepted coordinates and timestamps; treat client speed as advisory only on first fix
        Double measuredKmh = null;
        if (prevLat != null && prevLon != null && prevTime != null) {
            measuredKmh = GeoUtils.speedKmh(prevLat, prevLon, prevTime, roundedLat, roundedLon, fixTime);
        } else if (location.getSpeed() != null) {
            double advisoryKmh = location.getSpeed() * 3.6;
            if (advisoryKmh <= 120.0) {
                measuredKmh = advisoryKmh;
            }
        }

        double smoothedSpeedKmh;
        if (measuredKmh != null) {
            smoothedSpeedKmh = GeoUtils.smoothSpeedKmh(measuredKmh, current.getCurrentSpeed());
        } else {
            boolean previousIsFresh = current.getCurrentSpeed() != null
                    && prevTime != null
                    && now.minusSeconds(60).isBefore(prevTime);
            smoothedSpeedKmh = previousIsFresh ? GeoUtils.round1(current.getCurrentSpeed()) : 0.0;
        }

        Double bearing = null;
        if (prevLat != null && prevLon != null) {
            bearing = GeoUtils.bearingDegrees(prevLat, prevLon, roundedLat, roundedLon);
        }
        if (bearing == null && location.getHeading() != null) {
            bearing = GeoUtils.round1(location.getHeading());
        }
        if (bearing != null) {
            current.setCurrentBearing(bearing);
        }

        current.setCurrentLatitude(roundedLat);
        current.setCurrentLongitude(roundedLon);
        current.setCurrentSpeed(smoothedSpeedKmh);
        current.setLocationUpdatedAt(fixTime);

        Route route = current.getRoute();
        if (route != null && route.getRouteStops() != null && !route.getRouteStops().isEmpty()) {
            List<RouteStop> ordered = new ArrayList<>(route.getRouteStops());
            ordered.sort(Comparator.comparingLong(RouteStop::getSequenceNo));
            // lastSegmentIndex stores how many stops the bus has crossed (0 = none yet).
            GeoUtils.RouteProgress progress = GeoUtils.computeRouteProgress(
                    ordered,
                    current.getCurrentLatitude(),
                    current.getCurrentLongitude(),
                    smoothedSpeedKmh,
                    current.getLastSegmentIndex()
            );
            current.setLastSegmentIndex(progress.stopsCrossed);
        }

        Trip saved = tripRepository.save(current);
        broadcastAfterCommitOrNow(saved);
    }

    private void validateDriverOwnershipAndRunningStatus(Trip target, String driverId, String expectedTripId) {
        if (target.getDriver() == null
                || target.getDriver().getDriverId() == null
                || !target.getDriver().getDriverId().equalsIgnoreCase(driverId.trim())) {
            throw new DriverNotMatchedException("Driver does not match assigned trip");
        }
        if (expectedTripId != null && !expectedTripId.isBlank()
                && target.getTripId() != null
                && !target.getTripId().equals(expectedTripId.trim())) {
            throw new DriverNotMatchedException("Trip UUID does not match assigned trip");
        }
        if (target.getStatus() != TripStatus.RUNNING && target.getStatus() != TripStatus.OVERDUE) {
            throw new TripStatusException("Completed, cancelled or unstarted trip rejects GPS location updates");
        }
    }

    private Trip resolveRunningTripForDriver(String driverId) {
        List<Trip> lockedActive = tripRepository.findRunningTripsForDriverForUpdate(
                driverId,
                List.of(TripStatus.RUNNING, TripStatus.OVERDUE)
        );
        if (lockedActive != null && !lockedActive.isEmpty()) {
            return lockedActive.get(0);
        }

        Optional<Trip> runningTrip = tripRepository.findByDriver_DriverIdAndStatus(driverId, TripStatus.RUNNING);
        if (runningTrip.isPresent()) {
            return runningTrip.get();
        }

        List<Trip> driverTrips = tripRepository.findByDriver_DriverId(driverId);
        if (driverTrips != null && !driverTrips.isEmpty()) {
            throw new TripStatusException("No running trip active. Completed or scheduled trips reject location updates.");
        }
        throw new TripNotFoundException("Trip Not Found");
    }

    @Transactional(readOnly = true)
    public LocationResponseDTO getLocationByTripId(String tripId) {
        Optional<Trip> trip = tripRepository.findByTripId(tripId);
        if (trip.isEmpty()) {
            throw new TripNotFoundException("Trip Not Found");
        }
        if (trip.get().getStatus() == TripStatus.CANCELLED) {
            throw new TripStatusException("Cancelled trip cannot be tracked");
        }
        return buildLocationResponse(trip.get());
    }

    public void broadcastAfterCommitOrNow(Trip trip) {
        if (trip == null || trip.getTripId() == null) {
            return;
        }
        LocationResponseDTO dto = buildLocationResponse(trip);
        String tripId = trip.getTripId();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchLocationDto(tripId, dto);
                }
            });
        } else {
            dispatchLocationDto(tripId, dto);
        }
    }

    private void dispatchLocationDto(String tripId, LocationResponseDTO dto) {
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/trips/" + tripId, dto);
        }
    }

    private LocationResponseDTO buildLocationResponse(Trip tr) {
        boolean isLiveRunning = tr.getStatus() == TripStatus.RUNNING || tr.getStatus() == TripStatus.OVERDUE;
        Double exposedLat = isLiveRunning ? tr.getCurrentLatitude() : null;
        Double exposedLon = isLiveRunning ? tr.getCurrentLongitude() : null;
        Instant exposedUpdatedAt = isLiveRunning ? tr.getLocationUpdatedAt() : null;
        double speedKmh = isLiveRunning && tr.getCurrentSpeed() != null ? tr.getCurrentSpeed() : 0.0;

        LocationResponseDTO response = new LocationResponseDTO(
                exposedLat,
                exposedLon,
                exposedUpdatedAt,
                speedKmh,
                tr.getStatus()
        );
        response.setBearing(isLiveRunning ? tr.getCurrentBearing() : null);

        if (tr.getBus() != null) {
            response.setBusNumber(tr.getBus().getBusNumber());
            response.setRegistrationNumber(tr.getBus().getRegistrationNumber());
        }

        Route route = tr.getRoute();
        if (route != null) {
            response.setRouteId(route.getId());
            response.setRouteCode(route.getRouteCode());

            if (route.getRouteStops() != null && !route.getRouteStops().isEmpty()) {
                List<RouteStop> ordered = new ArrayList<>(route.getRouteStops());
                ordered.sort(Comparator.comparingLong(RouteStop::getSequenceNo));

                GeoUtils.RouteProgress progress = GeoUtils.computeRouteProgress(
                        ordered, exposedLat, exposedLon, speedKmh, tr.getLastSegmentIndex());
                boolean done = tr.getStatus() == TripStatus.COMPLETED;
                response.setPreviousStop(progress.previousStop);
                response.setCurrentStop(progress.currentStop);
                response.setNextStop(progress.nextStop);
                response.setDestinationStop(progress.destinationStop);
                response.setTrackingState(done ? GeoUtils.ARRIVED : progress.state);
                response.setStopsCrossed(progress.stopsCrossed);
                response.setTotalRouteDistanceMeters(progress.totalMeters);
                response.setDistanceTravelledMeters(done ? progress.totalMeters : progress.travelledMeters);
                response.setDistanceRemainingMeters(done ? 0.0 : progress.remainingMeters);
                response.setDistanceToNextStopMeters(done ? 0.0 : progress.toNextStopMeters);
                response.setDistanceFromRouteMeters(progress.distanceFromRouteMeters);
                response.setProgressPercent(done ? 100.0 : progress.progressPercent);
                response.setStopEtaMinutes(done || progress.stopEtaMinutes == null ? null : Arrays.asList(progress.stopEtaMinutes));
                response.setEtaMinutes(done ? Double.valueOf(0.0) : progress.etaMinutes);
                response.setNextStopEtaMinutes(done ? Double.valueOf(0.0) : progress.nextStopEtaMinutes);
            }
        }

        return response;
    }
}
