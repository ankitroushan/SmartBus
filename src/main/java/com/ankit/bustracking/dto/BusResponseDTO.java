package com.ankit.bustracking.dto;

import com.ankit.bustracking.enums.BusStatus;

public class BusResponseDTO {

    private Long id;
    private String busNumber;
    private BusStatus status;
    private String registrationNumber;

    public BusResponseDTO(
            Long id,
            String busNumber,
            BusStatus status,
            String registrationNumber) {

        this.id = id;
        this.busNumber = busNumber;
        this.status = status;
        this.registrationNumber = registrationNumber;
    }

    public Long getId() {
        return id;
    }

    public String getBusNumber() {
        return busNumber;
    }

    public void setBusNumber(String busNumber) {
        this.busNumber = busNumber;
    }

    public BusStatus getStatus() {
        return status;
    }

    public void setStatus(BusStatus status) {
        this.status = status;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }
}