package com.ankit.bustracking.repository;

import com.ankit.bustracking.model.Route;
import com.ankit.bustracking.model.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteStopRepository extends JpaRepository<RouteStop,Long> {
    void deleteAllByRoute(Route route);


    List<RouteStop> findByStop_Id(Long stopId);
    List<RouteStop> findByRoute_Id(Long routeId);

}
