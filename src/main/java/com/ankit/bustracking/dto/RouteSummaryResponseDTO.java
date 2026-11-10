package com.ankit.bustracking.dto;

import com.ankit.bustracking.enums.RouteStatus;

import java.util.ArrayList;
import java.util.List;

public class RouteSummaryResponseDTO {

    private Long id;
    private String routeCode;
    private RouteStatus routeStatus;
    private List<RouteStopResponseDTO> routeStops;

    public RouteSummaryResponseDTO(Long id, String routeCode, RouteStatus routeStatus) {
        this.id = id;
        this.routeCode = routeCode;
        this.routeStatus = routeStatus;
        this.routeStops = new ArrayList<>();
    }

    public RouteSummaryResponseDTO(
            Long id,
            String routeCode,
            RouteStatus routeStatus,
            List<RouteStopResponseDTO> routeStops) {
        this.id = id;
        this.routeCode = routeCode;
        this.routeStatus = routeStatus;
        this.routeStops = routeStops;
    }

    public Long getId() {
        return id;
    }

    public String getRouteCode() {
        return routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public RouteStatus getRouteStatus() {
        return routeStatus;
    }

    public void setRouteStatus(RouteStatus routeStatus) {
        this.routeStatus = routeStatus;
    }

    public List<RouteStopResponseDTO> getRouteStops() {
        return routeStops;
    }

    public void setRouteStops(List<RouteStopResponseDTO> routeStops) {
        this.routeStops = routeStops;
    }
}
