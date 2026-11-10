package com.ankit.bustracking.dto;

import com.ankit.bustracking.enums.RouteStatus;

import java.util.List;

public class RouteResponseDTO {

    private Long id;

    private String routeCode;

    private RouteStatus routeStatus;

    private List<RouteStopResponseDTO> routeStops;

    private String routingBaseUrl;

    private String routingProfile;

    public RouteResponseDTO(
            Long id,
            String routeCode,
            RouteStatus routeStatus,
            List<RouteStopResponseDTO> routeStops
    ) {
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

    public String getRoutingBaseUrl() {
        return routingBaseUrl;
    }

    public void setRoutingBaseUrl(String routingBaseUrl) {
        this.routingBaseUrl = routingBaseUrl;
    }

    public String getRoutingProfile() {
        return routingProfile;
    }

    public void setRoutingProfile(String routingProfile) {
        this.routingProfile = routingProfile;
    }
}