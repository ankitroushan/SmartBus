"use strict";

/* SmartBus — Public Passenger Search & Live Bus Tracking */

const STALE_AFTER_MS = 30000;
const LIVE_SYNC_INTERVAL_MS = 4000;
const FALLBACK_SPEED_KMH = 25;
const ROAD_WINDING_FACTOR = 1.22;

const OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
const OPENFREEMAP_ATTRIBUTION =
    '<a href="https://openfreemap.org" target="_blank" rel="noopener">OpenFreeMap</a> &copy; <a href="https://www.openmaptiles.org/" target="_blank" rel="noopener">OpenMapTiles</a> Data from <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors | Routing via OSRM';
const DEFAULT_OSRM_BASE_URL = "https://router.project-osrm.org";
const DEFAULT_CENTER_LNGLAT = [86.6948, 24.4851];

const routeGeometryCache = new Map();

const state = {
    trips: [],
    allStops: [],
    activeTripId: null,
    activeTrip: null,
    path: [],
    roadCoords: [],
    stopRoadIndices: [],
    stopCumulativeMeters: [],
    totalRoadMeters: 0,
    routingSource: "OSRM Road Geometry",
    fromIndex: -1,
    toIndex: -1,
    autoFollowBus: true,
    stompClient: null,
    reconnectTimer: null,
    reconnectAttempt: 0,
    liveSyncTimer: null,
    fetchInFlight: false,
    sessionToken: 0,
    lastLocation: null,
    lastBearing: 0,
    firstFixShown: false
};

const maps = {
    overview: null,
    overviewStopMarkers: [],
    overviewHighlightMarkers: [],
    live: null,
    liveLoaded: false,
    stopMarkers: [],
    busMarker: null,
    busMarkerEl: null,
    pendingRouteDraw: false
};

/*  BASIC HELPERS  */

function byId(id) {
    return document.getElementById(id);
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function setText(id, value) {
    const el = byId(id);
    if (el) el.textContent = value;
}

function formatDate(value) {
    if (!value) return "—";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? escapeHtml(value) : date.toLocaleString();
}

function formatClock(date) {
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function sameName(a, b) {
    return String(a ?? "").trim().toLowerCase() === String(b ?? "").trim().toLowerCase();
}

function hasMapLibre() {
    return typeof window.maplibregl !== "undefined" && typeof window.maplibregl.Map === "function";
}

function isWebGLSupported() {
    if (!hasMapLibre()) return false;
    return typeof window.maplibregl.supported === "function" ? window.maplibregl.supported() : true;
}

function showMapFallbackMessage(container, message) {
    if (container) {
        container.innerHTML = `<div class="map-webgl-fallback">${escapeHtml(message)}</div>`;
    }
}

function formatAgo(ms) {
    const seconds = Math.max(0, Math.round(ms / 1000));
    if (seconds < 5) return "just now";
    if (seconds < 60) return `${seconds} s ago`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes} min ago`;
    return `${Math.floor(minutes / 60)} h ${minutes % 60} min ago`;
}

function formatDuration(minutes) {
    if (!Number.isFinite(minutes)) return "—";
    if (minutes < 1) return "< 1 min";
    const rounded = Math.round(minutes);
    if (rounded < 60) return `~${rounded} min (est.)`;
    const h = Math.floor(rounded / 60);
    const m = rounded % 60;
    return m === 0 ? `~${h} h (est.)` : `~${h} h ${m} min (est.)`;
}

function formatDistance(meters) {
    if (!Number.isFinite(meters)) return "—";
    return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(2)} km`;
}

function compassLabel(deg) {
    if (!Number.isFinite(deg)) return "—";
    const dirs = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];
    const idx = Math.round(((deg % 360) + 360) % 360 / 45) % 8;
    return `${dirs[idx]} (${Math.round(deg)}°)`;
}

function getCsrfTokenFromCookie() {
    try {
        const match = document.cookie.split("; ").find(row => row.startsWith("XSRF-TOKEN="));
        return match ? decodeURIComponent(match.split("=")[1]) : "";
    } catch (_) {
        return "";
    }
}

/*  NAVBAR SESSION CHECK  */

async function syncNavbarWithSession() {
    const nav = byId("public-nav-links");
    if (!nav) return;

    try {
        const res = await fetch("/api/me", { credentials: "same-origin", headers: { Accept: "application/json" } });
        if (!res.ok) return;
        const data = await res.json();
        if (!data || !data.authenticated) return;

        const dashboardUrl = data.role === "ROLE_ADMIN"
            ? "/admin/dashboard.html"
            : data.role === "ROLE_DRIVER"
                ? "/driver/dashboard.html"
                : "/index.html";

        nav.innerHTML = `
            <a href="index.html">Home</a>
            <a href="${dashboardUrl}">Dashboard</a>
            <a href="/logout" class="login-link" id="public-logout-link">Logout</a>
        `;

        byId("public-logout-link")?.addEventListener("click", async event => {
            event.preventDefault();
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
    } catch (_) {}
}

/*  DISTANCE & ROAD GEOMETRY  */

function toLatLngObj(point) {
    if (Array.isArray(point)) return { lng: Number(point[0]), lat: Number(point[1]) };
    return { lat: Number(point.latitude ?? point.lat), lng: Number(point.longitude ?? point.lng) };
}

function haversineMeters(a, b) {
    const R = 6371000;
    const toRad = d => d * Math.PI / 180;
    const p1 = toLatLngObj(a);
    const p2 = toLatLngObj(b);
    const dLat = toRad(p2.lat - p1.lat);
    const dLon = toRad(p2.lng - p1.lng);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(p1.lat)) * Math.cos(toRad(p2.lat)) * Math.sin(dLon / 2) ** 2;
    return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

async function fetchRoadGeometryForStops(routeId, stops, routingBaseUrl, routingProfile) {
    const directFallback = {
        coords: stops.map(s => [s.longitude, s.latitude]),
        stopIndices: stops.map((_, i) => i),
        source: "Direct Route Geometry"
    };
    if (!stops || stops.length < 2) return directFallback;
    let rawTotal = 0;
    for (let i = 1; i < stops.length; i++) rawTotal += haversineMeters(stops[i - 1], stops[i]);
    if (rawTotal < 500) return { ...directFallback, source: "Precision Stop Geometry" };

    const cacheKey = `${routeId}:${stops.map(s => `${s.longitude.toFixed(5)},${s.latitude.toFixed(5)}`).join(";")}`;
    if (routeGeometryCache.has(cacheKey)) return routeGeometryCache.get(cacheKey);

    const baseUrl = (routingBaseUrl || DEFAULT_OSRM_BASE_URL).replace(/\/+$/, "");
    const profile = routingProfile || "driving";
    const coordString = stops.map(s => `${s.longitude},${s.latitude}`).join(";");
    const url = `${baseUrl}/route/v1/${encodeURIComponent(profile)}/${coordString}?overview=full&geometries=geojson&steps=false`;

    try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 4000);
        const response = await fetch(url, { signal: controller.signal, headers: { Accept: "application/json" } });
        clearTimeout(timeoutId);
        if (!response.ok) throw new Error(`OSRM HTTP ${response.status}`);

        const data = await response.json();
        const geoCoords = data?.routes?.[0]?.geometry?.coordinates;
        if (!Array.isArray(geoCoords) || geoCoords.length < 2) throw new Error("Invalid OSRM geometry");

        const coords = geoCoords.map(([lng, lat]) => [Number(lng), Number(lat)]);
        const stopIndices = [];
        let searchStart = 0;

        for (let i = 0; i < stops.length; i++) {
            if (i === 0) { stopIndices.push(0); continue; }
            if (i === stops.length - 1) { stopIndices.push(coords.length - 1); continue; }
            let bestIdx = searchStart;
            let bestDist = Infinity;
            for (let v = searchStart; v < coords.length; v++) {
                const d = haversineMeters(stops[i], coords[v]);
                if (d < bestDist) { bestDist = d; bestIdx = v; }
            }
            stopIndices.push(bestIdx);
            searchStart = bestIdx;
        }

        const result = { coords, stopIndices, source: "OSRM Road Geometry" };
        routeGeometryCache.set(cacheKey, result);
        return result;
    } catch (_) {
        routeGeometryCache.set(cacheKey, directFallback);
        return directFallback;
    }
}

/** Distance of every stop from the first stop, using the same estimate as the backend (straight line x winding factor). */
function computeStopDistances(stops) {
    const raw = [0];
    for (let i = 1; i < stops.length; i++) {
        raw.push(raw[i - 1] + haversineMeters(stops[i - 1], stops[i]));
    }
    const factor = (raw[raw.length - 1] || 0) < 500 ? 1.0 : ROAD_WINDING_FACTOR;
    return raw.map(d => d * factor);
}

/*  OVERVIEW MAP & STOP SEARCH  */

function clearMarkerList(list) {
    while (list.length > 0) list.pop().remove();
}

function initOverviewMap() {
    const container = byId("overviewMap");
    if (!container) return;
    if (!hasMapLibre() || !isWebGLSupported()) {
        showMapFallbackMessage(container, "Live stop timeline and ETA telemetry are available below.");
        return;
    }

    try {
        maps.overview = new maplibregl.Map({
            container,
            style: OPENFREEMAP_STYLE_URL,
            center: DEFAULT_CENTER_LNGLAT,
            zoom: 12,
            attributionControl: false
        });
        maps.overview.addControl(new maplibregl.NavigationControl(), "top-right");
        maps.overview.addControl(new maplibregl.AttributionControl({ compact: false, customAttribution: OPENFREEMAP_ATTRIBUTION }));
        maps.overview.on("load", drawOverviewStops);
    } catch (_) {
        showMapFallbackMessage(container, "Map style could not be loaded.");
    }
}

function drawOverviewStops() {
    if (!maps.overview) return;
    clearMarkerList(maps.overviewStopMarkers);
    const bounds = new maplibregl.LngLatBounds();
    let count = 0;

    state.allStops.forEach(stop => {
        if (!Number.isFinite(stop.latitude) || !Number.isFinite(stop.longitude)) return;
        const lngLat = [stop.longitude, stop.latitude];
        bounds.extend(lngLat);
        count++;

        const dot = document.createElement("div");
        dot.style.cssText = "width:12px;height:12px;border-radius:50%;background:#fff;border:2.5px solid #1d4ed8;box-shadow:0 1px 4px rgba(15,23,42,0.3);";
        dot.title = `${stop.stopName} (ID: ${stop.id})`;

        const popup = new maplibregl.Popup({ offset: 10 }).setHTML(`<strong>${escapeHtml(stop.stopName)}</strong> (ID: ${escapeHtml(stop.id)})`);
        const marker = new maplibregl.Marker({ element: dot }).setLngLat(lngLat).setPopup(popup).addTo(maps.overview);
        maps.overviewStopMarkers.push(marker);
    });

    if (count > 0) maps.overview.fitBounds(bounds, { padding: 34, maxZoom: 14 });
}

function highlightSearchedStops(fromStopObj, toStopObj, fromText, toText) {
    if (!maps.overview) return;
    clearMarkerList(maps.overviewHighlightMarkers);
    const bounds = new maplibregl.LngLatBounds();
    let count = 0;

    const fromList = fromStopObj ? [fromStopObj] : state.allStops.filter(s => sameName(s.stopName, fromText));
    const toList = toStopObj ? [toStopObj] : state.allStops.filter(s => sameName(s.stopName, toText));

    [[fromList, "#16a34a", "B", "Boarding"], [toList, "#dc2626", "D", "Destination"]].forEach(([list, color, code, label]) => {
        list.forEach(stop => {
            const lngLat = [stop.longitude, stop.latitude];
            bounds.extend(lngLat);
            count++;

            const pin = document.createElement("div");
            pin.className = "stop-map-pin";
            pin.style.background = color;
            pin.style.borderColor = "#ffffff";
            pin.style.color = "#ffffff";
            pin.textContent = code;

            const popup = new maplibregl.Popup({ offset: 12 }).setHTML(`<strong>${escapeHtml(label)}:</strong> ${escapeHtml(stop.stopName)}`);
            const marker = new maplibregl.Marker({ element: pin }).setLngLat(lngLat).setPopup(popup).addTo(maps.overview);
            maps.overviewHighlightMarkers.push(marker);
        });
    });

    if (count > 0) maps.overview.fitBounds(bounds, { padding: 50, maxZoom: 15 });
}

async function loadStopsForSuggestions() {
    try {
        const res = await fetch("/api/stops", { headers: { Accept: "application/json" } });
        if (!res.ok) return;
        const stops = await res.json();
        if (!Array.isArray(stops)) return;

        state.allStops = stops;
        const datalist = byId("stops-list");
        if (datalist) {
            const names = [...new Set(stops.map(s => s.stopName))];
            datalist.innerHTML = names.map(n => `<option value="${escapeHtml(n)}"></option>`).join("");
        }
        drawOverviewStops();
    } catch (error) {
        console.error("Could not load stops:", error);
    }
}

function setupStopAutocomplete(inputId, hiddenId, dropdownId) {
    const input = byId(inputId);
    const hidden = byId(hiddenId);
    const dropdown = byId(dropdownId);
    if (!input || !hidden || !dropdown) return;

    function renderDropdown(query) {
        const q = String(query || "").trim().toLowerCase();
        const matches = state.allStops.filter(s => !q || String(s.stopName || "").toLowerCase().includes(q)).slice(0, 12);
        if (!matches.length) {
            dropdown.classList.add("hidden");
            dropdown.innerHTML = "";
            return;
        }
        dropdown.innerHTML = matches.map(stop => `
            <div class="suggestion-item" data-stop-id="${escapeHtml(stop.id)}" data-stop-name="${escapeHtml(stop.stopName)}">
                <span class="suggestion-name">${escapeHtml(stop.stopName)}</span>
                <span class="suggestion-meta">Stop #${escapeHtml(stop.id)} · ${Number(stop.latitude).toFixed(3)}, ${Number(stop.longitude).toFixed(3)}</span>
            </div>
        `).join("");
        dropdown.classList.remove("hidden");
    }

    input.addEventListener("input", () => {
        const exact = state.allStops.filter(s => sameName(s.stopName, input.value));
        if (exact.length === 1) {
            hidden.value = String(exact[0].id);
            dropdown.classList.add("hidden");
            return;
        }
        hidden.value = "";
        renderDropdown(input.value);
    });

    input.addEventListener("focus", () => {
        if (state.allStops.length) renderDropdown(input.value);
    });

    dropdown.addEventListener("mousedown", event => {
        const item = event.target.closest(".suggestion-item");
        if (!item) return;
        event.preventDefault();
        input.value = item.dataset.stopName;
        hidden.value = item.dataset.stopId;
        dropdown.classList.add("hidden");
    });

    input.addEventListener("blur", () => setTimeout(() => dropdown.classList.add("hidden"), 150));
}

async function searchBuses(event) {
    event.preventDefault();
    const fromInput = byId("fromStop");
    const toInput = byId("toStop");
    const fromIdInput = byId("fromStopId");
    const toIdInput = byId("toStopId");

    const from = fromInput.value.trim();
    const to = toInput.value.trim();
    if (!from || !to) {
        alert("Please enter both starting stop and destination stop.");
        return;
    }

    let fromStopId = fromIdInput.value ? Number(fromIdInput.value) : null;
    let toStopId = toIdInput.value ? Number(toIdInput.value) : null;

    if (!fromStopId) {
        const matches = state.allStops.filter(s => sameName(s.stopName, from));
        if (matches.length === 1) { fromStopId = matches[0].id; fromIdInput.value = String(fromStopId); }
    }
    if (!toStopId) {
        const matches = state.allStops.filter(s => sameName(s.stopName, to));
        if (matches.length === 1) { toStopId = matches[0].id; toIdInput.value = String(toStopId); }
    }

    if ((fromStopId && toStopId && fromStopId === toStopId) || (!fromStopId && !toStopId && sameName(from, to))) {
        alert("Starting stop and destination stop must be different.");
        return;
    }

    const section = byId("search-results-section");
    const listContainer = byId("search-results-list");
    const title = byId("search-results-title");
    const button = byId("searchButton");

    stopLiveTracking();
    section.classList.remove("hidden");
    title.textContent = `Available Buses: "${from}" → "${to}"`;
    listContainer.innerHTML = "<p>Searching matching routes and active buses...</p>";
    button.disabled = true;
    button.textContent = "Searching...";

    highlightSearchedStops(
        fromStopId ? state.allStops.find(s => s.id === fromStopId) : null,
        toStopId ? state.allStops.find(s => s.id === toStopId) : null,
        from,
        to
    );

    try {
        const params = new URLSearchParams({ from, to });
        if (fromStopId) params.set("fromStopId", String(fromStopId));
        if (toStopId) params.set("toStopId", String(toStopId));

        const response = await fetch(`/api/trips/search?${params.toString()}`, { headers: { Accept: "application/json" } });
        const contentType = response.headers.get("content-type") || "";
        const data = contentType.includes("json") ? await response.json() : await response.text();

        if (!response.ok) {
            throw new Error((typeof data === "string" && data.trim()) ? data : (data?.message || "No matching buses found."));
        }

        state.trips = Array.isArray(data) ? data : [];
        renderSearchResults();
        section.scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (error) {
        state.trips = [];
        listContainer.innerHTML = `<p>${escapeHtml(error.message)}</p>`;
    } finally {
        button.disabled = false;
        button.textContent = "Search Available Buses →";
    }
}

function renderSearchResults() {
    const listContainer = byId("search-results-list");
    if (state.trips.length === 0) {
        listContainer.innerHTML = "<p>No scheduled or running buses found for this route and travel direction.</p>";
        return;
    }

    listContainer.innerHTML = state.trips.map(trip => {
        const isRunning = trip.tripStatus === "RUNNING" || trip.tripStatus === "OVERDUE";
        const statusClass = isRunning ? "status-running" : "status-scheduled";
        const label = isRunning ? "🚌 Open Live Road Tracking" : "🗺️ View Route & Track";
        const activeClass = state.activeTripId === trip.tripId ? "result-card-active" : "";

        return `
            <div class="result-card ${activeClass}" data-card-trip-id="${escapeHtml(trip.tripId)}">
                <div class="result-card-header">
                    <h3>Bus ${escapeHtml(trip.busNumber)}</h3>
                    <span class="status-pill ${statusClass}">${escapeHtml(trip.tripStatus)}</span>
                </div>
                <div class="result-meta">
                    <div><strong>Registration:</strong> ${escapeHtml(trip.registrationNumber)}</div>
                    <div><strong>Route Code:</strong> ${escapeHtml(trip.routeCode)}</div>
                    <div><strong>Journey:</strong> ${escapeHtml(trip.fromStop)} → ${escapeHtml(trip.toStop)}</div>
                    <div><strong>Scheduled Window:</strong> ${formatDate(trip.scheduledStartTime)} — ${formatDate(trip.scheduledEndTime)}</div>
                </div>
                <button type="button" class="track-btn" data-track-trip-id="${escapeHtml(trip.tripId)}">${label}</button>
            </div>
        `;
    }).join("");
}

/* LIVE ROAD MAP */

function createBusMarkerElement(stopped) {
    const wrapper = document.createElement("div");
    wrapper.className = "bus-live-wrapper";
    wrapper.innerHTML = `
        <div class="bus-live-halo${stopped ? " stopped" : ""}"></div>
        <div class="bus-direction-ring"><div class="bus-direction-arrow"></div></div>
        <div class="bus-pin${stopped ? " bus-pin-stopped" : ""}">🚌</div>
    `;
    return wrapper;
}

function createStopPinElement(index, isFrom, isTo, isPassed) {
    const el = document.createElement("div");
    let cls = "stop-map-pin";
    let label = String(index + 1);
    if (isPassed) { cls += " stop-map-pin-passed"; label = "✓"; }
    else if (isFrom) { cls += " stop-map-pin-boarding"; label = "B"; }
    else if (isTo) { cls += " stop-map-pin-dest"; label = "D"; }
    el.className = cls;
    el.textContent = label;
    return el;
}

function buildLineFeature(coordsLngLat) {
    return {
        type: "Feature",
        properties: {},
        geometry: { type: "LineString", coordinates: Array.isArray(coordsLngLat) ? coordsLngLat : [] }
    };
}

function ensureLiveMapGeoJsonLayers(map) {
    if (!map) return;
    if (!map.getSource("remaining-route")) {
        map.addSource("remaining-route", { type: "geojson", data: buildLineFeature([]) });
    }
    if (!map.getLayer("remaining-route-layer")) {
        map.addLayer({
            id: "remaining-route-layer",
            type: "line",
            source: "remaining-route",
            layout: { "line-join": "round", "line-cap": "round" },
            paint: { "line-color": "#2563eb", "line-width": 5, "line-opacity": 0.92 }
        });
    }
    if (!map.getSource("travelled-route")) {
        map.addSource("travelled-route", { type: "geojson", data: buildLineFeature([]) });
    }
    if (!map.getLayer("travelled-route-layer")) {
        map.addLayer({
            id: "travelled-route-layer",
            type: "line",
            source: "travelled-route",
            layout: { "line-join": "round", "line-cap": "round" },
            paint: { "line-color": "#10b981", "line-width": 6, "line-opacity": 0.95 }
        });
    }
}

function ensureLiveMap() {
    if (maps.live) return maps.live;
    const container = byId("liveMap");
    if (!container) return null;

    if (!hasMapLibre() || !isWebGLSupported()) {
        showMapFallbackMessage(container, "Follow live stop-by-stop progress and ETA in the panel.");
        return null;
    }

    try {
        maps.live = new maplibregl.Map({
            container,
            style: OPENFREEMAP_STYLE_URL,
            center: DEFAULT_CENTER_LNGLAT,
            zoom: 13,
            attributionControl: false
        });
        maps.live.addControl(new maplibregl.NavigationControl(), "top-right");
        maps.live.addControl(new maplibregl.AttributionControl({ compact: false, customAttribution: OPENFREEMAP_ATTRIBUTION }));
        maps.live.on("load", () => {
            maps.liveLoaded = true;
            ensureLiveMapGeoJsonLayers(maps.live);
            if (maps.pendingRouteDraw) {
                maps.pendingRouteDraw = false;
                drawInitialRoadRouteOnMap();
            }
        });
    } catch (_) {
        showMapFallbackMessage(container, "Live map style failed to load.");
        return null;
    }
    return maps.live;
}

function resetLiveMapLayers() {
    if (maps.busMarker) {
        maps.busMarker.remove();
        maps.busMarker = null;
        maps.busMarkerEl = null;
    }
    clearMarkerList(maps.stopMarkers);
    if (maps.live && maps.liveLoaded) {
        maps.live.getSource("travelled-route")?.setData(buildLineFeature([]));
        maps.live.getSource("remaining-route")?.setData(buildLineFeature([]));
    }
}

async function loadRouteAndRoadGeometry(trip) {
    const response = await fetch(`/api/route/${encodeURIComponent(trip.routeId)}`, { headers: { Accept: "application/json" } });
    if (!response.ok) throw new Error(`Could not load route (${response.status})`);

    const route = await response.json();
    const stops = (Array.isArray(route.routeStops) ? [...route.routeStops] : [])
        .sort((a, b) => Number(a.sequenceNo) - Number(b.sequenceNo))
        .filter(s => Number.isFinite(s.latitude) && Number.isFinite(s.longitude));

    const roadData = await fetchRoadGeometryForStops(trip.routeId, stops, route.routingBaseUrl, route.routingProfile);
    const stopCumulativeMeters = computeStopDistances(stops);

    return {
        stops,
        roadCoords: roadData.coords,
        stopRoadIndices: roadData.stopIndices,
        routingSource: roadData.source,
        stopCumulativeMeters,
        totalRoadMeters: stopCumulativeMeters[stopCumulativeMeters.length - 1] || 0
    };
}

function fitMapToCoordinates(map, coordsLngLat) {
    if (!map || !Array.isArray(coordsLngLat) || coordsLngLat.length === 0) return;
    const bounds = new maplibregl.LngLatBounds();
    coordsLngLat.forEach(c => bounds.extend(c));
    map.fitBounds(bounds, { padding: 42, maxZoom: 16 });
}

function drawInitialRoadRouteOnMap() {
    const map = maps.live;
    if (!map) return;
    if (!maps.liveLoaded) {
        maps.pendingRouteDraw = true;
        return;
    }

    resetLiveMapLayers();
    ensureLiveMapGeoJsonLayers(map);

    const coords = state.roadCoords.length > 1 ? state.roadCoords : state.path.map(s => [s.longitude, s.latitude]);
    if (coords.length > 1) {
        map.getSource("remaining-route")?.setData(buildLineFeature(coords));
        map.getSource("travelled-route")?.setData(buildLineFeature([]));
    }

    drawStopMarkersOnLiveMap(-1);
    fitMapToCoordinates(map, coords);
}

function drawStopMarkersOnLiveMap(passedUpToIndex) {
    if (!maps.live) return;
    clearMarkerList(maps.stopMarkers);

    state.path.forEach((stop, index) => {
        const isFrom = index === state.fromIndex;
        const isTo = index === state.toIndex;
        const isPassed = index <= passedUpToIndex;
        const roleLabel = isFrom ? " [BOARDING STOP]" : isTo ? " [DESTINATION]" : isPassed ? " [Passed]" : "";
        const distKm = ((state.stopCumulativeMeters[index] || 0) / 1000).toFixed(2);

        const pinEl = createStopPinElement(index, isFrom, isTo, isPassed);
        pinEl.title = `#${index + 1} ${stop.stopName}${roleLabel} (${distKm} km)`;

        const popup = new maplibregl.Popup({ offset: 12 }).setHTML(
            `<strong>#${index + 1} ${escapeHtml(stop.stopName)}</strong>${escapeHtml(roleLabel)}<br><small>${distKm} km from start</small>`
        );
        const marker = new maplibregl.Marker({ element: pinEl }).setLngLat([stop.longitude, stop.latitude]).setPopup(popup).addTo(maps.live);
        maps.stopMarkers.push(marker);
    });
}

/** Green = road from origin through crossed stops right up to the live bus position; blue = remaining route ahead. */
function updateRouteLines(stopsCrossed, bus, onRoute) {
    const map = maps.live;
    if (!map || !maps.liveLoaded || state.roadCoords.length < 2) return;
    ensureLiveMapGeoJsonLayers(map);
    if (stopsCrossed <= 0) {
        map.getSource("travelled-route")?.setData(buildLineFeature([]));
        map.getSource("remaining-route")?.setData(buildLineFeature(state.roadCoords));
        return;
    }
    if (stopsCrossed >= state.path.length) {
        map.getSource("travelled-route")?.setData(buildLineFeature(state.roadCoords));
        map.getSource("remaining-route")?.setData(buildLineFeature([]));
        return;
    }
    const startVert = state.stopRoadIndices[stopsCrossed - 1] ?? 0;
    const endVert = state.stopRoadIndices[Math.min(stopsCrossed, state.stopRoadIndices.length - 1)] ?? (state.roadCoords.length - 1);
    if (bus && onRoute && endVert > startVert) {
        const k = Math.cos(bus.latitude * Math.PI / 180);
        const px = bus.longitude * k, py = bus.latitude;
        let bestSeg = startVert, bestDist = Infinity, bestPt = state.roadCoords[startVert];
        for (let i = startVert; i < endVert; i++) {
            const a = state.roadCoords[i], b = state.roadCoords[i + 1];
            const dx = (b[0] - a[0]) * k, dy = b[1] - a[1];
            const len2 = dx * dx + dy * dy;
            const t = Math.max(0, Math.min(1, len2 === 0 ? 0 : (((px - a[0] * k) * dx + (py - a[1]) * dy) / len2)));
            const pt = [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
            const d = haversineMeters(bus, pt);
            if (d < bestDist) { bestDist = d; bestSeg = i; bestPt = pt; }
        }
        map.getSource("travelled-route")?.setData(buildLineFeature([...state.roadCoords.slice(0, bestSeg + 1), bestPt]));
        map.getSource("remaining-route")?.setData(buildLineFeature([bestPt, ...state.roadCoords.slice(bestSeg + 1)]));
        return;
    }
    map.getSource("travelled-route")?.setData(buildLineFeature(state.roadCoords.slice(0, startVert + 1)));
    map.getSource("remaining-route")?.setData(buildLineFeature(state.roadCoords.slice(startVert)));
}

function updateBusMarkerOnMap(bus, stopped, bearing) {
    const map = maps.live;
    if (!map) return;
    if (Number.isFinite(bearing)) state.lastBearing = bearing;

    if (!maps.busMarker) {
        maps.busMarkerEl = createBusMarkerElement(stopped);
        maps.busMarker = new maplibregl.Marker({
            element: maps.busMarkerEl,
            rotation: state.lastBearing,
            rotationAlignment: "map"
        }).setLngLat([bus.longitude, bus.latitude]).addTo(map);
        return;
    }

    maps.busMarkerEl?.querySelector(".bus-live-halo")?.classList.toggle("stopped", Boolean(stopped));
    maps.busMarkerEl?.querySelector(".bus-pin")?.classList.toggle("bus-pin-stopped", Boolean(stopped));
    maps.busMarker.setLngLat([bus.longitude, bus.latitude]);
    maps.busMarker.setRotation(state.lastBearing);
}

/*  STOP-BY-STOP TIMELINE & LIVE PANEL  */

function renderWimtTimeline(bus, location, status, stale) {
    const container = byId("wimt-timeline-list");
    const countEl = byId("wimt-stops-count");
    if (!container) return;

    const stops = state.path;
    if (!stops.length) {
        container.innerHTML = `<p class="wimt-empty">Route stops loading...</p>`;
        return;
    }
    if (countEl) countEl.textContent = `${stops.length} stops`;

    // Everything below comes from the backend: stopsCrossed, trackingState, distances and ETAs.
    const live = Boolean(bus) && (status === "RUNNING" || status === "OVERDUE");
    const crossed = Number(location?.stopsCrossed ?? 0);
    const trackState = location?.trackingState;
    const onRoute = live && trackState === "ON_ROUTE";
    const etaOf = v => (v != null && Number.isFinite(Number(v)) ? Number(v) : null);
    const stamp = stale ? " [stale]" : "";

    container.innerHTML = stops.map((stop, index) => {
        const isNextStop = live && index === crossed;
        const isHere = live && index === crossed - 1 && Boolean(location?.currentStop);
        const passed = index < crossed && !isHere;
        const stopState = isHere ? "current" : passed ? "passed" : "upcoming";

        const nodeClass = `wimt-node wimt-node-${stopState}`;
        const nodeText = stopState === "passed" ? "✓" : stopState === "current" ? "🚌" : String(index + 1);
        const railClass = passed || isHere
            ? (index === crossed - 1 ? "wimt-rail-line wimt-rail-line-active" : "wimt-rail-line wimt-rail-line-passed")
            : "wimt-rail-line";
        const cardClass = stopState === "current" ? "wimt-stop-card wimt-stop-card-current" : stopState === "passed" ? "wimt-stop-card wimt-stop-card-passed" : "wimt-stop-card";

        const badges = [];
        if (index === state.fromIndex) badges.push(`<span class="wimt-tag wimt-tag-boarding">Boarding</span>`);
        if (index === state.toIndex) badges.push(`<span class="wimt-tag wimt-tag-dest">Destination</span>`);
        if (isHere) badges.push(`<span class="wimt-tag wimt-tag-here">🚌 Bus Here</span>`);
        else if (passed) badges.push(`<span class="wimt-tag wimt-tag-passed">Passed</span>`);
        else if (isNextStop) badges.push(`<span class="wimt-tag wimt-tag-next">Next Stop</span>`);

        let etaText = `${formatDistance(state.stopCumulativeMeters[index] || 0)} from origin`;
        const nextEta = etaOf(location?.nextStopEtaMinutes);
        if (status === "COMPLETED") etaText = passed ? "Completed" : "Not reached";
        else if (isHere) etaText = "Bus is at this stop";
        else if (passed) etaText = "Departed";
        else if (isNextStop && onRoute && nextEta !== null) etaText = `${formatDistance(Number(location.distanceToNextStopMeters))} away · Est. ETA ${formatDuration(nextEta)}${stamp}`;
        else if (onRoute && etaOf(location?.stopEtaMinutes?.[index]) !== null) etaText = `Est. ETA ${formatDuration(etaOf(location.stopEtaMinutes[index]))}${stamp}`;
        else if (isNextStop && trackState === "NOT_STARTED") etaText = `Bus is ${formatDistance(Number(location.distanceFromRouteMeters))} away · journey starts here`;
        else if (isNextStop && trackState === "OFF_ROUTE") etaText = "Bus is off route · ETA unavailable";

        let inTransitHtml = "";
        if (live && index === crossed - 1 && !isHere && crossed < stops.length) {
            inTransitHtml = `
                <div class="wimt-in-transit-pill">
                    <span>🚌 <strong>Live Bus</strong> ${onRoute ? "in transit" : "off route"}</span>
                    <span>${formatDistance(Number(location.distanceToNextStopMeters))} to ${escapeHtml(stops[crossed].stopName)}</span>
                </div>`;
        }

        return `
            <div class="wimt-stop-row">
                <div class="wimt-rail-col">
                    <div class="${nodeClass}">${nodeText}</div>
                    ${index < stops.length - 1 ? `<div class="${railClass}"></div>` : ""}
                </div>
                <div class="wimt-stop-Body">
                    <div class="${cardClass}">
                        <div class="wimt-stop-top">
                            <span class="wimt-stop-name">${index + 1}. ${escapeHtml(stop.stopName)}</span>
                            <div class="wimt-stop-badges">${badges.join("")}</div>
                        </div>
                        <div class="wimt-stop-sub">
                            <span>Route km: ${((state.stopCumulativeMeters[index] || 0) / 1000).toFixed(2)} km</span>
                            <strong>${escapeHtml(etaText)}</strong>
                        </div>
                    </div>
                    ${inTransitHtml}
                </div>
            </div>`;
    }).join("");
}

function showBanner(message, kind) {
    const banner = byId("trip-banner");
    if (!banner) return;
    if (!message) {
        banner.classList.add("hidden");
        banner.textContent = "";
        return;
    }
    banner.textContent = message;
    banner.className = `trip-banner trip-banner-${kind || "info"}`;
}

function resetLivePanel() {
    [
        "eta-from-value", "eta-to-value", "track-speed", "track-next-stop",
        "track-prev-stop", "track-current-stop", "track-dest-stop",
        "track-distance-remaining", "info-bus-number", "info-route-name",
        "track-trip-status", "track-latitude", "track-longitude", "track-updated-at"
    ].forEach(id => setText(id, "—"));
    ["eta-from-sub", "eta-to-sub", "track-speed-note", "track-distance-travelled"].forEach(id => setText(id, " "));
    setText("jp-travelled-text", "0.00 km travelled");
    setText("jp-remaining-text", "0.00 km remaining");
    setText("jp-percent-text", "0%");
    if (byId("jp-bar-fill")) byId("jp-bar-fill").style.width = "0%";
    if (byId("jp-bus-knob")) byId("jp-bus-knob").style.left = "0%";
    showBanner("");
}

function updateLivePanel(location) {
    const trip = state.activeTrip;
    const status = location.tripStatus || (trip ? trip.tripStatus : "SCHEDULED");
    const hasFix = location.latitude !== null && location.latitude !== undefined
        && location.longitude !== null && location.longitude !== undefined;

    const pulseDot = byId("live-pulse-dot");
    if (pulseDot) {
        pulseDot.className = (status === "RUNNING" || status === "OVERDUE") && hasFix
            ? "live-pulse-indicator"
            : "live-pulse-indicator paused";
    }

    const busNum = location.busNumber || trip?.busNumber || "—";
    const regNum = location.registrationNumber || trip?.registrationNumber || "";
    const routeCode = location.routeCode || trip?.routeCode || "—";
    const originStopName = state.path[0]?.stopName || trip?.fromStop || "Origin";
    const destStopName = location.destinationStop || state.path[state.path.length - 1]?.stopName || trip?.toStop || "Destination";

    setText("info-bus-number", regNum ? `${busNum} (${regNum})` : busNum);
    setText("info-route-name", `${routeCode} (${originStopName} → ${destStopName})`);
    setText("track-trip-status", status);
    setText("jp-origin-name", originStopName);
    setText("jp-dest-name", destStopName);
    setText("track-dest-stop", destStopName);

    if (status === "COMPLETED") {
        setText("tracking-status-text", "This trip has been completed. Live GPS tracking has stopped.");
        showBanner(Number(location.stopsCrossed ?? 0) >= state.path.length ? "✔ Trip completed. The bus has reached its final destination." : "■ Trip ended before reaching the final destination.", "info");
    } else if (status === "CANCELLED") {
        setText("tracking-status-text", "This trip was cancelled.");
        showBanner("✖ This trip has been cancelled by the administrator.", "warn");
    } else if (status === "SCHEDULED") {
        setText("tracking-status-text", "Scheduled trip — waiting for the driver to start the trip.");
        showBanner(`Bus is scheduled to start at ${trip ? formatDate(trip.scheduledStartTime) : ""}. Live tracking will begin automatically.`, "info");
    } else if (!hasFix) {
        setText("tracking-status-text", "Trip is running. Waiting for the driver's first live GPS fix...");
        showBanner("Trip is running. Waiting for initial GPS coordinates from the driver...", "info");
    }

    if (!hasFix) {
        setText("track-latitude", status === "SCHEDULED" ? "Not started" : "Waiting for GPS");
        setText("track-longitude", status === "SCHEDULED" ? "Not started" : "Waiting for GPS");
        setText("track-updated-at", "—");
        setText("track-current-stop", location.currentStop || originStopName);
        setText("track-next-stop", location.nextStop || state.path[1]?.stopName || destStopName);
        const totalM = location.totalRouteDistanceMeters ?? state.totalRoadMeters;
        setText("track-distance-remaining", formatDistance(totalM));
        setText("track-distance-travelled", `0 m travelled of ${formatDistance(totalM)}`);
        setText("jp-remaining-text", `${formatDistance(totalM)} remaining`);
        renderWimtTimeline(null, location, status, false);
        return;
    }

    const bus = { latitude: Number(location.latitude), longitude: Number(location.longitude) };
    const updatedAtMs = location.locationUpdatedAt ? new Date(location.locationUpdatedAt).getTime() : NaN;
    const ageMs = Number.isNaN(updatedAtMs) ? 0 : Math.max(0, Date.now() - updatedAtMs);
    const live = status === "RUNNING" || status === "OVERDUE";
    const stale = live && ageMs > STALE_AFTER_MS;
    const trackState = location.trackingState;
    const onRoute = trackState === "ON_ROUTE";

    setText("track-latitude", bus.latitude.toFixed(6));
    setText("track-longitude", bus.longitude.toFixed(6));
    setText("track-updated-at", `${formatDate(location.locationUpdatedAt)} (${formatAgo(ageMs)})`);

    const reported = Number(location.speedKmh);
    const reportedOk = location.speedKmh != null && Number.isFinite(reported);
    const isStopped = status === "COMPLETED" || (reportedOk && reported < 1.0);
    setText("track-speed", status === "COMPLETED" ? "0.0 km/h" : reportedOk ? `${reported.toFixed(1)} km/h${reported < 1.0 ? " (Stationary)" : ""}` : "—");
    setText("track-speed-note", status === "COMPLETED" ? "Trip completed"
        : reportedOk && reported < 1.0 ? `Stationary · ETA uses ~${FALLBACK_SPEED_KMH} km/h average`
        : "Server-computed GPS speed");

    const displayBearing = location.bearing != null && Number.isFinite(Number(location.bearing)) ? Number(location.bearing) : state.lastBearing;
    setText("track-direction-heading", `Heading: ${compassLabel(displayBearing)}`);

    const crossed = status === "COMPLETED" ? state.path.length : Number(location.stopsCrossed ?? 0);
    updateRouteLines(crossed, bus, onRoute);

    const totalMeters = Math.max(1, Number(location.totalRouteDistanceMeters ?? state.totalRoadMeters));
    const travelledMeters = Number(location.distanceTravelledMeters ?? 0);
    const remainingMeters = Number(location.distanceRemainingMeters ?? Math.max(0, totalMeters - travelledMeters));
    const pct = Number(location.progressPercent ?? 0);

    setText("jp-travelled-text", `${formatDistance(travelledMeters)} travelled`);
    setText("jp-remaining-text", `${formatDistance(remainingMeters)} remaining`);
    setText("jp-percent-text", `${pct.toFixed(1)}%`);
    if (byId("jp-bar-fill")) byId("jp-bar-fill").style.width = `${pct}%`;
    if (byId("jp-bus-knob")) byId("jp-bus-knob").style.left = `${pct}%`;
    setText("track-distance-remaining", formatDistance(remainingMeters));
    setText("track-distance-travelled", `${formatDistance(travelledMeters)} travelled of ${formatDistance(totalMeters)}`);

    drawStopMarkersOnLiveMap(crossed - 1);

    const nextLabel = location.nextStop
        ? `${location.nextStop}${Number.isFinite(location.distanceToNextStopMeters) ? ` (${formatDistance(location.distanceToNextStopMeters)})` : ""}`
        : "Reached Destination";
    setText("track-prev-stop", location.previousStop || "Not reached yet");
    setText("track-current-stop", location.currentStop || (crossed === 0 ? "Not at first stop yet" : "In transit"));
    setText("track-next-stop", status === "COMPLETED" ? "Reached Destination" : nextLabel);

    // The two ETA cards answer the passenger's own question: when does the bus reach MY boarding and MY destination stop?
    const etaCard = (labelId, valueId, subId, role, index) => {
        const name = state.path[index]?.stopName || "—";
        const minutes = onRoute && Array.isArray(location.stopEtaMinutes) ? location.stopEtaMinutes[index] : null;
        setText(labelId, `Est. ETA to your ${role} (${name})`);
        if (index >= 0 && index < crossed) {
            const atStop = index === crossed - 1 && location.currentStop;
            setText(valueId, role === "destination" ? "Reached" : atStop ? "Here" : "Passed");
            setText(subId, role === "destination" ? "Bus has reached this stop" : atStop ? "Bus is at your stop now" : "Bus has already left your stop");
        } else if (minutes != null && Number.isFinite(Number(minutes))) {
            setText(valueId, formatDuration(Number(minutes)));
            setText(subId, stale ? "GPS signal delayed – approximate estimate"
                : `${index - crossed + 1} stop(s) away · Est. arrival ≈ ${formatClock(new Date(Date.now() + Number(minutes) * 60000))}`);
        } else {
            setText(valueId, "—");
            setText(subId, trackState === "OFF_ROUTE" ? "Bus is off route" : "Starts when the bus reaches the first stop");
        }
    };
    etaCard("eta-from-label", "eta-from-value", "eta-from-sub", "boarding stop", Math.max(0, state.fromIndex));
    etaCard("eta-to-label", "eta-to-value", "eta-to-sub", "destination", state.toIndex >= 0 ? state.toIndex : state.path.length - 1);

    renderWimtTimeline(bus, location, status, stale);

    if (live) {
        let message = "Live GPS active · receiving real-time updates.";
        let banner = "";
        let kind = "warn";
        if (stale) {
            message = `GPS signal delayed – last update ${formatAgo(ageMs)}.`;
            banner = `⚠ No new GPS update for ${formatAgo(ageMs).replace(" ago", "")}. Showing last verified position.`;
        } else if (trackState === "NOT_STARTED") {
            message = `Bus has not reached the first stop (${originStopName}) yet.`;
            banner = `Bus is ${formatDistance(Number(location.distanceFromRouteMeters))} away from ${originStopName}. Stops and ETA start once it reaches the first stop.`;
            kind = "info";
        } else if (trackState === "OFF_ROUTE") {
            message = "Bus is away from the route line.";
            banner = `⚠ Bus is ${formatDistance(Number(location.distanceFromRouteMeters))} off the route. ETA is hidden until it rejoins the route.`;
        }
        setText("tracking-status-text", message);
        showBanner(banner, kind);
        setText("mapFooterStatus", `Live Bus ${busNum}: ${bus.latitude.toFixed(5)}, ${bus.longitude.toFixed(5)}`);
    } else if (status === "COMPLETED") {
        setText("mapFooterStatus", "Trip completed");
    }

    updateBusMarkerOnMap(bus, isStopped, displayBearing);

    const map = maps.live;
    if (map) {
        if (!state.firstFixShown) {
            state.firstFixShown = true;
            // Far from the route (e.g. testing from another city): show the bus itself, not a zoomed-out world.
            if (trackState === "NOT_STARTED" && Number(location.distanceFromRouteMeters) > 20000) {
                map.easeTo({ center: [bus.longitude, bus.latitude], zoom: 14, duration: 800 });
            } else {
                fitMapToCoordinates(map, [...(state.roadCoords.length ? state.roadCoords : state.path.map(p => [p.longitude, p.latitude])), [bus.longitude, bus.latitude]]);
            }
        } else if (state.autoFollowBus) {
            map.easeTo({ center: [bus.longitude, bus.latitude], duration: 700 });
        }
    }
}

/* WEBSOCKET + LIVE LOCATION SYNC */

async function fetchInitialTripLocation(tripId) {
    if (state.fetchInFlight) return;
    const token = state.sessionToken;
    state.fetchInFlight = true;

    try {
        const response = await fetch(`/api/trips/${encodeURIComponent(tripId)}/location`, { headers: { Accept: "application/json" } });
        if (token !== state.sessionToken) return;
        if (!response.ok) {
            const errData = await response.json().catch(() => null);
            throw new Error(errData?.message || `Could not fetch location (${response.status})`);
        }
        const location = await response.json();
        if (token !== state.sessionToken) return;

        state.lastLocation = location;
        updateLivePanel(location);
        if (location.tripStatus === "COMPLETED" || location.tripStatus === "CANCELLED") {
            disconnectStompWebSocket();
        }
    } catch (error) {
        if (token === state.sessionToken) setText("tracking-status-text", error.message);
    } finally {
        state.fetchInFlight = false;
    }
}

function connectStompWebSocket(tripId, token) {
    if (typeof window.WebSocket === "undefined") return;
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${protocol}//${window.location.host}/ws`;
    const subId = `sub-trip-${tripId}`;
    const topic = `/topic/trips/${tripId}`;

    let ws;
    try { ws = new WebSocket(wsUrl); } catch (_) { return; }

    const client = {
        ws,
        connected: false,
        closedByUser: false,
        sendFrame(command, headers = {}, body = "") {
            if (ws.readyState !== WebSocket.OPEN) return;
            const headerLines = Object.entries(headers).map(([k, v]) => `${k}:${v}`);
            ws.send(`${command}\n${headerLines.join("\n")}\n\n${body}\0`);
        },
        disconnect() {
            this.closedByUser = true;

            try {
                if (ws.readyState === WebSocket.OPEN ||
                    ws.readyState === WebSocket.CONNECTING) {
                    ws.close(1000, "Client disconnect");
                }
            } catch (_) {}
        }
    };
    state.stompClient = client;

    ws.onopen = () => {
        if (token !== state.sessionToken || client.closedByUser) { ws.close(); return; }
        client.sendFrame("CONNECT", { "accept-version": "1.2,1.1,1.0", "heart-beat": "10000,10000" });
    };

    ws.onmessage = event => {
        if (token !== state.sessionToken || client.closedByUser) return;
        const frames = String(event.data || "").split("\0");
        for (const frame of frames) {
            const trimmed = frame.replace(/^\n+/, "");
            if (!trimmed) continue;
            const splitIdx = trimmed.indexOf("\n\n");
            const headerPart = splitIdx >= 0 ? trimmed.slice(0, splitIdx) : trimmed;
            const bodyPart = splitIdx >= 0 ? trimmed.slice(splitIdx + 2) : "";
            const command = headerPart.split("\n")[0].trim();

            if (command === "CONNECTED") {
                client.connected = true;
                state.reconnectAttempt = 0;
                stopFallbackPolling();
                client.sendFrame("SUBSCRIBE", { id: subId, destination: topic });
            } else if (command === "MESSAGE" && bodyPart) {
                try {
                    const location = JSON.parse(bodyPart);
                    state.lastLocation = location;
                    updateLivePanel(location);
                    if (location.tripStatus === "COMPLETED" || location.tripStatus === "CANCELLED") {
                        disconnectStompWebSocket();
                    }
                } catch (_) {}
            }
        }
    };

    ws.onclose = () => {
        client.connected = false;
        if (token !== state.sessionToken || client.closedByUser) return;
        // Enable REST polling fallback only while STOMP is disconnected
        startFallbackPolling(tripId, token);
        const delayMs = Math.min(30000, 1000 * Math.pow(2, state.reconnectAttempt++));
        state.reconnectTimer = setTimeout(() => {
            if (token === state.sessionToken && state.activeTripId) connectStompWebSocket(tripId, token);
        }, delayMs);
    };
}

function startFallbackPolling(tripId, token) {
    if (state.liveSyncTimer !== null) return;
    state.liveSyncTimer = setInterval(() => {
        if (token !== state.sessionToken || !state.activeTripId) return;
        if (state.stompClient && state.stompClient.connected) {
            stopFallbackPolling();
            return;
        }
        fetchInitialTripLocation(tripId);
    }, LIVE_SYNC_INTERVAL_MS);
}

function stopFallbackPolling() {
    clearInterval(state.liveSyncTimer);
    state.liveSyncTimer = null;
}

function disconnectStompWebSocket() {
    clearTimeout(state.reconnectTimer);
    state.reconnectTimer = null;
    clearInterval(state.liveSyncTimer);
    state.liveSyncTimer = null;
    if (state.stompClient) {
        state.stompClient.disconnect();
        state.stompClient = null;
    }
    state.sessionToken++;
    state.fetchInFlight = false;
}

async function startLiveTracking(tripId) {
    disconnectStompWebSocket();
    const trip = state.trips.find(item => String(item.tripId) === String(tripId));
    if (!trip) return;

    state.activeTripId = tripId;
    state.activeTrip = trip;
    state.lastLocation = null;
    state.firstFixShown = false;
    state.reconnectAttempt = 0;
    state.path = [];
    state.roadCoords = [];
    state.stopRoadIndices = [];
    state.stopCumulativeMeters = [];
    state.totalRoadMeters = 0;
    state.fromIndex = -1;
    state.toIndex = -1;

    renderSearchResults();
    const token = ++state.sessionToken;

    const box = byId("live-tracking-box");
    box.classList.remove("hidden");
    resetLivePanel();

    setText("tracking-bus-title", `Bus ${trip.busNumber} — Route ${trip.routeCode} (${trip.fromStop} → ${trip.toStop})`);
    setText("tracking-status-text", "Loading road geometry and live GPS telemetry...");
    setText("eta-from-label", `Est. ETA to your boarding stop (${trip.fromStop})`);
    setText("eta-to-label", `Est. ETA to your destination (${trip.toStop})`);

    const map = ensureLiveMap();
    if (map) map.resize();
    box.scrollIntoView({ behavior: "smooth", block: "start" });

    try {
        const routeBundle = await loadRouteAndRoadGeometry(trip);
        if (token !== state.sessionToken) return;

        state.path = routeBundle.stops;
        state.roadCoords = routeBundle.roadCoords;
        state.stopRoadIndices = routeBundle.stopRoadIndices;
        state.routingSource = routeBundle.routingSource;
        state.stopCumulativeMeters = routeBundle.stopCumulativeMeters;
        state.totalRoadMeters = routeBundle.totalRoadMeters;
        setText("routing-engine-badge", state.routingSource);

        if (trip.fromStopId) state.fromIndex = state.path.findIndex(s => Number(s.stopId) === Number(trip.fromStopId));
        if (state.fromIndex < 0) state.fromIndex = state.path.findIndex(s => sameName(s.stopName, trip.fromStop));
        if (state.fromIndex < 0) state.fromIndex = 0;

        if (trip.toStopId) state.toIndex = state.path.findIndex((s, idx) => Number(s.stopId) === Number(trip.toStopId) && idx > state.fromIndex);
        if (state.toIndex < 0) state.toIndex = state.path.findIndex((s, idx) => sameName(s.stopName, trip.toStop) && idx > state.fromIndex);
        if (state.toIndex < 0) state.toIndex = state.path.length - 1;

        if (map) {
            drawInitialRoadRouteOnMap();
            map.resize();
        }
    } catch (error) {
        if (token === state.sessionToken) setText("tracking-status-text", `${error.message}. Showing raw GPS position.`);
    }

    await fetchInitialTripLocation(tripId);
    if (token !== state.sessionToken) return;

    const finished = state.lastLocation && (state.lastLocation.tripStatus === "COMPLETED" || state.lastLocation.tripStatus === "CANCELLED");
    if (!finished) {
        connectStompWebSocket(tripId, token);
    }
}

function stopLiveTracking() {
    disconnectStompWebSocket();
    state.activeTripId = null;
    state.activeTrip = null;
    state.lastLocation = null;

    resetLiveMapLayers();
    renderSearchResults();
    byId("live-tracking-box")?.classList.add("hidden");
    setText("mapFooterStatus", "Select From & To stops to track your bus");
}

/* INITIALIZATION */

document.addEventListener("DOMContentLoaded", () => {
    syncNavbarWithSession();
    initOverviewMap();
    loadStopsForSuggestions();

    setupStopAutocomplete("fromStop", "fromStopId", "fromStopSuggestions");
    setupStopAutocomplete("toStop", "toStopId", "toStopSuggestions");

    byId("swapStopsBtn")?.addEventListener("click", () => {
        const fromInput = byId("fromStop");
        const toInput = byId("toStop");
        const fromId = byId("fromStopId");
        const toId = byId("toStopId");
        [fromInput.value, toInput.value] = [toInput.value, fromInput.value];
        [fromId.value, toId.value] = [toId.value, fromId.value];
    });

    byId("busSearchForm")?.addEventListener("submit", searchBuses);
    byId("refresh-location-btn")?.addEventListener("click", () => {
        if (state.activeTripId) fetchInitialTripLocation(state.activeTripId);
    });

    byId("auto-follow-btn")?.addEventListener("click", event => {
        state.autoFollowBus = !state.autoFollowBus;
        const btn = event.currentTarget;
        btn.textContent = state.autoFollowBus ? "🎯 Auto-Follow: ON" : "🎯 Auto-Follow: OFF";
        btn.classList.toggle("active-toggle", state.autoFollowBus);
    });

    byId("fit-route-btn")?.addEventListener("click", () => {
        const coords = state.roadCoords.length > 1
            ? state.roadCoords
            : state.path.map(stop => [stop.longitude, stop.latitude]);

        if (maps.live && coords.length > 0) {
            fitMapToCoordinates(maps.live, coords);
        }
    });

    byId("close-tracking-btn")?.addEventListener("click", stopLiveTracking);

    window.addEventListener("beforeunload", disconnectStompWebSocket);

    document.addEventListener("click", event => {
        const button = event.target.closest("[data-track-trip-id]");
        if (button) startLiveTracking(button.dataset.trackTripId);
    });
});
