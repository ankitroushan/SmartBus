# SmartBus – Real-Time Bus Tracking System

SmartBus is a web-based bus tracking system built using Java and Spring Boot. It allows an administrator to manage buses, drivers, routes, stops and trips. Drivers can share their live location during an active trip, while passengers can search for buses and track their movement on a map.

The application has three main parts: an admin dashboard, a driver dashboard and a public passenger page. The frontend is kept simple using HTML, CSS and JavaScript, while the main application logic is handled by the Spring Boot backend.

## Features :

### Admin
- Secure admin login.
- Manage buses, drivers, stops and routes.
- Activate or deactivate buses, drivers and routes.
- Assign a bus, driver and route while scheduling a trip.
- View scheduled, running and completed trips.
- Cancel scheduled trips or force-complete running/overdue trips.
- Manage admin accounts and reset driver PINs.

### Driver
- Secure driver login (email and PIN).
- View assigned trips.
- Start an assigned trip within its scheduled time window.
- Share the device's current location during an active trip.
- Complete an assigned trip.
- Location sharing stops when the trip is completed or the driver ends the session.

### Passenger
- Search for trips using starting and destination stops.
- View available trip details.
- Track a bus on the map.
- See route progress and the next stop.
- View estimated arrival times.
- Receive live location updates without repeatedly refreshing the page.

## Technology Stack

| Area | Technology |
|---|---|
| Programming Language | Java 21 |
| Backend Framework | Spring Boot 4.1.1 |
| Web Layer | Spring MVC |
| Security | Spring Security |
| Authentication | Session-based authentication |
| Password Encoding | BCrypt |
| Persistence | Spring Data JPA, Hibernate |
| Database | MySQL 8+ |
| Database Migration | Flyway |
| Real-time Communication | WebSocket with STOMP |
| Frontend | HTML5, CSS3, Vanilla JavaScript |
| Map | MapLibre GL JS |
| Map Tiles | OpenFreeMap |
| Road Route Geometry | OSRM |
| API Testing | Postman |
| Build Tool | Maven |

## Project Architecture

The backend follows a three-tier architecture:

```text
             Frontend
      (HTML, CSS, JavaScript)
                 |
                 | HTTP / REST
                 |
             Controller
                 |
                 v
               Service
                 |
                 v
             Repository
                 |
                 v
              MySQL
```

## How live tracking works

All route progress is calculated once, on the backend (`GeoUtils.computeRouteProgress`); the browser only displays it.

- **Not started** – until the bus is within 100 m of the first stop nothing is ticked and no ETA is shown.
- **Stops** – crossed strictly in order, when the bus is within 100 m of a stop or clearly past it on the way to the next one. A crossed stop never un-crosses and a stop is never skipped.
- **On route / off route** – if the bus is far from the line between its last and next stop, the state is `OFF_ROUTE` and the ETA is hidden until it rejoins the route.
- **ETA** – remaining distance (straight line × 1.22 road factor) ÷ current speed (25 km/h assumed when the bus is almost stationary), calculated for every upcoming stop so the passenger sees the ETA to *their* boarding and destination stop. OSRM is used only to draw the road line on the map.

## How to run 

You need Java 21 and MySQL 8.

1. Start MySQL. The database `busTrackingSystem` is created automatically on first run.
2. Set these environment variables:

   | Variable | Meaning |
      |---|---|
   | `DB_USERNAME` | MySQL user |
   | `DB_PASSWORD` | MySQL password |
   | `APP_INITIAL_ADMIN_PASSWORD` | Password of the first admin (at least 8 characters, letters and numbers) |

3. Run the app:

   ```bash
   ./mvnw spring-boot:run
   ```

4. Open `http://localhost:8080`.

The first admin id is `10001` and its password is the value of `APP_INITIAL_ADMIN_PASSWORD`. You must change this password on the first login. Log in from `/login.html`.

Note: browsers allow GPS only on `localhost` or on HTTPS. To use the driver page from a phone, open the app over HTTPS (for example, behind ngrok), and include that origin in `APP_WEBSOCKET_ALLOWED_ORIGINS`.

## API testing

Import `SmartBus_Postman_Collection.json` in Postman and set the variables at the top of the collection (admin password, driver id and PIN).