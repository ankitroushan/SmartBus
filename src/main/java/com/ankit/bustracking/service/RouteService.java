package com.ankit.bustracking.service;

import com.ankit.bustracking.dto.RouteRequestDTO;
import com.ankit.bustracking.dto.RouteResponseDTO;
import com.ankit.bustracking.dto.RouteStopResponseDTO;
import com.ankit.bustracking.dto.RouteSummaryResponseDTO;
import com.ankit.bustracking.enums.RouteStatus;
import com.ankit.bustracking.enums.TripStatus;
import com.ankit.bustracking.exception.RouteNotFoundException;
import com.ankit.bustracking.exception.StopNotFoundException;
import com.ankit.bustracking.model.Route;
import com.ankit.bustracking.model.RouteStop;
import com.ankit.bustracking.model.Stop;
import com.ankit.bustracking.repository.RouteRepository;
import com.ankit.bustracking.repository.RouteStopRepository;
import com.ankit.bustracking.repository.StopRepository;
import com.ankit.bustracking.repository.TripRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Transactional
@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final RouteStopRepository routeStopRepository;
    private final TripRepository tripRepository;

    @Value("${routing.osrm.base-url:https://router.project-osrm.org}")
    private String routingBaseUrl = "https://router.project-osrm.org";

    @Value("${routing.osrm.profile:driving}")
    private String routingProfile = "driving";

    public RouteService(
            RouteRepository routeRepository,
            StopRepository stopRepository,
            RouteStopRepository routeStopRepository,
            TripRepository tripRepository) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.routeStopRepository = routeStopRepository;
        this.tripRepository = tripRepository;
    }

    private List<RouteStopResponseDTO> toRouteStopDTOs(List<RouteStop> routeStops) {
        List<RouteStopResponseDTO> list = new ArrayList<>();
        if (routeStops == null) {
            return list;
        }
        for (RouteStop routeStop : routeStops) {
            Stop stop = routeStop.getStop();
            list.add(new RouteStopResponseDTO(
                    stop.getId(),
                    stop.getStopName(),
                    stop.getLatitude(),
                    stop.getLongitude(),
                    routeStop.getSequenceNo()
            ));
        }
        return list;
    }

    private RouteResponseDTO toRouteResponseDTO(Route route) {
        RouteResponseDTO dto = new RouteResponseDTO(
                route.getId(),
                route.getRouteCode(),
                route.getRouteStatus(),
                toRouteStopDTOs(route.getRouteStops())
        );
        dto.setRoutingBaseUrl(routingBaseUrl);
        dto.setRoutingProfile(routingProfile);
        return dto;
    }

    private void validateRouteStops(List<Long> stopIds) {
        if (stopIds == null || stopIds.size() < 2) {
            throw new IllegalArgumentException("A route must contain at least 2 stops");
        }
        for (int i = 0; i < stopIds.size(); i++) {
            if (stopIds.get(i) == null) {
                throw new IllegalArgumentException("Stop ID cannot be null");
            }
            if (i > 0 && Objects.equals(stopIds.get(i), stopIds.get(i - 1))) {
                throw new IllegalArgumentException("Route cannot contain the same stop twice in a row");
            }
        }
    }

    private List<RouteStop> buildRouteStops(Route route, List<Long> stopIds) {
        List<RouteStop> routeStops = new ArrayList<>();
        for (int i = 0; i < stopIds.size(); i++) {
            Long stopId = stopIds.get(i);
            Stop stop = stopRepository.findById(stopId)
                    .orElseThrow(StopNotFoundException::new);
            if (Boolean.FALSE.equals(stop.getActive())) {
                throw new IllegalStateException("Stop '" + stop.getStopName() + "' is inactive");
            }
            RouteStop routeStop = new RouteStop();
            routeStop.setRoute(route);
            routeStop.setStop(stop);
            routeStop.setSequenceNo(i + 1L);
            routeStops.add(routeStop);
        }
        return routeStops;
    }

    public RouteResponseDTO createRoute(RouteRequestDTO routeRequestDTO) {
        validateRouteStops(routeRequestDTO.getStopIds());

        String normalizedCode = routeRequestDTO.getRouteCode().trim().toUpperCase();
        if (routeRepository.existsByRouteCodeIgnoreCase(normalizedCode)) {
            throw new IllegalStateException("Route with code '" + normalizedCode + "' already exists");
        }

        Route route = new Route();
        route.setRouteCode(normalizedCode);
        RouteStatus status = routeRequestDTO.getRouteStatus() != null
                ? routeRequestDTO.getRouteStatus()
                : RouteStatus.AVAILABLE;
        route.setRouteStatus(status);

        route.setRouteStops(buildRouteStops(route, routeRequestDTO.getStopIds()));
        Route savedRoute = routeRepository.save(route);
        return toRouteResponseDTO(savedRoute);
    }

    @Transactional(readOnly = true)
    public RouteResponseDTO getRouteById(Long id) {
        Optional<Route> existingRoute = routeRepository.findById(id);
        if (existingRoute.isEmpty()) {
            throw new RouteNotFoundException("Route does not exist");
        }
        return toRouteResponseDTO(existingRoute.get());
    }

    @Transactional(readOnly = true)
    public List<RouteSummaryResponseDTO> getAllRoute() {
        List<Route> routes = routeRepository.findAll();
        List<RouteSummaryResponseDTO> summary = new ArrayList<>();
        for (Route rt : routes) {
            summary.add(new RouteSummaryResponseDTO(
                    rt.getId(),
                    rt.getRouteCode(),
                    rt.getRouteStatus(),
                    toRouteStopDTOs(rt.getRouteStops())
            ));
        }
        return summary;
    }

    public RouteResponseDTO updateRoute(Long id, RouteRequestDTO routeRequestDTO) {
        validateRouteStops(routeRequestDTO.getStopIds());

        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new RouteNotFoundException("Route does not exist"));

        if (tripRepository.existsByRoute_IdAndStatusIn(
                id,
                List.of(TripStatus.SCHEDULED, TripStatus.RUNNING, TripStatus.OVERDUE))) {
            throw new IllegalStateException("Cannot edit route while trips on this route are scheduled or running");
        }

        String normalizedCode = routeRequestDTO.getRouteCode().trim().toUpperCase();
        if (routeRepository.existsByRouteCodeIgnoreCaseAndIdNot(normalizedCode, id)) {
            throw new IllegalStateException("Route with code '" + normalizedCode + "' already exists");
        }

        route.setRouteCode(normalizedCode);
        if (routeRequestDTO.getRouteStatus() != null) {
            route.setRouteStatus(routeRequestDTO.getRouteStatus());
        }

        List<RouteStop> newRouteStops = buildRouteStops(route, routeRequestDTO.getStopIds());

        route.getRouteStops().clear();
        routeStopRepository.flush();
        route.getRouteStops().addAll(newRouteStops);

        Route savedRoute = routeRepository.save(route);
        return toRouteResponseDTO(savedRoute);
    }

    public RouteResponseDTO deactivateRoute(Long id) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new RouteNotFoundException("Route does not exist"));

        if (tripRepository.existsByRoute_IdAndStatusIn(
                id,
                List.of(TripStatus.SCHEDULED, TripStatus.RUNNING, TripStatus.OVERDUE))) {
            throw new IllegalStateException("Cannot deactivate route while trips on this route are scheduled or running");
        }

        route.setRouteStatus(RouteStatus.UNAVAILABLE);
        return toRouteResponseDTO(routeRepository.save(route));
    }
}
