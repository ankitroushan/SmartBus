package com.ankit.bustracking.repository;

import com.ankit.bustracking.model.Stop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StopRepository extends JpaRepository<Stop, Long> {

    List<Stop> findByStopNameIgnoreCase(String stopName);

    boolean existsByStopNameIgnoreCaseAndLatitudeAndLongitude(String stopName, Double latitude, Double longitude);

    boolean existsByStopNameIgnoreCaseAndLatitudeAndLongitudeAndIdNot(
            String stopName, Double latitude, Double longitude, Long id);
}
