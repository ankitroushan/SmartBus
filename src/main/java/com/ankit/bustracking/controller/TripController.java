package com.ankit.bustracking.controller;

import com.ankit.bustracking.dto.LocationResponseDTO;
import com.ankit.bustracking.dto.LocationUpdateRequestDTO;
import com.ankit.bustracking.dto.PassengerTripResponseDTO;
import com.ankit.bustracking.dto.TripRequestDTO;
import com.ankit.bustracking.dto.TripResponseDTO;
import com.ankit.bustracking.service.LocationUpdateService;
import com.ankit.bustracking.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;
    private final LocationUpdateService updateService;

    public TripController(TripService tripService, LocationUpdateService updateService) {
        this.tripService = tripService;
        this.updateService = updateService;
    }

    @PostMapping
    public TripResponseDTO createTrip(@RequestBody @Valid TripRequestDTO tripRequestDTO) {
        return tripService.createTrip(tripRequestDTO);
    }

    @GetMapping("/search")
    public List<PassengerTripResponseDTO> searchTrips(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "fromStopId", required = false) Long fromStopId,
            @RequestParam(value = "toStopId", required = false) Long toStopId) {
        return tripService.passengerSearch(from, to, fromStopId, toStopId);
    }

    @GetMapping("/{id}")
    public TripResponseDTO getTripById(@PathVariable Long id) {
        return tripService.getTripById(id);
    }

    @GetMapping
    public List<TripResponseDTO> getAllTrips() {
        return tripService.getAllTrips();
    }

    @PutMapping("/{id}/start")
    public TripResponseDTO startTrip(
            @PathVariable Long id,
            @RequestBody(required = false) LocationUpdateRequestDTO startLocation,
            Authentication authentication) {
        return tripService.startTrip(id, authentication.getName(), startLocation);
    }

    @PutMapping("/{id}/complete")
    public TripResponseDTO completeTrip(@PathVariable Long id, Authentication authentication) {
        return tripService.completeTrip(id, authentication.getName());
    }

    @PutMapping("/{id}/cancel")
    public TripResponseDTO cancelTrip(@PathVariable Long id) {
        return tripService.cancelTrip(id);
    }

    @PutMapping("/{id}/force-complete")
    public TripResponseDTO forceCompleteTrip(@PathVariable Long id) {
        return tripService.forceCompleteTrip(id);
    }

    @GetMapping("/my-trips")
    public List<TripResponseDTO> getMyTrips(Authentication authentication) {
        return tripService.getTripsForDriver(authentication.getName());
    }

    @PostMapping("/location")
    public ResponseEntity<Void> update(@RequestBody @Valid LocationUpdateRequestDTO location, Authentication authentication) {
        updateService.updateLocation(location, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tripId}/location")
    public LocationResponseDTO getLocationByTripId(@PathVariable String tripId) {
        return updateService.getLocationByTripId(tripId);
    }
}
