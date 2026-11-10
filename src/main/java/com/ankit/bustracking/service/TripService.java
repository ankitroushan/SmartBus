package com.ankit.bustracking.service;

import com.ankit.bustracking.dto.LocationUpdateRequestDTO;
import com.ankit.bustracking.dto.PassengerTripResponseDTO;
import com.ankit.bustracking.util.GeoUtils;
import com.ankit.bustracking.dto.TripRequestDTO;
import com.ankit.bustracking.dto.TripResponseDTO;
import com.ankit.bustracking.enums.*;
import com.ankit.bustracking.exception.*;
import com.ankit.bustracking.model.*;
import com.ankit.bustracking.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    private final TripRepository tripRepository;
    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final StopRepository stopRepository;
    private final LocationUpdateService locationUpdateService;

    public TripService(
            TripRepository tripRepository,
            BusRepository busRepository,
            DriverRepository driverRepository,
            RouteRepository routeRepository,
            RouteStopRepository routeStopRepository,
            StopRepository stopRepository,
            LocationUpdateService locationUpdateService) {
        this.tripRepository = tripRepository;
        this.busRepository = busRepository;
        this.driverRepository = driverRepository;
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.stopRepository = stopRepository;
        this.locationUpdateService = locationUpdateService;
    }

    private TripResponseDTO toTripResponseDTO(Trip trip) {
        TripResponseDTO dto = new TripResponseDTO(
                trip.getId(),
                trip.getTripId(),
                trip.getBus() != null ? trip.getBus().getId() : null,
                trip.getDriver() != null ? trip.getDriver().getId() : null,
                trip.getRoute() != null ? trip.getRoute().getId() : null,
                trip.getStatus(),
                trip.getScheduledStartTime(),
                trip.getScheduledEndTime(),
                trip.getActualStartTime(),
                trip.getActualEndTime()
        );

        if (trip.getBus() != null) {
            dto.setBusNumber(trip.getBus().getBusNumber());
            dto.setRegistrationNumber(trip.getBus().getRegistrationNumber());
        }
        if (trip.getDriver() != null) {
            dto.setDriverName(trip.getDriver().getDriverName());
            dto.setDriverEmail(trip.getDriver().getDriverId());
        }
        if (trip.getRoute() != null) {
            dto.setRouteCode(trip.getRoute().getRouteCode());
            List<RouteStop> stops = trip.getRoute().getRouteStops();
            if (stops != null && !stops.isEmpty()) {
                List<RouteStop> sorted = new ArrayList<>(stops);
                sorted.sort(Comparator.comparingLong(RouteStop::getSequenceNo));
                Stop first = sorted.get(0).getStop();
                Stop last = sorted.get(sorted.size() - 1).getStop();
                if (first != null) {
                    dto.setOriginStop(first.getStopName());
                }
                if (last != null) {
                    dto.setDestinationStop(last.getStopName());
                }
            }
        }
        return dto;
    }

    @Transactional
    public TripResponseDTO createTrip(TripRequestDTO tripRequestDTO) {
        if (tripRequestDTO.getScheduledStartTime() == null
                || tripRequestDTO.getScheduledEndTime() == null
                || !tripRequestDTO.getScheduledStartTime().isBefore(tripRequestDTO.getScheduledEndTime())) {
            throw new InvalidTripTimeException("ScheduledStartTime or ScheduledEndTime is invalid");
        }

        // Lock bus and driver rows in deterministic order (Bus -> Driver) with PESSIMISTIC_WRITE
        Optional<Bus> bus = busRepository.findByIdForUpdate(tripRequestDTO.getBusId());
        if (bus.isEmpty()) {
            bus = busRepository.findById(tripRequestDTO.getBusId());
        }
        if (bus.isEmpty()) {
            throw new BusNotFoundException();
        }

        Optional<Driver> driver = driverRepository.findByIdForUpdate(tripRequestDTO.getDriverId());
        if (driver.isEmpty()) {
            driver = driverRepository.findById(tripRequestDTO.getDriverId());
        }
        if (driver.isEmpty()) {
            throw new DriverNotFoundException("Driver does not exist");
        }

        Optional<Route> route = routeRepository.findById(tripRequestDTO.getRouteId());
        if (route.isEmpty()) {
            throw new RouteNotFoundException("Route does not exist");
        }

        if (bus.get().getStatus() != BusStatus.ACTIVE) {
            throw new BusNotActiveException("Bus INACTIVE");
        }

        if (driver.get().getAccountStatus() != AccountStatus.ACTIVE) {
            throw new DriverInactiveException("Driver INACTIVE");
        }

        if (route.get().getRouteStatus() != RouteStatus.AVAILABLE) {
            throw new RouteInactiveException("Route UNAVAILABLE");
        }

        // Driver must be AVAILABLE at scheduling time
        if (driver.get().getAvailabilityStatus() != AvailabilityStatus.AVAILABLE) {
            throw new DriverUnavailableException("Driver Unavailable");
        }

        Collection<TripStatus> statuses = List.of(
                TripStatus.RUNNING,
                TripStatus.SCHEDULED,
                TripStatus.OVERDUE
        );

        if (tripRepository.existsByBus_IdAndStatusInAndScheduledEndTimeGreaterThanAndScheduledStartTimeLessThan(
                tripRequestDTO.getBusId(),
                statuses,
                tripRequestDTO.getScheduledStartTime(),
                tripRequestDTO.getScheduledEndTime())) {
            throw new BusScheduleConflictException("Bus Schedule conflict");
        }

        if (tripRepository.existsByDriver_IdAndStatusInAndScheduledEndTimeGreaterThanAndScheduledStartTimeLessThan(
                tripRequestDTO.getDriverId(),
                statuses,
                tripRequestDTO.getScheduledStartTime(),
                tripRequestDTO.getScheduledEndTime())) {
            throw new DriverScheduleConflictException("Driver Schedule conflict");
        }

        Trip trip = new Trip();
        trip.setBus(bus.get());
        trip.setDriver(driver.get());
        trip.setRoute(route.get());
        trip.setScheduledStartTime(tripRequestDTO.getScheduledStartTime());
        trip.setScheduledEndTime(tripRequestDTO.getScheduledEndTime());
        trip.setStatus(TripStatus.SCHEDULED);
        trip.setTripId(UUID.randomUUID().toString());

        Trip savedTrip = tripRepository.save(trip);
        return toTripResponseDTO(savedTrip);
    }

    /**
     * Scheduled background job is the single owner of RUNNING -> OVERDUE state transitions.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void markOverdueTrips() {
        Instant now = Instant.now();
        List<Trip> overdueRunning = tripRepository.findByStatusAndScheduledEndTimeBefore(TripStatus.RUNNING, now);
        if (overdueRunning != null && !overdueRunning.isEmpty()) {
            for (Trip trip : overdueRunning) {
                trip.setStatus(TripStatus.OVERDUE);
                Trip saved = tripRepository.save(trip);
                log.warn("Trip id={} (tripId={}) exceeded scheduledEndTime={} and is now marked OVERDUE.",
                        saved.getId(), saved.getTripId(), saved.getScheduledEndTime());
                locationUpdateService.broadcastAfterCommitOrNow(saved);
            }
        }
    }

    @Transactional(readOnly = true)
    public TripResponseDTO getTripById(Long id) {
        Optional<Trip> trip = tripRepository.findById(id);
        if (trip.isEmpty()) {
            throw new TripNotFoundException("Trip not found");
        }
        return toTripResponseDTO(trip.get());
    }

    @Transactional(readOnly = true)
    public List<TripResponseDTO> getAllTrips() {
        List<Trip> trips = tripRepository.findAll();
        List<TripResponseDTO> tr = new ArrayList<>();
        for (Trip trip : trips) {
            tr.add(toTripResponseDTO(trip));
        }
        return tr;
    }

    /**
     * Starts an assigned SCHEDULED trip.
     * Boundary rule: currentTime must be within [scheduledStartTime, scheduledEndTime] (inclusive).
     * A trip cannot start before scheduledStartTime or after scheduledEndTime.
     */
    @Transactional
    public TripResponseDTO startTrip(Long id, String driverEmail) {
        return startTrip(id, driverEmail, null);
    }

    @Transactional
    public TripResponseDTO startTrip(Long id, String driverEmail, LocationUpdateRequestDTO startLocation) {
        if (driverEmail == null || driverEmail.isBlank()) {
            throw new DriverNotMatchedException("Driver does not Matched");
        }

        Optional<Trip> trip = tripRepository.findByIdForUpdate(id);
        if (trip.isEmpty()) {
            trip = tripRepository.findById(id);
        }
        if (trip.isEmpty()) {
            throw new TripNotFoundException("Trip not Found");
        }

        Trip target = trip.get();
        if (target.getStatus() != TripStatus.SCHEDULED) {
            throw new TripStatusException("tripStatus conflict");
        }

        if (target.getDriver() == null
                || target.getDriver().getDriverId() == null
                || !target.getDriver().getDriverId().equalsIgnoreCase(driverEmail.trim())) {
            throw new DriverNotMatchedException("Driver does not Matched");
        }

        Bus bus = target.getBus();
        if (bus != null && bus.getId() != null) {
            bus = busRepository.findByIdForUpdate(bus.getId()).orElse(bus);
        }
        Driver driver = target.getDriver();
        if (driver.getId() != null) {
            driver = driverRepository.findByIdForUpdate(driver.getId()).orElse(driver);
        }

        if (driver.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new DriverInactiveException("Driver account is inactive");
        }
        if (driver.getAvailabilityStatus() != AvailabilityStatus.AVAILABLE) {
            throw new DriverUnavailableException("Driver is already on duty");
        }
        if (driver.getId() != null && target.getId() != null
                && tripRepository.existsByDriver_IdAndStatusInAndIdNot(
                        driver.getId(),
                        List.of(TripStatus.RUNNING, TripStatus.OVERDUE),
                        target.getId())) {
            throw new DriverUnavailableException("Driver already has a running trip");
        }

        if (bus != null) {
            if (bus.getStatus() != BusStatus.ACTIVE) {
                throw new BusNotActiveException("Assigned bus is inactive");
            }
            if (bus.getId() != null && target.getId() != null
                    && tripRepository.existsByBus_IdAndStatusInAndIdNot(
                            bus.getId(),
                            List.of(TripStatus.RUNNING, TripStatus.OVERDUE),
                            target.getId())) {
                throw new BusScheduleConflictException("Assigned bus is already running another trip");
            }
        }

        Route route = target.getRoute();
        if (route != null && route.getRouteStatus() != RouteStatus.AVAILABLE) {
            throw new RouteInactiveException("Assigned route is unavailable");
        }

        Instant currentTime = Instant.now();
        if (target.getScheduledStartTime() != null && target.getScheduledEndTime() != null) {
            if (currentTime.isBefore(target.getScheduledStartTime())
                    || currentTime.isAfter(target.getScheduledEndTime())) {
                throw new InvalidTripTimeException("trip time conflict");
            }
        }

        target.setStatus(TripStatus.RUNNING);
        target.setActualStartTime(currentTime);
        target.setLastSegmentIndex(0);
        target.setCurrentLatitude(null);
        target.setCurrentLongitude(null);
        target.setCurrentSpeed(null);
        target.setCurrentBearing(null);
        target.setLocationUpdatedAt(null);

        if (startLocation != null && startLocation.getLatitude() != null && startLocation.getLongitude() != null) {
            double startLat = GeoUtils.roundCoordinate(startLocation.getLatitude());
            double startLon = GeoUtils.roundCoordinate(startLocation.getLongitude());
            List<RouteStop> ordered = route != null && route.getRouteStops() != null
                    ? new ArrayList<>(route.getRouteStops()) : Collections.emptyList();
            ordered.sort(Comparator.comparingLong(RouteStop::getSequenceNo));
            if (!ordered.isEmpty()) {
                Stop origin = ordered.get(0).getStop();
                if (origin != null && origin.getLatitude() != null && origin.getLongitude() != null) {
                    double dist = GeoUtils.distanceMeters(startLat, startLon, origin.getLatitude(), origin.getLongitude());
                    if (dist > GeoUtils.START_TRIP_GEOFENCE_METERS) {
                        String dStr = dist >= 1000 ? String.format(Locale.US, "%.2f km", dist / 1000.0) : Math.round(dist) + " m";
                        throw new TripStatusException("Cannot start trip: You are " + dStr + " away from starting stop '" + origin.getStopName() + "'. Move within 300 m of the origin stop to start.");
                    }
                }
            }
            target.setCurrentLatitude(startLat);
            target.setCurrentLongitude(startLon);
            target.setLocationUpdatedAt(startLocation.getCapturedAt() != null ? startLocation.getCapturedAt() : currentTime);
            target.setCurrentSpeed(0.0);
            if (!ordered.isEmpty()) {
                GeoUtils.RouteProgress prog = GeoUtils.computeRouteProgress(ordered, startLat, startLon, 0.0, 0);
                target.setLastSegmentIndex(prog.stopsCrossed);
            }
        }
        driver.setAvailabilityStatus(AvailabilityStatus.ON_DUTY);

        Trip savedTrip = tripRepository.save(target);
        locationUpdateService.broadcastAfterCommitOrNow(savedTrip);
        return toTripResponseDTO(savedTrip);
    }

    @Transactional
    public TripResponseDTO completeTrip(Long id, String driverEmail) {
        if (driverEmail == null || driverEmail.isBlank()) {
            throw new DriverNotMatchedException("Driver does not Matched");
        }

        Optional<Trip> trip = tripRepository.findByIdForUpdate(id);
        if (trip.isEmpty()) {
            trip = tripRepository.findById(id);
        }
        if (trip.isEmpty()) {
            throw new TripNotFoundException("Trip not Found");
        }

        Trip target = trip.get();
        if (target.getStatus() != TripStatus.RUNNING
                && target.getStatus() != TripStatus.OVERDUE) {
            throw new TripStatusException("tripStatus conflict");
        }

        if (target.getDriver() == null
                || target.getDriver().getDriverId() == null
                || !target.getDriver().getDriverId().equalsIgnoreCase(driverEmail.trim())) {
            throw new DriverNotMatchedException("Driver does not Matched");
        }

        Driver driver = target.getDriver();
        if (driver.getId() != null) {
            driver = driverRepository.findByIdForUpdate(driver.getId()).orElse(driver);
        }

        target.setStatus(TripStatus.COMPLETED);
        target.setActualEndTime(Instant.now());
        target.setCurrentSpeed(0.0);
        releaseDriverIfNoOtherRunningTrips(driver, target.getId());

        Trip savedTrip = tripRepository.save(target);
        locationUpdateService.broadcastAfterCommitOrNow(savedTrip);
        return toTripResponseDTO(savedTrip);
    }

    @Transactional
    public TripResponseDTO cancelTrip(Long id) {
        Optional<Trip> trip = tripRepository.findByIdForUpdate(id);
        if (trip.isEmpty()) {
            trip = tripRepository.findById(id);
        }
        if (trip.isEmpty()) {
            throw new TripNotFoundException("Trip not Found");
        }

        Trip target = trip.get();
        if (target.getStatus() != TripStatus.SCHEDULED) {
            throw new TripStatusException("Only SCHEDULED trips can be cancelled");
        }

        Driver driver = target.getDriver();
        if (driver != null && driver.getId() != null) {
            driver = driverRepository.findByIdForUpdate(driver.getId()).orElse(driver);
        }

        target.setStatus(TripStatus.CANCELLED);
        target.setActualEndTime(Instant.now());
        target.setCurrentSpeed(0.0);
        if (driver != null && driver.getAvailabilityStatus() == AvailabilityStatus.ON_DUTY) {
            releaseDriverIfNoOtherRunningTrips(driver, target.getId());
        }

        Trip savedTrip = tripRepository.save(target);
        locationUpdateService.broadcastAfterCommitOrNow(savedTrip);
        return toTripResponseDTO(savedTrip);
    }

    @Transactional
    public TripResponseDTO forceCompleteTrip(Long id) {
        Optional<Trip> trip = tripRepository.findByIdForUpdate(id);
        if (trip.isEmpty()) {
            trip = tripRepository.findById(id);
        }
        if (trip.isEmpty()) {
            throw new TripNotFoundException("Trip not Found");
        }

        Trip target = trip.get();
        if (target.getStatus() != TripStatus.RUNNING && target.getStatus() != TripStatus.OVERDUE) {
            throw new TripStatusException("Only RUNNING or OVERDUE trips can be force-completed");
        }

        Driver driver = target.getDriver();
        if (driver != null && driver.getId() != null) {
            driver = driverRepository.findByIdForUpdate(driver.getId()).orElse(driver);
        }

        target.setStatus(TripStatus.COMPLETED);
        target.setActualEndTime(Instant.now());
        target.setCurrentSpeed(0.0);
        if (driver != null) {
            releaseDriverIfNoOtherRunningTrips(driver, target.getId());
        }

        Trip savedTrip = tripRepository.save(target);
        locationUpdateService.broadcastAfterCommitOrNow(savedTrip);
        return toTripResponseDTO(savedTrip);
    }

    private void releaseDriverIfNoOtherRunningTrips(Driver driver, Long completedTripId) {
        if (driver == null) {
            return;
        }
        if (driver.getAccountStatus() == AccountStatus.INACTIVE) {
            driver.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
            return;
        }
        boolean stillRunningOther = driver.getId() != null && completedTripId != null
                && tripRepository.existsByDriver_IdAndStatusInAndIdNot(
                        driver.getId(),
                        List.of(TripStatus.RUNNING, TripStatus.OVERDUE),
                        completedTripId);
        if (!stillRunningOther) {
            driver.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        }
    }

    @Transactional(readOnly = true)
    public List<TripResponseDTO> getTripsForDriver(String driverEmail) {
        List<Trip> trips = tripRepository.findByDriver_DriverId(driverEmail);
        List<TripResponseDTO> list = new ArrayList<>();
        if (trips != null) {
            for (Trip temp : trips) {
                list.add(toTripResponseDTO(temp));
            }
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<PassengerTripResponseDTO> passengerSearch(
            String from,
            String to,
            Long fromStopId,
            Long toStopId) {

        List<Stop> fromStops = new ArrayList<>();
        if (fromStopId != null) {
            Stop stop = stopRepository.findById(fromStopId)
                    .orElseThrow(StopNotFoundException::new);
            fromStops.add(stop);
        } else {
            if (from == null || from.trim().isEmpty()) {
                throw new IllegalArgumentException("From stop and To stop are required");
            }
            fromStops = stopRepository.findByStopNameIgnoreCase(from.trim());
            if (fromStops.isEmpty()) {
                throw new StopNotFoundException();
            }
        }

        List<Stop> toStops = new ArrayList<>();
        if (toStopId != null) {
            Stop stop = stopRepository.findById(toStopId)
                    .orElseThrow(StopNotFoundException::new);
            toStops.add(stop);
        } else {
            if (to == null || to.trim().isEmpty()) {
                throw new IllegalArgumentException("From stop and To stop are required");
            }
            toStops = stopRepository.findByStopNameIgnoreCase(to.trim());
            if (toStops.isEmpty()) {
                throw new StopNotFoundException();
            }
        }

        if (fromStopId != null && toStopId != null && Objects.equals(fromStopId, toStopId)) {
            throw new IllegalArgumentException("Starting stop and destination stop must be different");
        }
        if (fromStopId == null && toStopId == null
                && from != null && to != null
                && from.trim().equalsIgnoreCase(to.trim())) {
            throw new IllegalArgumentException("Starting stop and destination stop must be different");
        }

        Set<Long> toStopIds = new HashSet<>();
        for (Stop toStop : toStops) {
            toStopIds.add(toStop.getId());
        }

        Collection<TripStatus> activeStatuses = List.of(
                TripStatus.RUNNING,
                TripStatus.SCHEDULED,
                TripStatus.OVERDUE
        );

        Instant now = Instant.now();
        Set<String> addedTripKeys = new HashSet<>();
        List<PassengerTripResponseDTO> result = new ArrayList<>();

        for (Stop fromStop : fromStops) {
            List<RouteStop> fromRouteStops = routeStopRepository.findByStop_Id(fromStop.getId());

            for (RouteStop fromRouteStop : fromRouteStops) {
                Route route = fromRouteStop.getRoute();
                if (route == null || route.getRouteStatus() != RouteStatus.AVAILABLE) {
                    continue;
                }

                List<RouteStop> routeStops = new ArrayList<>(routeStopRepository.findByRoute_Id(route.getId()));
                routeStops.sort(Comparator.comparingLong(RouteStop::getSequenceNo));

                RouteStop matchedToAfter = null;
                for (RouteStop candidate : routeStops) {
                    if (candidate.getStop() != null
                            && toStopIds.contains(candidate.getStop().getId())
                            && candidate.getSequenceNo() > fromRouteStop.getSequenceNo()) {
                        matchedToAfter = candidate;
                        break;
                    }
                }

                if (matchedToAfter != null) {
                    List<Trip> routeTrips = tripRepository.findByRoute_IdAndStatusIn(route.getId(), activeStatuses);
                    if (routeTrips != null) {
                        for (Trip t : routeTrips) {
                            addMatchingTripResult(
                                    result, addedTripKeys, t, route, fromRouteStop, matchedToAfter, now);
                        }
                    }
                }
            }
        }

        result.sort(
                Comparator.comparing((PassengerTripResponseDTO r) ->
                                (r.getTripStatus() == TripStatus.RUNNING || r.getTripStatus() == TripStatus.OVERDUE) ? 0 : 1)
                        .thenComparing(PassengerTripResponseDTO::getScheduledStartTime,
                                Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
        );

        return result;
    }

    private void addMatchingTripResult(
            List<PassengerTripResponseDTO> result,
            Set<String> addedTripKeys,
            Trip t,
            Route route,
            RouteStop fromRouteStop,
            RouteStop toRouteStop,
            Instant now) {

        if (t.getStatus() == TripStatus.SCHEDULED
                && t.getScheduledEndTime() != null
                && t.getScheduledEndTime().isBefore(now)) {
            return;
        }

        String dedupKey = t.getId() != null ? "ID:" + t.getId() : "UUID:" + t.getTripId();
        if (!addedTripKeys.add(dedupKey)) {
            return;
        }

        boolean isLive = t.getStatus() == TripStatus.RUNNING || t.getStatus() == TripStatus.OVERDUE;
        Double exposedLat = isLive ? t.getCurrentLatitude() : null;
        Double exposedLon = isLive ? t.getCurrentLongitude() : null;
        Instant exposedUpdatedAt = isLive ? t.getLocationUpdatedAt() : null;

        PassengerTripResponseDTO dto = new PassengerTripResponseDTO(
                t.getId(),
                t.getTripId(),
                t.getBus().getId(),
                t.getBus().getBusNumber(),
                t.getBus().getRegistrationNumber(),
                route.getId(),
                route.getRouteCode(),
                t.getStatus(),
                fromRouteStop.getStop().getStopName(),
                toRouteStop.getStop().getStopName(),
                t.getScheduledStartTime(),
                t.getScheduledEndTime(),
                exposedLat,
                exposedLon,
                exposedUpdatedAt
        );
        dto.setFromStopId(fromRouteStop.getStop().getId());
        dto.setToStopId(toRouteStop.getStop().getId());

        result.add(dto);
    }
}
