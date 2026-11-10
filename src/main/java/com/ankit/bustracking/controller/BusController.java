package com.ankit.bustracking.controller;

import com.ankit.bustracking.dto.BusRequestDTO;
import com.ankit.bustracking.dto.BusResponseDTO;
import com.ankit.bustracking.dto.BusUpdateRequestDTO;
import com.ankit.bustracking.service.BusService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buses")
public class BusController {

    private final BusService busService;

    public BusController(BusService busService) {
        this.busService = busService;
    }

    @PostMapping
    public BusResponseDTO createBus(@RequestBody @Valid BusRequestDTO busRequestDTO) {
        return busService.createBus(busRequestDTO);
    }

    @GetMapping("/{id}")
    public BusResponseDTO getBusById(@PathVariable Long id) {
        return busService.getBusById(id);
    }

    @GetMapping
    public List<BusResponseDTO> getAllBuses() {
        return busService.getAllBuses();
    }

    @PutMapping("/{id}")
    public BusResponseDTO updateBus(@RequestBody @Valid BusUpdateRequestDTO busUpdateRequestDTO, @PathVariable Long id) {
        return busService.updateBus(busUpdateRequestDTO, id);
    }

    @DeleteMapping("/{id}")
    public BusResponseDTO deactivateBus(@PathVariable Long id) {
        return busService.deactivateBus(id);
    }
}
