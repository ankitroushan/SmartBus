"use strict";

/* Driver dashboard and live GPS tracking. */

const API = {
    me: "/api/me",
    myTrips: "/api/trips/my-trips",
    startTrip: id => `/api/trips/${id}/start`,
    completeTrip: id => `/api/trips/${id}/complete`,
    location: "/api/trips/location"
};

const state = {
    trips: [],
    activeTripId: null,
    activeTripUuid: null,
    watchId: null,
    sendingLocation: false,
    lastAcceptedTimestampMs: 0,
    updatesSentCount: 0,
    gpsSessionId: 0,
    locationRequestId: 0
};

// A 150 m ceiling rejects fixes too imprecise for useful city-bus tracking.
const MAX_ALLOWED_ACCURACY_METERS = 150;
const GPS_OPTIONS = {
    enableHighAccuracy: true,
    maximumAge: 0,
    timeout: 30000
};

/* BASIC HELPERS */

function byId(id) {
    return document.getElementById(id);
}

function setText(id, value) {
    const el = byId(id);
    if (el) el.textContent = value;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function notify(message, duration = 4000) {
    const box = byId("notification");
    if (!box) return;
    box.textContent = message;
    box.classList.remove("hidden");
    clearTimeout(notify.timer);
    notify.timer = setTimeout(() => box.classList.add("hidden"), duration);
}

function formatDate(value) {
    if (!value) return "—";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? escapeHtml(value) : date.toLocaleString();
}

function getCsrfTokenFromCookie() {
    try {
        const match = document.cookie.split("; ").find(row => row.startsWith("XSRF-TOKEN="));
        return match ? decodeURIComponent(match.split("=")[1]) : "";
    } catch (_) {
        return "";
    }
}

function isValidCoordinate(lat, lng) {
    return typeof lat === "number"
        && typeof lng === "number"
        && Number.isFinite(lat)
        && Number.isFinite(lng)
        && lat >= -90 && lat <= 90
        && lng >= -180 && lng <= 180
        && !(Math.abs(lat) < 1e-6 && Math.abs(lng) < 1e-6);
}

function setGpsStatus(message, type = "") {
    const element = byId("gps-status");
    if (element) {
        element.textContent = message;
        element.className = `gps-status ${type}`;
    }

    const badge = byId("gps-badge");
    if (badge) {
        if (type === "active") {
            badge.textContent = "LIVE GPS ACTIVE";
            badge.className = "status-badge status-running";
        } else if (type === "error") {
            badge.textContent = "GPS ATTENTION NEEDED";
            badge.className = "status-badge status-error";
        } else if (state.activeTripId) {
            badge.textContent = "CONNECTING GPS";
            badge.className = "status-badge status-scheduled";
        } else {
            badge.textContent = "STANDBY";
            badge.className = "status-badge status-unknown";
        }
    }

    const retryBtn = byId("retry-gps-btn");
    if (retryBtn) {
        retryBtn.classList.toggle("hidden", !state.activeTripId);
    }
}

/* API CLIENT */

async function apiRequest(url, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const csrfToken = getCsrfTokenFromCookie();

    const config = {
        method,
        credentials: "same-origin",
        headers: {
            Accept: "application/json",
            ...(options.body ? { "Content-Type": "application/json" } : {}),
            ...(csrfToken && method !== "GET" && method !== "HEAD" ? { "X-XSRF-TOKEN": csrfToken } : {})
        }
    };

    if (options.body) {
        config.body = JSON.stringify(options.body);
    }

    const response = await fetch(url, config);
    const contentType = response.headers.get("content-type") || "";
    let result = null;

    if (response.status !== 204) {
        result = contentType.includes("json") ? await response.json() : await response.text();
    }

    if (response.redirected && response.url.includes("/login")) {
        window.location.href = "/login.html";
        throw new Error("Your session has expired. Please login again.");
    }

    if (!response.ok) {
        if (response.status === 401) {
            window.location.href = "/login.html";
            throw new Error("Authentication required. Please login again.");
        }
        const message = (result && typeof result === "object")
            ? (result.message || result.detail || result.error || `Request failed: ${response.status}`)
            : (typeof result === "string" && result.trim() ? result : `Request failed: ${response.status}`);
        const err = new Error(message);
        err.status = response.status;
        throw err;
    }

    return result;
}

/*  TRIP LIST & ACTIONS  */

async function refreshTrips() {
    const container = byId("trip-list");
    container.innerHTML = "<p>Loading your trips...</p>";

    try {
        const trips = await apiRequest(API.myTrips);
        state.trips = Array.isArray(trips) ? trips : [];
        renderTrips();
        syncGpsWithTrips();
    } catch (error) {
        console.error(error);
        container.innerHTML = `<p>${escapeHtml(error.message)}</p>`;
        notify(error.message);
    }
}

function getStatusClass(status) {
    if (status === "SCHEDULED") return "status-scheduled";
    if (status === "RUNNING" || status === "OVERDUE") return "status-running";
    if (status === "COMPLETED" || status === "CANCELLED") return "status-completed";
    return "status-unknown";
}

function getTripAction(trip) {
    const status = String(trip.tripStatus).toUpperCase();
    if (status === "SCHEDULED") {
        return `<button class="primary-btn" data-trip-action="start" data-trip-id="${escapeHtml(trip.id)}">▶ Start Trip &amp; Share GPS</button>`;
    }
    if (status === "RUNNING" || status === "OVERDUE") {
        return `<button class="primary-btn complete-btn" data-trip-action="complete" data-trip-id="${escapeHtml(trip.id)}">■ Complete Trip &amp; Stop GPS</button>`;
    }
    if (status === "CANCELLED") {
        return `<span class="status-badge status-completed">✖ Trip Cancelled</span>`;
    }
    return `<span class="status-badge status-completed">✔ Trip Completed (${formatDate(trip.actualEndTime)})</span>`;
}

function renderTrips() {
    const container = byId("trip-list");
    if (state.trips.length === 0) {
        container.innerHTML = "<p>No trips assigned to you.</p>";
        return;
    }

    const order = { RUNNING: 0, OVERDUE: 0, SCHEDULED: 1, COMPLETED: 2, CANCELLED: 3 };
    const sorted = [...state.trips].sort((a, b) => (order[a.tripStatus] ?? 4) - (order[b.tripStatus] ?? 4));

    container.innerHTML = sorted.map(trip => {
        const isRunning = trip.tripStatus === "RUNNING" || trip.tripStatus === "OVERDUE";
        const busLabel = trip.busNumber ? `${trip.busNumber} (${trip.registrationNumber || ""})` : `Bus #${trip.busId}`;
        const routeLabel = trip.routeCode
            ? `${trip.routeCode}${trip.originStop && trip.destinationStop ? ` (${trip.originStop} → ${trip.destinationStop})` : ""}`
            : `Route #${trip.routeId}`;

        return `
        <div class="trip-card ${isRunning ? "trip-card-running" : ""}">
            <h3>
                <span>Trip #${escapeHtml(trip.id)} — ${escapeHtml(routeLabel)}</span>
                <span class="status-badge ${getStatusClass(trip.tripStatus)}">${escapeHtml(trip.tripStatus)}</span>
            </h3>
            <div class="trip-details">
                <p>Assigned Bus: <strong>${escapeHtml(busLabel)}</strong></p>
                <p>Route: <strong>${escapeHtml(routeLabel)}</strong></p>
                <p>Scheduled Start: <strong>${formatDate(trip.scheduledStartTime)}</strong></p>
                <p>Scheduled End: <strong>${formatDate(trip.scheduledEndTime)}</strong></p>
                <p>Actual Start: <strong>${formatDate(trip.actualStartTime)}</strong></p>
                <p>Actual Completion: <strong>${formatDate(trip.actualEndTime)}</strong></p>
            </div>
            ${getTripAction(trip)}
        </div>`;
    }).join("");
}

function captureInitialGpsFix() {
    return new Promise((resolve, reject) => {
        if (!navigator.geolocation) {
            reject(new Error("Browser GPS is not supported on this device."));
            return;
        }
        navigator.geolocation.getCurrentPosition(
            pos => resolve({
                latitude: Number(pos.coords.latitude),
                longitude: Number(pos.coords.longitude),
                accuracy: pos.coords.accuracy,
                capturedAt: new Date(pos.timestamp || Date.now()).toISOString()
            }),
            err => reject(new Error(err?.code === 1
                ? "Location permission denied. Allow GPS access to verify you are at the starting stop."
                : "Could not verify current GPS location. Please enable Location Services and try again.")),
            GPS_OPTIONS
        );
    });
}

async function startTrip(id, button) {
    button.disabled = true;
    button.textContent = "Verifying GPS at Start Stop...";

    try {
        const startFix = await captureInitialGpsFix();
        button.textContent = "Starting Trip...";
        const started = await apiRequest(API.startTrip(id), { method: "PUT", body: startFix });
        const matched = state.trips.find(t => Number(t.id) === Number(id));

        state.activeTripId = id;
        state.activeTripUuid = started?.tripId || matched?.tripId || null;
        state.lastAcceptedTimestampMs = 0;
        state.updatesSentCount = 0;
        setText("gps-count", "0");

        notify("Trip started! Requesting browser GPS permission...");
        startGpsTracking(true);
        await refreshTrips();
    } catch (error) {
        console.error(error);
        notify(error.message);
        button.disabled = false;
        button.textContent = "▶ Start Trip & Share GPS";
    }
}

async function completeTrip(id, button) {
    button.disabled = true;
    button.textContent = "Completing Trip...";

    try {
        await apiRequest(API.completeTrip(id), { method: "PUT" });
        stopGpsTracking();
        notify("Trip completed. GPS location sharing stopped.");
        await refreshTrips();
    } catch (error) {
        console.error(error);
        notify(error.message);
        button.disabled = false;
        button.textContent = "■ Complete Trip & Stop GPS";
    }
}

/*   BROWSER GPS TRACKING  */

function syncGpsWithTrips() {
    const runningTrip = state.trips.find(t => t.tripStatus === "RUNNING" || t.tripStatus === "OVERDUE");
    if (runningTrip) {
        state.activeTripId = runningTrip.id;
        state.activeTripUuid = runningTrip.tripId || null;
        startGpsTracking(false);
    } else {
        stopGpsTracking();
    }
}

function startGpsTracking(forceRestart = false) {
    if (forceRestart) {
        stopGpsWatcher();
    }

    if (!state.activeTripId || state.watchId !== null) {
        return;
    }

    if (!navigator.geolocation) {
        setGpsStatus("Geolocation is not supported by this browser. Use a supported browser on a secure HTTPS page.", "error");
        notify("This browser does not support device geolocation.");
        return;
    }

    const sessionId = state.gpsSessionId;
    setGpsStatus("Waiting for a genuine high-accuracy device GPS fix. Keep this page open...", "");

    try {


        state.watchId = navigator.geolocation.watchPosition(
            position => handleLocationSuccess(position, sessionId),
            error => handleLocationError(error, sessionId),
            GPS_OPTIONS
        );
    } catch (error) {
        console.error("Could not start browser GPS watcher:", error);
        state.watchId = null;
        setGpsStatus("Could not start browser GPS. Check permission and device location settings, then retry.", "error");
    }
}

async function handleLocationSuccess(position, sessionId) {
    if (sessionId !== state.gpsSessionId || !state.activeTripId || !position || !position.coords) {
        return;
    }

    const lat = Number(position.coords.latitude);
    const lng = Number(position.coords.longitude);
    const accuracy = position.coords.accuracy;
    const speed = position.coords.speed;

    if (!isValidCoordinate(lat, lng)) {
        setGpsStatus("The device returned invalid GPS coordinates. Waiting for a valid fix...", "error");
        return;
    }


    if (!Number.isFinite(position.timestamp) || position.timestamp <= 0) {
        setGpsStatus("The device GPS fix has no valid capture timestamp. Waiting for a fresh fix...", "error");
        return;
    }

    setText("gps-lat", lat.toFixed(6));
    setText("gps-lng", lng.toFixed(6));
    setText("gps-accuracy",
        (typeof accuracy === "number" && Number.isFinite(accuracy) && accuracy >= 0)
            ? `±${Math.round(accuracy)} m`
            : "—");
    setText("gps-speed",
        (typeof speed === "number" && Number.isFinite(speed) && speed >= 0)
            ? `${(speed * 3.6).toFixed(1)} km/h (Advisory)`
            : "Server-derived");

    if (typeof accuracy === "number" && Number.isFinite(accuracy)
            && (accuracy < 0 || accuracy > MAX_ALLOWED_ACCURACY_METERS)) {
        setGpsStatus(
            `GPS accuracy is too low (±${Math.round(accuracy)} m). Waiting for a better fix (maximum ${MAX_ALLOWED_ACCURACY_METERS} m).`,
            "error"
        );
        return;
    }

    await sendLocation(position, sessionId);
}

async function sendLocation(position, sessionId) {
    if (!state.activeTripId || state.sendingLocation || sessionId !== state.gpsSessionId) {
        return;
    }

    const fixTimeMs = position.timestamp;
    if (!Number.isFinite(fixTimeMs) || fixTimeMs <= state.lastAcceptedTimestampMs) {
        return;
    }

    const requestId = ++state.locationRequestId;
    state.sendingLocation = true;

    const payload = {
        latitude: Number(position.coords.latitude),
        longitude: Number(position.coords.longitude),
        capturedAt: new Date(fixTimeMs).toISOString()
    };

    if (state.activeTripUuid) {
        payload.tripId = state.activeTripUuid;
    }

    const accuracy = position.coords.accuracy;
    if (typeof accuracy === "number" && Number.isFinite(accuracy) && accuracy >= 0) {
        payload.accuracy = accuracy;
    }

    const speed = position.coords.speed;
    if (typeof speed === "number" && Number.isFinite(speed) && speed >= 0) {
        payload.speed = speed;
    }

    const heading = position.coords.heading;
    if (typeof heading === "number" && Number.isFinite(heading) && heading >= 0 && heading <= 360) {
        payload.heading = heading;
    }

    try {
        await apiRequest(API.location, { method: "POST", body: payload });


        if (sessionId !== state.gpsSessionId || requestId !== state.locationRequestId || !state.activeTripId) {
            return;
        }

        state.lastAcceptedTimestampMs = fixTimeMs;
        state.updatesSentCount += 1;
        setText("gps-count", String(state.updatesSentCount));
        setText("gps-last-sent", new Date().toLocaleTimeString());
        setGpsStatus(
            `● Live GPS sharing active (${payload.latitude.toFixed(5)}, ${payload.longitude.toFixed(5)}) — verified device fix accepted by server.`,
            "active"
        );
    } catch (error) {
        console.error("Location update failed:", error);
        if (sessionId === state.gpsSessionId && requestId === state.locationRequestId && state.activeTripId) {
            setGpsStatus(`Location transmission failed: ${error.message}. Waiting for the next fresh GPS fix.`, "error");
        }
    } finally {
        if (requestId === state.locationRequestId) {
            state.sendingLocation = false;
        }
    }
}

function handleLocationError(error, sessionId) {
    if (sessionId !== state.gpsSessionId || !state.activeTripId) {
        return;
    }

    switch (error?.code) {
        case 1: // PERMISSION_DENIED
            setGpsStatus("Browser GPS permission was denied. Allow Location access in browser settings, then click Retry GPS.", "error");
            stopGpsWatcher();
            notify("Location permission was denied. Allow it in browser settings and retry.");
            return;

        case 2: // POSITION_UNAVAILABLE

            setGpsStatus("Device location is temporarily unavailable. Keep this page open; the browser will continue waiting for a fresh GPS fix.", "");
            return;

        case 3: // TIMEOUT

            setGpsStatus("GPS is taking longer than expected. The browser is still listening for a genuine device fix. Check Location Services or use Retry GPS.", "");
            return;

        default:
            setGpsStatus("Unable to retrieve browser GPS right now. The watcher remains active; check device settings or retry.", "error");
    }
}

function stopGpsWatcher() {
    // Invalidate callbacks and in-flight responses from the previous GPS session.
    state.gpsSessionId += 1;
    state.locationRequestId += 1;
    state.sendingLocation = false;

    const watchId = state.watchId;
    state.watchId = null;

    if (watchId !== null && navigator.geolocation) {
        try {
            navigator.geolocation.clearWatch(watchId);
        } catch (error) {
            console.warn("Could not clear browser GPS watcher:", error);
        }
    }
}

function stopGpsTracking() {
    stopGpsWatcher();
    state.activeTripId = null;
    state.activeTripUuid = null;
    state.lastAcceptedTimestampMs = 0;
    state.updatesSentCount = 0;

    setText("gps-lat", "—");
    setText("gps-lng", "—");
    setText("gps-speed", "0.0 km/h (Stopped)");
    setText("gps-accuracy", "—");
    setText("gps-count", "0");
    setText("gps-last-sent", "—");
    setGpsStatus("GPS location sharing stopped. No active running trip.", "");
}

/* EVENTS & INIT  */

function setupEvents() {
    byId("refresh-trips")?.addEventListener("click", refreshTrips);

    byId("driver-logout-link")?.addEventListener("click", async event => {
        event.preventDefault();
        stopGpsTracking();
        try {
            await fetch("/logout", {
                method: "POST",
                credentials: "same-origin",
                headers: { "X-XSRF-TOKEN": getCsrfTokenFromCookie() }
            });
        } finally {
            window.location.href = "/login.html?logout";
        }
    });

    byId("retry-gps-btn")?.addEventListener("click", () => {
        if (!state.activeTripId) {
            notify("Start a scheduled trip first to share GPS.");
            return;
        }
        notify("Retrying browser GPS connection...");
        startGpsTracking(true);
    });

    document.addEventListener("click", async event => {
        const button = event.target.closest("[data-trip-action]");
        if (!button || button.disabled) return;
        const id = Number(button.dataset.tripId);
        const action = button.dataset.tripAction;
        if (!id) return;

        if (action === "start") {
            await startTrip(id, button);
        } else if (action === "complete") {
            await completeTrip(id, button);
        }
    });
}

document.addEventListener("DOMContentLoaded", async () => {
    try { await apiRequest(API.me); } catch (_) {}
    setupEvents();
    refreshTrips();
});
