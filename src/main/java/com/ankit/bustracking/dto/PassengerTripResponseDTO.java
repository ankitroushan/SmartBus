package com.ankit.bustracking.dto;

import com.ankit.bustracking.enums.TripStatus;

import java.time.Instant;

public class PassengerTripResponseDTO {

    private Long id;
    private String tripId;
    private Long busId;
    private String busNumber;
    private String registrationNumber;
    private Long routeId;
    private String routeCode;
    private TripStatus tripStatus;
    private Long fromStopId;
    private String fromStop;
    private Long toStopId;
    private String toStop;
    private Instant scheduledStartTime;
    private Instant scheduledEndTime;
    private Double currentLatitude;
    private Double currentLongitude;
    private Instant locationUpdatedAt;

    public PassengerTripResponseDTO(
            Long id,
            String tripId,
            Long busId,
            String busNumber,
            String registrationNumber,
            Long routeId,
            String routeCode,
            TripStatus tripStatus,
            String fromStop,
            String toStop,
            Instant scheduledStartTime,
            Instant scheduledEndTime,
            Double currentLatitude,
            Double currentLongitude,
            Instant locationUpdatedAt) {

        this.id = id;
        this.tripId = tripId;
        this.busId = busId;
        this.busNumber = busNumber;
        this.registrationNumber = registrationNumber;
        this.routeId = routeId;
        this.routeCode = routeCode;
        this.tripStatus = tripStatus;
        this.fromStop = fromStop;
        this.toStop = toStop;
        this.scheduledStartTime = scheduledStartTime;
        this.scheduledEndTime = scheduledEndTime;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.locationUpdatedAt = locationUpdatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTripId() {
        return tripId;
    }

    public Long getBusId() {
        return busId;
    }

    public String getBusNumber() {
        return busNumber;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public Long getRouteId() {
        return routeId;
    }

    public String getRouteCode() {
        return routeCode;
    }

    public TripStatus getTripStatus() {
        return tripStatus;
    }

    public Long getFromStopId() {
        return fromStopId;
    }

    public void setFromStopId(Long fromStopId) {
        this.fromStopId = fromStopId;
    }

    public String getFromStop() {
        return fromStop;
    }

    public Long getToStopId() {
        return toStopId;
    }

    public void setToStopId(Long toStopId) {
        this.toStopId = toStopId;
    }

    public String getToStop() {
        return toStop;
    }

    public Instant getScheduledStartTime() {
        return scheduledStartTime;
    }

    public Instant getScheduledEndTime() {
        return scheduledEndTime;
    }

    public Double getCurrentLatitude() {
        return currentLatitude;
    }

    public Double getCurrentLongitude() {
        return currentLongitude;
    }

    public Instant getLocationUpdatedAt() {
        return locationUpdatedAt;
    }
}
