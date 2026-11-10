package com.ankit.bustracking.repository;

import com.ankit.bustracking.model.Route;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {

    @Override
    @EntityGraph(attributePaths = {"routeStops", "routeStops.stop"})
    List<Route> findAll();

    @Override
    @EntityGraph(attributePaths = {"routeStops", "routeStops.stop"})
    Optional<Route> findById(Long id);

    boolean existsByRouteCodeIgnoreCase(String routeCode);

    boolean existsByRouteCodeIgnoreCaseAndIdNot(String routeCode, Long id);
}
