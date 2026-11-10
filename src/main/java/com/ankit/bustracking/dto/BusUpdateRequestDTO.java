package com.ankit.bustracking.dto;

import com.ankit.bustracking.enums.BusStatus;
import jakarta.validation.constraints.NotBlank;

public class BusUpdateRequestDTO {

    @NotBlank
    private String busNumber;

    private BusStatus status;

    public BusUpdateRequestDTO() {
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
}
