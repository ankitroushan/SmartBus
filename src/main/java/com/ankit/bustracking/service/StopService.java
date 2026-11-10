package com.ankit.bustracking.service;

import com.ankit.bustracking.dto.StopRequestDTO;
import com.ankit.bustracking.dto.StopResponseDTO;
import com.ankit.bustracking.enums.TripStatus;
import com.ankit.bustracking.exception.StopNotFoundException;
import com.ankit.bustracking.model.RouteStop;
import com.ankit.bustracking.model.Stop;
import com.ankit.bustracking.repository.RouteStopRepository;
import com.ankit.bustracking.repository.StopRepository;
import com.ankit.bustracking.repository.TripRepository;
import com.ankit.bustracking.util.GeoUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class StopService {

    private final StopRepository stopRepository;
    private final RouteStopRepository routeStopRepository;
    private final TripRepository tripRepository;

    public StopService(
            StopRepository stopRepository,
            RouteStopRepository routeStopRepository,
            TripRepository tripRepository) {
        this.stopRepository = stopRepository;
        this.routeStopRepository = routeStopRepository;
        this.tripRepository = tripRepository;
    }

    private StopResponseDTO toStopResponseDTO(Stop stop) {
        return new StopResponseDTO(
                stop.getId(),
                stop.getStopName(),
                stop.getLatitude(),
                stop.getLongitude(),
                stop.getActive()
        );
    }

    private void validateStopRequest(StopRequestDTO stopRequestDTO) {

        Double lat = stopRequestDTO.getLatitude();
        Double lon = stopRequestDTO.getLongitude();
        if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
            throw new IllegalArgumentException("Invalid stop GPS coordinates");
        }
    }

    private void ensureNoActiveTripsForStop(Long stopId) {
        List<RouteStop> routeStops = routeStopRepository.findByStop_Id(stopId);
        if (routeStops.isEmpty()) {
            return;
        }
        for (RouteStop rs : routeStops) {
            if (rs.getRoute() != null && tripRepository.existsByRoute_IdAndStatusIn(
                    rs.getRoute().getId(),
                    List.of(TripStatus.SCHEDULED, TripStatus.RUNNING, TripStatus.OVERDUE))) {
                throw new IllegalStateException(
                        "Cannot modify or deactivate stop while trips on route '" + rs.getRoute().getRouteCode() + "' are scheduled or running");
            }
        }
    }

    @Transactional
    public StopResponseDTO createStop(StopRequestDTO stopRequestDTO) {
        validateStopRequest(stopRequestDTO);
        String stopName = stopRequestDTO.getStopName().trim();
        double lat = GeoUtils.roundCoordinate(stopRequestDTO.getLatitude());
        double lon = GeoUtils.roundCoordinate(stopRequestDTO.getLongitude());

        if (stopRepository.existsByStopNameIgnoreCaseAndLatitudeAndLongitude(stopName, lat, lon)) {
            throw new IllegalStateException("Stop '" + stopName + "' at (" + lat + ", " + lon + ") already exists");
        }

        Stop stop = new Stop();
        stop.setStopName(stopName);
        stop.setLatitude(lat);
        stop.setLongitude(lon);
        stop.setActive(true);

        Stop savedStop = stopRepository.save(stop);
        return toStopResponseDTO(savedStop);
    }

    @Transactional(readOnly = true)
    public StopResponseDTO getStopById(Long id) {
        Optional<Stop> stop = stopRepository.findById(id);
        if (stop.isEmpty()) {
            throw new StopNotFoundException();
        }
        return toStopResponseDTO(stop.get());
    }

    @Transactional(readOnly = true)
    public List<StopResponseDTO> getAllStops() {
        List<Stop> stops = stopRepository.findAll();
        List<StopResponseDTO> response = new ArrayList<>();
        for (Stop stop : stops) {
            if (Boolean.FALSE.equals(stop.getActive())) {
                continue;
            }
            response.add(toStopResponseDTO(stop));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<StopResponseDTO> getAllStopsForAdmin() {

        List<Stop> stops = stopRepository.findAll();
        List<StopResponseDTO> response = new ArrayList<>();

        for (Stop stop : stops) {
            response.add(toStopResponseDTO(stop));
        }

        return response;
    }


    @Transactional
    public StopResponseDTO updateStop(StopRequestDTO stopRequestDTO, Long id) {
        validateStopRequest(stopRequestDTO);
        Optional<Stop> existingStop = stopRepository.findById(id);
        if (existingStop.isEmpty()) {
            throw new StopNotFoundException();
        }

        ensureNoActiveTripsForStop(id);

        String stopName = stopRequestDTO.getStopName().trim();
        double lat = GeoUtils.roundCoordinate(stopRequestDTO.getLatitude());
        double lon = GeoUtils.roundCoordinate(stopRequestDTO.getLongitude());

        if (stopRepository.existsByStopNameIgnoreCaseAndLatitudeAndLongitudeAndIdNot(stopName, lat, lon, id)) {
            throw new IllegalStateException("Stop '" + stopName + "' at (" + lat + ", " + lon + ") already exists");
        }

        Stop stop = existingStop.get();
        stop.setStopName(stopName);
        stop.setLatitude(lat);
        stop.setLongitude(lon);
        if (stopRequestDTO.getActive() != null) {
            stop.setActive(stopRequestDTO.getActive());
        }

        Stop newStop = stopRepository.save(stop);
        return toStopResponseDTO(newStop);
    }

    @Transactional
    public StopResponseDTO deactivateStop(Long id) {
        Stop stop = stopRepository.findById(id)
                .orElseThrow(StopNotFoundException::new);

        ensureNoActiveTripsForStop(id);

        stop.setActive(false);
        return toStopResponseDTO(stopRepository.save(stop));
    }
}
