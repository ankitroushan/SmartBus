package com.ankit.bustracking.repository;

import com.ankit.bustracking.model.Bus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BusRepository extends JpaRepository<Bus, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bus b WHERE b.id = :id")
    Optional<Bus> findByIdForUpdate(@Param("id") Long id);

    boolean existsByBusNumberIgnoreCase(String busNumber);

    boolean existsByBusNumberIgnoreCaseAndIdNot(String busNumber, Long id);

    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);
}
