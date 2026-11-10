package com.ankit.bustracking.dto;

public class RouteStopResponseDTO {

    private Long stopId;

    private String stopName;

    private Double latitude;

    private Double longitude;

    private Long sequenceNo;

    public RouteStopResponseDTO(
            Long stopId,
            String stopName,
            Double latitude,
            Double longitude,
            Long sequenceNo
    ) {
        this.stopId = stopId;
        this.stopName = stopName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.sequenceNo = sequenceNo;
    }

    public Long getStopId() {
        return stopId;
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

    public Long getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(Long sequenceNo) {
        this.sequenceNo = sequenceNo;
    }
}