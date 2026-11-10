package com.ankit.bustracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class DriverResetPinRequestDTO {

    @NotBlank
    @Pattern(
            regexp = "^[0-9]{4,6}$",
            message = "PIN must be 4 to 6 digits"
    )
    private String pin;

    public DriverResetPinRequestDTO() {
    }

    public DriverResetPinRequestDTO(String pin) {
        this.pin = pin;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}
