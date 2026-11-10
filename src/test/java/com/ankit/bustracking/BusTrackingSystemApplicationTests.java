package com.ankit.bustracking;

import com.ankit.bustracking.dto.BusRequestDTO;
import com.ankit.bustracking.dto.BusResponseDTO;
import com.ankit.bustracking.dto.LocationUpdateRequestDTO;
import com.ankit.bustracking.dto.RouteRequestDTO;
import com.ankit.bustracking.dto.RouteResponseDTO;
import com.ankit.bustracking.dto.TripRequestDTO;
import com.ankit.bustracking.dto.TripResponseDTO;
import com.ankit.bustracking.enums.AccountStatus;
import com.ankit.bustracking.enums.AvailabilityStatus;
import com.ankit.bustracking.enums.BusStatus;
import com.ankit.bustracking.enums.RouteStatus;
import com.ankit.bustracking.enums.TripStatus;
import com.ankit.bustracking.exception.DriverNotMatchedException;
import com.ankit.bustracking.exception.InvalidTripTimeException;
import com.ankit.bustracking.model.Bus;
import com.ankit.bustracking.model.Driver;
import com.ankit.bustracking.model.Route;
import com.ankit.bustracking.model.Stop;
import com.ankit.bustracking.model.Trip;
import com.ankit.bustracking.repository.BusRepository;
import com.ankit.bustracking.repository.DriverRepository;
import com.ankit.bustracking.repository.RouteRepository;
import com.ankit.bustracking.repository.RouteStopRepository;
import com.ankit.bustracking.repository.StopRepository;
import com.ankit.bustracking.repository.TripRepository;
import com.ankit.bustracking.service.BusService;
import com.ankit.bustracking.service.LocationUpdateService;
import com.ankit.bustracking.service.RouteService;
import com.ankit.bustracking.service.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusTrackingSystemApplicationTests {

    @Mock
    private BusRepository busRepository;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private RouteStopRepository routeStopRepository;

    @Mock
    private StopRepository stopRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private BusService busService;
    private RouteService routeService;
    private TripService tripService;
    private LocationUpdateService locationUpdateService;

    @BeforeEach
    void setUp() {
        busService = new BusService(busRepository, tripRepository);

        routeService = new RouteService(
                routeRepository,
                stopRepository,
                routeStopRepository,
                tripRepository
        );

        locationUpdateService = new LocationUpdateService(
                tripRepository,
                messagingTemplate
        );

        tripService = new TripService(
                tripRepository,
                busRepository,
                driverRepository,
                routeRepository,
                routeStopRepository,
                stopRepository,
                locationUpdateService
        );
    }

    @Test
    void createBusTrimsAndConvertsValuesToUppercase() {

        BusRequestDTO request = new BusRequestDTO();
        request.setBusNumber("  bus-101 ");
        request.setRegistrationNumber(" jh15ab1234 ");

        when(busRepository.existsByBusNumberIgnoreCase("BUS-101"))
                .thenReturn(false);

        when(busRepository.existsByRegistrationNumberIgnoreCase("JH15AB1234"))
                .thenReturn(false);

        when(busRepository.save(any(Bus.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusResponseDTO response = busService.createBus(request);

        assertEquals("BUS-101", response.getBusNumber());
        assertEquals("JH15AB1234", response.getRegistrationNumber());
        assertEquals(BusStatus.INACTIVE, response.getStatus());
    }

    @Test
    void createBusRejectsDuplicateBusNumber() {

        BusRequestDTO request = new BusRequestDTO();
        request.setBusNumber("BUS-101");
        request.setRegistrationNumber("JH15AB1234");

        when(busRepository.existsByBusNumberIgnoreCase("BUS-101"))
                .thenReturn(true);

        assertThrows(
                IllegalStateException.class,
                () -> busService.createBus(request)
        );

        verify(busRepository, never()).save(any(Bus.class));
    }

    @Test
    void createRouteSavesStopsInTheGivenOrder() {

        Stop firstStop = new Stop();
        firstStop.setId(1L);
        firstStop.setStopName("Tower Chowk");
        firstStop.setLatitude(24.4851);
        firstStop.setLongitude(86.6948);

        Stop secondStop = new Stop();
        secondStop.setId(2L);
        secondStop.setStopName("Jasidih Station");
        secondStop.setLatitude(24.5123);
        secondStop.setLongitude(86.6452);

        when(routeRepository.existsByRouteCodeIgnoreCase("R-101"))
                .thenReturn(false);

        when(stopRepository.findById(1L))
                .thenReturn(Optional.of(firstStop));

        when(stopRepository.findById(2L))
                .thenReturn(Optional.of(secondStop));

        when(routeRepository.save(any(Route.class)))
                .thenAnswer(invocation -> {
                    Route route = invocation.getArgument(0);
                    route.setId(10L);
                    return route;
                });

        RouteRequestDTO request = new RouteRequestDTO();
        request.setRouteCode("r-101");
        request.setStopIds(List.of(1L, 2L));

        RouteResponseDTO response = routeService.createRoute(request);

        assertEquals("R-101", response.getRouteCode());
        assertEquals(RouteStatus.AVAILABLE, response.getRouteStatus());

        assertEquals(2, response.getRouteStops().size());

        assertEquals(1L, response.getRouteStops().get(0).getSequenceNo());
        assertEquals(2L, response.getRouteStops().get(1).getSequenceNo());
    }

    @Test
    void createRouteRejectsRouteWithLessThanTwoStops() {

        RouteRequestDTO request = new RouteRequestDTO();
        request.setRouteCode("R-INVALID");
        request.setStopIds(List.of(1L));

        assertThrows(
                IllegalArgumentException.class,
                () -> routeService.createRoute(request)
        );

        verify(routeRepository, never()).save(any(Route.class));
    }

    @Test
    void createTripRejectsInvalidScheduleTime() {

        TripRequestDTO request = new TripRequestDTO();

        request.setBusId(1L);
        request.setDriverId(1L);
        request.setRouteId(1L);

        request.setScheduledStartTime(
                Instant.now().plusSeconds(3600)
        );

        request.setScheduledEndTime(
                Instant.now().plusSeconds(1800)
        );

        assertThrows(
                InvalidTripTimeException.class,
                () -> tripService.createTrip(request)
        );

        verifyNoInteractions(
                busRepository,
                driverRepository,
                routeRepository,
                tripRepository
        );
    }

    @Test
    void createTripSchedulesAnAvailableBusDriverAndRoute() {

        Bus bus = new Bus();
        bus.setId(1L);
        bus.setBusNumber("BUS-101");
        bus.setRegistrationNumber("JH15AB1234");
        bus.setStatus(BusStatus.ACTIVE);

        Driver driver = new Driver();
        driver.setDriverId("driver@smartbus.in");
        driver.setDriverName("Ramesh");
        driver.setPhoneNumber("9876543210");
        driver.setLicenseNumber("DL-101");
        driver.setAccountStatus(AccountStatus.ACTIVE);
        driver.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);

        Route route = new Route();
        route.setId(10L);
        route.setRouteCode("R-101");
        route.setRouteStatus(RouteStatus.AVAILABLE);

        when(busRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(bus));

        when(driverRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(driver));

        when(routeRepository.findById(10L))
                .thenReturn(Optional.of(route));

        when(tripRepository
                .existsByBus_IdAndStatusInAndScheduledEndTimeGreaterThanAndScheduledStartTimeLessThan(
                        eq(1L),
                        anyCollection(),
                        any(Instant.class),
                        any(Instant.class)
                )).thenReturn(false);

        when(tripRepository
                .existsByDriver_IdAndStatusInAndScheduledEndTimeGreaterThanAndScheduledStartTimeLessThan(
                        eq(1L),
                        anyCollection(),
                        any(Instant.class),
                        any(Instant.class)
                )).thenReturn(false);

        when(tripRepository.save(any(Trip.class)))
                .thenAnswer(invocation -> {
                    Trip trip = invocation.getArgument(0);
                    trip.setId(100L);
                    return trip;
                });

        TripRequestDTO request = new TripRequestDTO();

        request.setBusId(1L);
        request.setDriverId(1L);
        request.setRouteId(10L);

        request.setScheduledStartTime(
                Instant.now().plusSeconds(600)
        );

        request.setScheduledEndTime(
                Instant.now().plusSeconds(3600)
        );

        TripResponseDTO response = tripService.createTrip(request);

        assertEquals(TripStatus.SCHEDULED, response.getTripStatus());
        assertEquals("BUS-101", response.getBusNumber());
        assertEquals("R-101", response.getRouteCode());

        assertNotNull(response.getTripId());

        verify(tripRepository).save(any(Trip.class));
    }

    @Test
    void startTripRejectsDriverWhoIsNotAssignedToTheTrip() {

        Driver assignedDriver = new Driver();
        assignedDriver.setDriverId("assigned@smartbus.in");

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setStatus(TripStatus.SCHEDULED);
        trip.setDriver(assignedDriver);

        when(tripRepository.findByIdForUpdate(50L))
                .thenReturn(Optional.of(trip));

        assertThrows(
                DriverNotMatchedException.class,
                () -> tripService.startTrip(
                        50L,
                        "someone-else@smartbus.in"
                )
        );
    }

    @Test
    void locationUpdateSavesTheDriversLatestGpsCoordinates() {

        Driver driver = new Driver();
        driver.setDriverId("driver@smartbus.in");

        Trip trip = new Trip();
        trip.setTripId("trip-uuid-1");
        trip.setStatus(TripStatus.RUNNING);
        trip.setDriver(driver);

        when(tripRepository.findByTripIdForUpdate("trip-uuid-1"))
                .thenReturn(Optional.of(trip));

        when(tripRepository.save(any(Trip.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocationUpdateRequestDTO request = new LocationUpdateRequestDTO();

        request.setTripId("trip-uuid-1");
        request.setLatitude(24.4851);
        request.setLongitude(86.6948);
        request.setAccuracy(10.0);
        request.setHeading(90.0);
        request.setCapturedAt(Instant.now());

        locationUpdateService.updateLocation(
                request,
                "driver@smartbus.in"
        );

        assertEquals(24.4851, trip.getCurrentLatitude());
        assertEquals(86.6948, trip.getCurrentLongitude());

        assertNotNull(trip.getLocationUpdatedAt());

        verify(tripRepository).save(trip);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/trips/trip-uuid-1"),
                any(Object.class)
        );
    }

    @Test
    void locationUpdateRejectsInvalidGpsCoordinates() {

        LocationUpdateRequestDTO request = new LocationUpdateRequestDTO();

        request.setTripId("trip-uuid-1");
        request.setLatitude(0.0);
        request.setLongitude(0.0);
        request.setCapturedAt(Instant.now());

        assertThrows(
                IllegalArgumentException.class,
                () -> locationUpdateService.updateLocation(
                        request,
                        "driver@smartbus.in"
                )
        );

        verifyNoInteractions(tripRepository);
    }
}
