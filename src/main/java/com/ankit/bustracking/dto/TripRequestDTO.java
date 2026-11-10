package com.ankit.bustracking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class TripRequestDTO {

    @NotNull
    private Long busId;

    @NotNull
    private Long driverId;

    @NotNull
    private Long routeId;

    @NotNull
    private Instant scheduledStartTime;

    @NotNull
    private Instant scheduledEndTime;

    public Long getBusId() {
        return busId;
    }

    public void setBusId(Long busId) {
        this.busId = busId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }

    public Instant getScheduledStartTime() {
        return scheduledStartTime;
    }

    public void setScheduledStartTime(Instant scheduledStartTime) {
        this.scheduledStartTime = scheduledStartTime;
    }

    public Instant getScheduledEndTime() {
        return scheduledEndTime;
    }

    public void setScheduledEndTime(Instant scheduledEndTime) {
        this.scheduledEndTime = scheduledEndTime;
    }
}