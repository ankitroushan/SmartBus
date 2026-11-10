package com.ankit.bustracking.controller;

import com.ankit.bustracking.dto.DriverRequestDTO;
import com.ankit.bustracking.dto.DriverResetPinRequestDTO;
import com.ankit.bustracking.dto.DriverResponseDTO;
import com.ankit.bustracking.dto.DriverUpdateRequestDTO;
import com.ankit.bustracking.service.DriverService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    public DriverResponseDTO createDriver(@RequestBody @Valid DriverRequestDTO driverRequestDTO) {
        return driverService.createDriver(driverRequestDTO);
    }

    @GetMapping("/{id}")
    public DriverResponseDTO getDriverById(@PathVariable Long id) {
        return driverService.getDriverById(id);
    }

    @GetMapping
    public List<DriverResponseDTO> getAllDrivers() {
        return driverService.getAllDrivers();
    }

    @PutMapping("/{id}")
    public DriverResponseDTO updateDriver(
            @RequestBody @Valid DriverUpdateRequestDTO driverUpdateRequestDTO,
            @PathVariable Long id) {
        return driverService.updateDriver(driverUpdateRequestDTO, id);
    }

    @PutMapping("/{id}/reset-pin")
    public DriverResponseDTO resetDriverPin(
            @PathVariable Long id,
            @RequestBody @Valid DriverResetPinRequestDTO resetPinRequestDTO) {
        return driverService.resetDriverPin(id, resetPinRequestDTO);
    }

    @DeleteMapping("/{id}")
    public DriverResponseDTO deactivateDriver(@PathVariable Long id) {
        return driverService.deactivateDriver(id);
    }
}
