package com.ankit.bustracking.service;

import com.ankit.bustracking.dto.BusRequestDTO;
import com.ankit.bustracking.dto.BusResponseDTO;
import com.ankit.bustracking.dto.BusUpdateRequestDTO;
import com.ankit.bustracking.enums.BusStatus;
import com.ankit.bustracking.enums.TripStatus;
import com.ankit.bustracking.exception.BusNotFoundException;
import com.ankit.bustracking.model.Bus;
import com.ankit.bustracking.repository.BusRepository;
import com.ankit.bustracking.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BusService {

    private final BusRepository busRepository;
    private final TripRepository tripRepository;

    public BusService(BusRepository busRepository, TripRepository tripRepository) {
        this.busRepository = busRepository;
        this.tripRepository = tripRepository;
    }

    private BusResponseDTO toBusResponseDTO(Bus bus) {
        return new BusResponseDTO(
                bus.getId(),
                bus.getBusNumber(),
                bus.getStatus(),
                bus.getRegistrationNumber()
        );
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    @Transactional
    public BusResponseDTO createBus(BusRequestDTO busRequestDTO) {
        String busNumber = normalizeCode(busRequestDTO.getBusNumber());
        String registrationNumber = normalizeCode(busRequestDTO.getRegistrationNumber());

        if (busRepository.existsByBusNumberIgnoreCase(busNumber)) {
            throw new IllegalStateException("Bus with bus number '" + busNumber + "' already exists");
        }
        if (busRepository.existsByRegistrationNumberIgnoreCase(registrationNumber)) {
            throw new IllegalStateException("Bus with registration number '" + registrationNumber + "' already exists");
        }

        Bus bus = new Bus();
        bus.setBusNumber(busNumber);
        bus.setRegistrationNumber(registrationNumber);

        // Newly created Bus defaults to INACTIVE until explicitly activated by Admin
        BusStatus status = busRequestDTO.getStatus() != null ? busRequestDTO.getStatus() : BusStatus.INACTIVE;
        bus.setStatus(status);

        Bus savedBus = busRepository.save(bus);
        return toBusResponseDTO(savedBus);
    }

    @Transactional(readOnly = true)
    public BusResponseDTO getBusById(Long id) {
        Optional<Bus> bus = busRepository.findById(id);
        if (bus.isEmpty()) {
            throw new BusNotFoundException();
        }
        return toBusResponseDTO(bus.get());
    }

    @Transactional(readOnly = true)
    public List<BusResponseDTO> getAllBuses() {
        List<Bus> buses = busRepository.findAll();
        List<BusResponseDTO> responses = new ArrayList<>();
        for (Bus bus : buses) {
            responses.add(toBusResponseDTO(bus));
        }
        return responses;
    }

    @Transactional
    public BusResponseDTO updateBus(BusUpdateRequestDTO busUpdateRequestDTO, Long id) {
        Optional<Bus> busOpt = busRepository.findByIdForUpdate(id);
        if (busOpt.isEmpty()) {
            busOpt = busRepository.findById(id);
        }
        if (busOpt.isEmpty()) {
            throw new BusNotFoundException();
        }

        Bus bus = busOpt.get();
        String busNumber = normalizeCode(busUpdateRequestDTO.getBusNumber());
        if (busRepository.existsByBusNumberIgnoreCaseAndIdNot(busNumber, id)) {
            throw new IllegalStateException("Bus with bus number '" + busNumber + "' already exists");
        }

        boolean hasActiveTrips = tripRepository.existsByBus_IdAndStatusIn(
                id,
                List.of(TripStatus.SCHEDULED, TripStatus.RUNNING, TripStatus.OVERDUE)
        );
        if (hasActiveTrips) {
            if (busUpdateRequestDTO.getStatus() == BusStatus.INACTIVE) {
                throw new IllegalStateException("Cannot deactivate bus while it has scheduled or running trips");
            }
            if (!busNumber.equalsIgnoreCase(bus.getBusNumber())) {
                throw new IllegalStateException("Cannot change bus number while it has scheduled or running trips");
            }
        }

        bus.setBusNumber(busNumber);
        if (busUpdateRequestDTO.getStatus() != null) {
            bus.setStatus(busUpdateRequestDTO.getStatus());
        }

        Bus updatedBus = busRepository.save(bus);
        return toBusResponseDTO(updatedBus);
    }

    @Transactional
    public BusResponseDTO deactivateBus(Long id) {
        Optional<Bus> busOpt = busRepository.findByIdForUpdate(id);
        if (busOpt.isEmpty()) {
            busOpt = busRepository.findById(id);
        }
        Bus bus = busOpt.orElseThrow(BusNotFoundException::new);

        if (tripRepository.existsByBus_IdAndStatusIn(
                id,
                List.of(TripStatus.SCHEDULED, TripStatus.RUNNING, TripStatus.OVERDUE))) {
            throw new IllegalStateException("Cannot deactivate bus while it has scheduled or running trips");
        }

        bus.setStatus(BusStatus.INACTIVE);
        return toBusResponseDTO(busRepository.save(bus));
    }
}
