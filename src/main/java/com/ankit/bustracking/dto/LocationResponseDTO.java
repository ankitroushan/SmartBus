package com.ankit.bustracking.dto;

import com.ankit.bustracking.enums.TripStatus;

import java.time.Instant;
import java.util.List;

public class LocationResponseDTO {

    private Double latitude;

    private Double longitude;

    private Instant locationUpdatedAt;

    /** Server-computed filtered speed in km/h (0 when stationary or unknown). */
    private Double speedKmh;

    /** Direction of travel in degrees (0 = North, 90 = East). */
    private Double bearing;

    private TripStatus tripStatus;

    private String busNumber;
    private String registrationNumber;
    private Long routeId;
    private String routeCode;

    private String previousStop;
    private String currentStop;
    private String nextStop;
    private String destinationStop;

    private Double totalRouteDistanceMeters;
    private Double distanceTravelledMeters;
    private Double distanceRemainingMeters;
    private Double distanceToNextStopMeters;
    private Double progressPercent;
    private Double etaMinutes;
    private Double nextStopEtaMinutes;

    /** NOT_STARTED, ON_ROUTE, OFF_ROUTE or ARRIVED (see GeoUtils). */
    private String trackingState;
    private Integer stopsCrossed;
    /** Distance from the route line (ON_ROUTE/OFF_ROUTE) or from the first stop (NOT_STARTED). */
    private Double distanceFromRouteMeters;
    /** ETA in minutes for every stop of the route, by stop order (null = already crossed / not available). */
    private List<Double> stopEtaMinutes;

    public LocationResponseDTO(Double latitude, Double longitude, Instant locationUpdatedAt) {
        this(latitude, longitude, locationUpdatedAt, null, null);
    }

    public LocationResponseDTO(Double latitude, Double longitude, Instant locationUpdatedAt,
                               Double speedKmh, TripStatus tripStatus) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationUpdatedAt = locationUpdatedAt;
        this.speedKmh = speedKmh;
        this.tripStatus = tripStatus;
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

    public Instant getLocationUpdatedAt() {
        return locationUpdatedAt;
    }

    public void setLocationUpdatedAt(Instant locationUpdatedAt) {
        this.locationUpdatedAt = locationUpdatedAt;
    }

    public Double getSpeedKmh() {
        return speedKmh;
    }

    public void setSpeedKmh(Double speedKmh) {
        this.speedKmh = speedKmh;
    }

    public Double getBearing() {
        return bearing;
    }

    public void setBearing(Double bearing) {
        this.bearing = bearing;
    }

    public TripStatus getTripStatus() {
        return tripStatus;
    }

    public void setTripStatus(TripStatus tripStatus) {
        this.tripStatus = tripStatus;
    }

    public String getBusNumber() {
        return busNumber;
    }

    public void setBusNumber(String busNumber) {
        this.busNumber = busNumber;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }

    public String getRouteCode() {
        return routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public String getPreviousStop() {
        return previousStop;
    }

    public void setPreviousStop(String previousStop) {
        this.previousStop = previousStop;
    }

    public String getCurrentStop() {
        return currentStop;
    }

    public void setCurrentStop(String currentStop) {
        this.currentStop = currentStop;
    }

    public String getNextStop() {
        return nextStop;
    }

    public void setNextStop(String nextStop) {
        this.nextStop = nextStop;
    }

    public String getDestinationStop() {
        return destinationStop;
    }

    public void setDestinationStop(String destinationStop) {
        this.destinationStop = destinationStop;
    }

    public Double getTotalRouteDistanceMeters() {
        return totalRouteDistanceMeters;
    }

    public void setTotalRouteDistanceMeters(Double totalRouteDistanceMeters) {
        this.totalRouteDistanceMeters = totalRouteDistanceMeters;
    }

    public Double getDistanceTravelledMeters() {
        return distanceTravelledMeters;
    }

    public void setDistanceTravelledMeters(Double distanceTravelledMeters) {
        this.distanceTravelledMeters = distanceTravelledMeters;
    }

    public Double getDistanceRemainingMeters() {
        return distanceRemainingMeters;
    }

    public void setDistanceRemainingMeters(Double distanceRemainingMeters) {
        this.distanceRemainingMeters = distanceRemainingMeters;
    }

    public Double getDistanceToNextStopMeters() {
        return distanceToNextStopMeters;
    }

    public void setDistanceToNextStopMeters(Double distanceToNextStopMeters) {
        this.distanceToNextStopMeters = distanceToNextStopMeters;
    }

    public Double getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Double progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Double getEtaMinutes() {
        return etaMinutes;
    }

    public void setEtaMinutes(Double etaMinutes) {
        this.etaMinutes = etaMinutes;
    }

    public Double getNextStopEtaMinutes() {
        return nextStopEtaMinutes;
    }

    public void setNextStopEtaMinutes(Double nextStopEtaMinutes) {
        this.nextStopEtaMinutes = nextStopEtaMinutes;
    }

    public String getTrackingState() {
        return trackingState;
    }

    public void setTrackingState(String trackingState) {
        this.trackingState = trackingState;
    }

    public Integer getStopsCrossed() {
        return stopsCrossed;
    }

    public void setStopsCrossed(Integer stopsCrossed) {
        this.stopsCrossed = stopsCrossed;
    }

    public List<Double> getStopEtaMinutes() {
        return stopEtaMinutes;
    }

    public void setStopEtaMinutes(List<Double> stopEtaMinutes) {
        this.stopEtaMinutes = stopEtaMinutes;
    }

    public Double getDistanceFromRouteMeters() {
        return distanceFromRouteMeters;
    }

    public void setDistanceFromRouteMeters(Double distanceFromRouteMeters) {
        this.distanceFromRouteMeters = distanceFromRouteMeters;
    }
}
