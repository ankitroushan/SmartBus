package com.ankit.bustracking.service;

import com.ankit.bustracking.dto.DriverRequestDTO;
import com.ankit.bustracking.dto.DriverResetPinRequestDTO;
import com.ankit.bustracking.dto.DriverResponseDTO;
import com.ankit.bustracking.dto.DriverUpdateRequestDTO;
import com.ankit.bustracking.enums.AccountStatus;
import com.ankit.bustracking.enums.AvailabilityStatus;
import com.ankit.bustracking.enums.TripStatus;
import com.ankit.bustracking.exception.DriverNotFoundException;
import com.ankit.bustracking.model.Driver;
import com.ankit.bustracking.repository.DriverRepository;
import com.ankit.bustracking.repository.TripRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final TripRepository tripRepository;

    public DriverService(
            DriverRepository driverRepository,
            BCryptPasswordEncoder bCryptPasswordEncoder,
            TripRepository tripRepository) {
        this.driverRepository = driverRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.tripRepository = tripRepository;
    }

    private DriverResponseDTO toDriverResponseDTO(Driver driver) {
        return new DriverResponseDTO(
                driver.getId(),
                driver.getDriverName(),
                driver.getDriverId(),
                driver.getPhoneNumber(),
                driver.getLicenseNumber(),
                driver.getAccountStatus(),
                driver.getAvailabilityStatus()
        );
    }

    @Transactional
    public DriverResponseDTO createDriver(DriverRequestDTO driverRequestDTO) {
        String driverId = driverRequestDTO.getDriverId() != null
                ? driverRequestDTO.getDriverId().trim().toLowerCase()
                : "";
        String driverName = driverRequestDTO.getDriverName() != null
                ? driverRequestDTO.getDriverName().trim()
                : "";
        String phoneNumber = driverRequestDTO.getPhoneNumber() != null
                ? driverRequestDTO.getPhoneNumber().trim()
                : "";
        String licenseNumber = driverRequestDTO.getLicenseNumber() != null
                ? driverRequestDTO.getLicenseNumber().trim().toUpperCase()
                : "";

        if (driverRepository.existsByDriverIdIgnoreCase(driverId)) {
            throw new IllegalStateException("Driver with email '" + driverId + "' already exists");
        }
        if (driverRepository.existsByPhoneNumber(phoneNumber)) {
            throw new IllegalStateException("Driver with phone number '" + phoneNumber + "' already exists");
        }
        if (driverRepository.existsByLicenseNumberIgnoreCase(licenseNumber)) {
            throw new IllegalStateException("Driver with license number '" + licenseNumber + "' already exists");
        }

        Driver driver = new Driver();
        driver.setDriverId(driverId);
        driver.setDriverName(driverName);
        driver.setLicenseNumber(licenseNumber);
        driver.setPhoneNumber(phoneNumber);
        driver.setAccountStatus(AccountStatus.ACTIVE);
        driver.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);

        String pinHash = bCryptPasswordEncoder.encode(driverRequestDTO.getPin().trim());
        driver.setPinHash(pinHash);

        Driver savedDriver = driverRepository.save(driver);
        return toDriverResponseDTO(savedDriver);
    }

    @Transactional(readOnly = true)
    public DriverResponseDTO getDriverById(Long id) {
        Optional<Driver> driver = driverRepository.findById(id);
        if (driver.isEmpty()) {
            throw new DriverNotFoundException("Driver not Found");
        }
        return toDriverResponseDTO(driver.get());
    }

    @Transactional(readOnly = true)
    public List<DriverResponseDTO> getAllDrivers() {
        List<Driver> drivers = driverRepository.findAll();
        List<DriverResponseDTO> response = new ArrayList<>();
        for (Driver d : drivers) {
            response.add(toDriverResponseDTO(d));
        }
        return response;
    }

    @Transactional
    public DriverResponseDTO updateDriver(DriverUpdateRequestDTO driverUpdateRequestDTO, Long id) {
        Optional<Driver> driverOpt = driverRepository.findByIdForUpdate(id);
        if (driverOpt.isEmpty()) {
            driverOpt = driverRepository.findById(id);
        }
        if (driverOpt.isEmpty()) {
            throw new DriverNotFoundException("Driver does not exist");
        }

        Driver driver = driverOpt.get();
        String phone = driverUpdateRequestDTO.getPhoneNumber() != null
                ? driverUpdateRequestDTO.getPhoneNumber().trim()
                : "";
        if (driverRepository.existsByPhoneNumberAndIdNot(phone, id)) {
            throw new IllegalStateException("Driver with phone number '" + phone + "' already exists");
        }

        boolean hasRunningTrip = tripRepository.existsByDriver_IdAndStatusIn(
                id,
                List.of(TripStatus.RUNNING, TripStatus.OVERDUE)
        );
        boolean hasScheduledTrip = tripRepository.existsByDriver_IdAndStatusIn(
                id,
                List.of(TripStatus.SCHEDULED)
        );

        // Block admin edits to availability or account status while driver has a RUNNING/OVERDUE trip
        if (hasRunningTrip) {
            if (driverUpdateRequestDTO.getAvailabilityStatus() != null
                    && driverUpdateRequestDTO.getAvailabilityStatus() != driver.getAvailabilityStatus()) {
                throw new IllegalStateException("Cannot change driver availability while a trip is currently running");
            }
            if (driverUpdateRequestDTO.getAccountStatus() != null
                    && driverUpdateRequestDTO.getAccountStatus() != driver.getAccountStatus()) {
                throw new IllegalStateException("Cannot change driver account status while a trip is currently running");
            }
        } else {
            // Prevent manually setting ON_DUTY when no trip is running
            if (driverUpdateRequestDTO.getAvailabilityStatus() == AvailabilityStatus.ON_DUTY) {
                throw new IllegalStateException("ON_DUTY status is managed automatically when a trip starts");
            }
            // Prevent deactivating or making a driver UNAVAILABLE while they have SCHEDULED trips
            if (hasScheduledTrip) {
                if (driverUpdateRequestDTO.getAccountStatus() == AccountStatus.INACTIVE) {
                    throw new IllegalStateException("Cannot deactivate driver while they have scheduled trips");
                }
                if (driverUpdateRequestDTO.getAvailabilityStatus() == AvailabilityStatus.UNAVAILABLE) {
                    throw new IllegalStateException("Cannot mark driver UNAVAILABLE while they have scheduled trips");
                }
            }
        }

        driver.setPhoneNumber(phone);
        driver.setDriverName(driverUpdateRequestDTO.getDriverName().trim());

        if (driverUpdateRequestDTO.getAccountStatus() != null) {
            driver.setAccountStatus(driverUpdateRequestDTO.getAccountStatus());
        }
        if (driverUpdateRequestDTO.getAvailabilityStatus() != null) {
            driver.setAvailabilityStatus(driverUpdateRequestDTO.getAvailabilityStatus());
        }
        if (driver.getAccountStatus() == AccountStatus.INACTIVE) {
            driver.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        }

        Driver updatedDriver = driverRepository.save(driver);
        return toDriverResponseDTO(updatedDriver);
    }

    @Transactional
    public DriverResponseDTO resetDriverPin(Long id, DriverResetPinRequestDTO resetPinRequestDTO) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new DriverNotFoundException("Driver does not exist"));

        String rawPin = resetPinRequestDTO != null && resetPinRequestDTO.getPin() != null
                ? resetPinRequestDTO.getPin().trim()
                : "";
        if (!rawPin.matches("^[0-9]{4,6}$")) {
            throw new IllegalArgumentException("PIN must be 4 to 6 digits");
        }

        driver.setPinHash(bCryptPasswordEncoder.encode(rawPin));
        Driver saved = driverRepository.save(driver);
        return toDriverResponseDTO(saved);
    }

    @Transactional
    public DriverResponseDTO deactivateDriver(Long id) {
        Optional<Driver> driverOpt = driverRepository.findByIdForUpdate(id);
        if (driverOpt.isEmpty()) {
            driverOpt = driverRepository.findById(id);
        }
        Driver driver = driverOpt.orElseThrow(() -> new DriverNotFoundException("Driver does not exist"));

        if (tripRepository.existsByDriver_IdAndStatusIn(
                id,
                List.of(TripStatus.SCHEDULED, TripStatus.RUNNING, TripStatus.OVERDUE))) {
            throw new IllegalStateException("Cannot deactivate driver while they have scheduled or running trips");
        }

        driver.setAccountStatus(AccountStatus.INACTIVE);
        driver.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        return toDriverResponseDTO(driverRepository.save(driver));
    }
}
