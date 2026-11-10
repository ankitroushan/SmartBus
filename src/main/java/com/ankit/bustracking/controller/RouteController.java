package com.ankit.bustracking.controller;

import com.ankit.bustracking.dto.RouteRequestDTO;
import com.ankit.bustracking.dto.RouteResponseDTO;
import com.ankit.bustracking.dto.RouteSummaryResponseDTO;
import com.ankit.bustracking.service.RouteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/route")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping
    public RouteResponseDTO createRoute(@RequestBody @Valid RouteRequestDTO routeRequestDTO) {
        return routeService.createRoute(routeRequestDTO);
    }

    @GetMapping("/{id}")
    public RouteResponseDTO getRouteById(@PathVariable Long id) {
        return routeService.getRouteById(id);
    }

    @GetMapping
    public List<RouteSummaryResponseDTO> getAllRoute() {
        return routeService.getAllRoute();
    }

    @PutMapping("/{id}")
    public RouteResponseDTO updateRoute(@PathVariable Long id, @RequestBody @Valid RouteRequestDTO routeRequestDTO) {
        return routeService.updateRoute(id, routeRequestDTO);
    }

    @DeleteMapping("/{id}")
    public RouteResponseDTO deactivateRoute(@PathVariable Long id) {
        return routeService.deactivateRoute(id);
    }
}
