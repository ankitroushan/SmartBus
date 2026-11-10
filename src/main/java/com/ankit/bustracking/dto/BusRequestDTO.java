package com.ankit.bustracking.dto;

import com.ankit.bustracking.enums.BusStatus;
import jakarta.validation.constraints.NotBlank;

public class BusRequestDTO {

    @NotBlank
    private String busNumber;

    @NotBlank
    private String registrationNumber;

    private BusStatus status;

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

    public BusStatus getStatus() {
        return status;
    }

    public void setStatus(BusStatus status) {
        this.status = status;
    }
}
