package com.ankit.bustracking.controller;

import com.ankit.bustracking.dto.StopRequestDTO;
import com.ankit.bustracking.dto.StopResponseDTO;
import com.ankit.bustracking.service.StopService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stops")
public class StopController {

    private final StopService stopService;

    public StopController(StopService stopService) {
        this.stopService = stopService;
    }

    @PostMapping
    public StopResponseDTO createStop(@RequestBody @Valid StopRequestDTO stopRequestDTO) {
        return stopService.createStop(stopRequestDTO);
    }

    @GetMapping("/{id}")
    public StopResponseDTO getStopById(@PathVariable Long id) {
        return stopService.getStopById(id);
    }

    @GetMapping
    public List<StopResponseDTO> getAllStops() {
        return stopService.getAllStops();
    }

    @GetMapping("/admin")
    public List<StopResponseDTO> getAllStopsForAdmin() {
        return stopService.getAllStopsForAdmin();
    }

    @PutMapping("/{id}")
    public StopResponseDTO updateStop(@RequestBody @Valid StopRequestDTO stopRequestDTO, @PathVariable Long id) {
        return stopService.updateStop(stopRequestDTO, id);
    }

    @DeleteMapping("/{id}")
    public StopResponseDTO deactivateStop(@PathVariable Long id) {
        return stopService.deactivateStop(id);
    }
}
