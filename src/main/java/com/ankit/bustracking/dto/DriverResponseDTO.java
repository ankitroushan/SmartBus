package com.ankit.bustracking.dto;

import com.ankit.bustracking.enums.AccountStatus;
import com.ankit.bustracking.enums.AvailabilityStatus;

public class DriverResponseDTO {

    private Long id;
    private String driverName;
    private String driverId;
    private String phoneNumber;
    private String licenseNumber;
    private AccountStatus accountStatus;
    private AvailabilityStatus availabilityStatus;

    public DriverResponseDTO(
            Long id,
            String driverName,
            String driverId,
            String phoneNumber,
            String licenseNumber,
            AccountStatus accountStatus,
            AvailabilityStatus availabilityStatus) {

        this.id = id;
        this.driverName = driverName;
        this.driverId = driverId;
        this.phoneNumber = phoneNumber;
        this.licenseNumber = licenseNumber;
        this.accountStatus = accountStatus;
        this.availabilityStatus = availabilityStatus;
    }

    public Long getId() {
        return id;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public AvailabilityStatus getAvailabilityStatus() {
        return availabilityStatus;
    }

    public void setAvailabilityStatus(AvailabilityStatus availabilityStatus) {
        this.availabilityStatus = availabilityStatus;
    }
}