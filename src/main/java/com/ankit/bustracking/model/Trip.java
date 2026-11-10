package com.ankit.bustracking.model;

import com.ankit.bustracking.enums.TripStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        indexes = {
                @Index(name = "idx_trip_status_route", columnList = "status, route_id"),
                @Index(name = "idx_trip_driver", columnList = "driver_id"),
                @Index(name = "idx_trip_bus", columnList = "bus_id")
        }
)
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tripId;

    @JoinColumn(name = "bus_id")
    @ManyToOne
    private Bus bus;

    @ManyToOne
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne
    @JoinColumn(name = "route_id")
    private Route route;

    @Enumerated(EnumType.STRING)
    private TripStatus status;

    private Instant scheduledStartTime;

    private Instant scheduledEndTime;

    private Instant actualStartTime;

    private Instant actualEndTime;

    private Double currentLatitude;

    private Double currentLongitude;

    private Instant locationUpdatedAt;

    /** Latest filtered speed of the bus in km/h. */
    private Double currentSpeed;

    /** Latest heading/bearing of the bus in degrees (0 = North, 90 = East). */
    private Double currentBearing;

    /** Last matched route segment index so loop/out-and-back routes progress forward. */
    private Integer lastSegmentIndex;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public Bus getBus() {
        return bus;
    }

    public void setBus(Bus bus) {
        this.bus = bus;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public TripStatus getStatus() {
        return status;
    }

    public void setStatus(TripStatus status) {
        this.status = status;
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

    public Instant getActualStartTime() {
        return actualStartTime;
    }

    public void setActualStartTime(Instant actualStartTime) {
        this.actualStartTime = actualStartTime;
    }

    public Instant getActualEndTime() {
        return actualEndTime;
    }

    public void setActualEndTime(Instant actualEndTime) {
        this.actualEndTime = actualEndTime;
    }

    public Double getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(Double currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public Double getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(Double currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    public Instant getLocationUpdatedAt() {
        return locationUpdatedAt;
    }

    public void setLocationUpdatedAt(Instant locationUpdatedAt) {
        this.locationUpdatedAt = locationUpdatedAt;
    }

    public Double getCurrentSpeed() {
        return currentSpeed;
    }

    public void setCurrentSpeed(Double currentSpeed) {
        this.currentSpeed = currentSpeed;
    }

    public Double getCurrentBearing() {
        return currentBearing;
    }

    public void setCurrentBearing(Double currentBearing) {
        this.currentBearing = currentBearing;
    }

    public Integer getLastSegmentIndex() {
        return lastSegmentIndex;
    }

    public void setLastSegmentIndex(Integer lastSegmentIndex) {
        this.lastSegmentIndex = lastSegmentIndex;
    }
}
