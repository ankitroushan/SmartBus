package com.ankit.bustracking.repository;

import com.ankit.bustracking.model.Driver;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Driver d WHERE d.id = :id")
    Optional<Driver> findByIdForUpdate(@Param("id") Long id);

    Optional<Driver> getByDriverId(String driverId);

    Optional<Driver> findByDriverIdIgnoreCase(String driverId);

    boolean existsByDriverIdIgnoreCase(String driverId);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);

    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);
}
