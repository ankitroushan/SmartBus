package com.ankit.bustracking.dto;

import com.ankit.bustracking.enums.RouteStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class RouteRequestDTO {

    @NotBlank
    private String routeCode;

    @NotEmpty
    @Size(min = 2, message = "A route must contain at least 2 stops")
    private List<Long> stopIds;

    private RouteStatus routeStatus;

    public String getRouteCode() {
        return routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public List<Long> getStopIds() {
        return stopIds;
    }

    public void setStopIds(List<Long> stopIds) {
        this.stopIds = stopIds;
    }

    public RouteStatus getRouteStatus() {
        return routeStatus;
    }

    public void setRouteStatus(RouteStatus routeStatus) {
        this.routeStatus = routeStatus;
    }
}
