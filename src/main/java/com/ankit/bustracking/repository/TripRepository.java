package com.ankit.bustracking.repository;

import com.ankit.bustracking.enums.TripStatus;
import com.ankit.bustracking.model.Trip;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    boolean existsByBus_IdAndStatusInAndScheduledEndTimeGreaterThanAndScheduledStartTimeLessThan(
            Long busId,
            Collection<TripStatus> statuses,
            Instant newStartTime,
            Instant newEndTime
    );

    boolean existsByDriver_IdAndStatusInAndScheduledEndTimeGreaterThanAndScheduledStartTimeLessThan(
            Long driverId,
            Collection<TripStatus> statuses,
            Instant newStartTime,
            Instant newEndTime
    );

    boolean existsByDriver_IdAndStatusIn(Long driverId, Collection<TripStatus> statuses);

    boolean existsByDriver_IdAndStatusInAndIdNot(Long driverId, Collection<TripStatus> statuses, Long id);

    boolean existsByBus_IdAndStatusIn(Long busId, Collection<TripStatus> statuses);

    boolean existsByBus_IdAndStatusInAndIdNot(Long busId, Collection<TripStatus> statuses, Long id);

    boolean existsByRoute_IdAndStatusIn(Long routeId, Collection<TripStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"bus", "driver", "route", "route.routeStops", "route.routeStops.stop"})
    @Query("SELECT t FROM Trip t WHERE t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"bus", "driver", "route", "route.routeStops", "route.routeStops.stop"})
    @Query("SELECT t FROM Trip t WHERE t.tripId = :tripId")
    Optional<Trip> findByTripIdForUpdate(@Param("tripId") String tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"bus", "driver", "route", "route.routeStops", "route.routeStops.stop"})
    @Query("SELECT t FROM Trip t WHERE LOWER(t.driver.driverId) = LOWER(:driverId) AND t.status IN :statuses ORDER BY t.actualStartTime DESC")
    List<Trip> findRunningTripsForDriverForUpdate(
            @Param("driverId") String driverId,
            @Param("statuses") Collection<TripStatus> statuses
    );

    @Override
    @EntityGraph(attributePaths = {"bus", "driver", "route", "route.routeStops", "route.routeStops.stop"})
    List<Trip> findAll();

    @Override
    @EntityGraph(attributePaths = {"bus", "driver", "route", "route.routeStops", "route.routeStops.stop"})
    Optional<Trip> findById(Long id);

    @EntityGraph(attributePaths = {"bus", "driver", "route", "route.routeStops", "route.routeStops.stop"})
    List<Trip> findByDriver_DriverId(String driverId);

    @EntityGraph(attributePaths = {"bus", "driver", "route", "route.routeStops", "route.routeStops.stop"})
    Optional<Trip> findByTripId(String tripId);

    Optional<Trip> findByDriver_DriverIdAndStatus(
            String driverId,
            TripStatus status
    );

    @EntityGraph(attributePaths = {"bus", "driver", "route", "route.routeStops", "route.routeStops.stop"})
    List<Trip> findByRoute_IdAndStatusIn(
            Long routeId,
            Collection<TripStatus> statuses
    );

    List<Trip> findByStatusAndScheduledEndTimeBefore(
            TripStatus status,
            Instant time
    );
}