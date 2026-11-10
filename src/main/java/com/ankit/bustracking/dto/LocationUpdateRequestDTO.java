package com.ankit.bustracking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class LocationUpdateRequestDTO {

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @NotNull
    private Double latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @NotNull
    private Double longitude;

    /** Optional speed in meters/second as reported by the browser Geolocation API (advisory only). */
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private Double speed;

    /** Optional heading in degrees (0..360) reported by the browser Geolocation API. */
    @DecimalMin("0.0")
    @DecimalMax("360.0")
    private Double heading;

    /** Horizontal GPS accuracy in meters. */
    @DecimalMin("0.0")
    @DecimalMax("150.0")
    private Double accuracy;

    /** Timestamp when the GPS fix was captured on the device. */
    private Instant capturedAt;

    /** Public trip UUID string to explicitly validate ownership and status. */
    private String tripId;

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

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public Double getHeading() {
        return heading;
    }

    public void setHeading(Double heading) {
        this.heading = heading;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }
}
