package com.ankit.bustracking.dto;

public class StopResponseDTO {

    private Long id;

    private String stopName;

    private Double latitude;

    private Double longitude;

    private Boolean active = true;

    public StopResponseDTO(Long id, String stopName, Double latitude, Double longitude) {
        this(id, stopName, latitude, longitude, true);
    }

    public StopResponseDTO(Long id, String stopName, Double latitude, Double longitude, Boolean active) {
        this.id = id;
        this.stopName = stopName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.active = active == null ? Boolean.TRUE : active;
    }

    public Long getId() {
        return id;
    }

    public String getStopName() {
        return stopName;
    }

    public void setStopName(String stopName) {
        this.stopName = stopName;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}