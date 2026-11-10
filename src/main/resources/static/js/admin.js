"use strict";

/* SmartBus Admin Dashboard */

const API = {
    adminRegister: "/admin/register",
    me: "/api/me",
    drivers: "/api/drivers",
    buses: "/api/buses",
    stops: "/api/stops",
    adminStops: "/api/stops/admin",
    routes: "/api/route",
    trips: "/api/trips"
};

const state = {
    drivers: [],
    buses: [],
    stops: [],
    routes: [],
    trips: [],
    routeStopIds: []
};

/* COMMON HELPERS */

function byId(id) {
    return document.getElementById(id);
}

function valueOf(form, name) {
    return form.elements[name] ? form.elements[name].value.trim() : "";
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function getCsrfTokenFromCookie() {
    try {
        const match = document.cookie.split("; ").find(row => row.startsWith("XSRF-TOKEN="));
        return match ? decodeURIComponent(match.split("=")[1]) : "";
    } catch (_) {
        return "";
    }
}

async function apiRequest(url, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const csrfToken = getCsrfTokenFromCookie();

    const config = {
        method,
        credentials: "same-origin",
        headers: {
            Accept: "application/json",
            ...(options.body ? { "Content-Type": "application/json" } : {}),
            ...(csrfToken && method !== "GET" && method !== "HEAD" ? { "X-XSRF-TOKEN": csrfToken } : {}),
            ...(options.headers || {})
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
        throw new Error("Your session has expired. Redirecting to login...");
    }

    if (!response.ok) {
        if (response.status === 401) {
            window.location.href = "/login.html";
            throw new Error("Authentication required. Please login again.");
        }
        let message = `Request failed (${response.status}).`;
        if (typeof result === "string" && result.trim()) {
            message = result;
        } else if (result && typeof result === "object") {
            message = result.message || result.detail || result.error || message;
        }
        throw new Error(message);
    }

    return result;
}

async function getList(url) {
    const result = await apiRequest(url);
    if (Array.isArray(result)) return result;
    if (Array.isArray(result?.data)) return result.data;
    if (Array.isArray(result?.content)) return result.content;
    if (Array.isArray(result?.items)) return result.items;
    throw new Error(`Unexpected response from ${url}. Expected a list.`);
}

function notify(message, duration = 4000) {
    const box = byId("notification");
    if (!box) return;
    box.textContent = message;
    box.classList.remove("hidden");
    clearTimeout(notify.timer);
    notify.timer = setTimeout(() => box.classList.add("hidden"), duration);
}

function showTableMessage(tableId, message, colspan) {
    const table = byId(tableId);
    if (table) {
        table.innerHTML = `<tr><td colspan="${colspan}">${escapeHtml(message)}</td></tr>`;
    }
}

function statusBadge(status) {
    const value = String(status || "UNKNOWN").toUpperCase();
    const active = ["ACTIVE", "AVAILABLE", "ON_DUTY", "RUNNING"].includes(value);
    return `<span class="${active ? "status-active" : "status-inactive"}">${escapeHtml(value)}</span>`;
}

function formatDate(value) {
    if (!value) return "—";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? escapeHtml(value) : date.toLocaleString();
}

function formatRouteChain(routeStops) {
    if (!Array.isArray(routeStops) || routeStops.length === 0) return "No stops";
    const sorted = [...routeStops].sort((a, b) => Number(a.sequenceNo) - Number(b.sequenceNo));
    return sorted.map(s => s.stopName).join(" → ");
}

function showForm(containerId, html) {
    const container = byId(containerId);
    if (!container) return;
    container.innerHTML = html;
    container.classList.remove("hidden");
    container.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

function hideForm(containerId) {
    const container = byId(containerId);
    if (!container) return;
    container.classList.add("hidden");
    container.innerHTML = "";
}

/*  NAVIGATION */

const sectionTitles = {
    overview: "Dashboard Overview",
    drivers: "Driver Management",
    buses: "Bus Management",
    stops: "Stop Management",
    routes: "Route Management",
    trips: "Assign Driver & Bus / Schedule Trips",
    "running-trips": "Active Running Trips"
};

function openSection(name) {
    document.querySelectorAll(".content-section").forEach(s => s.classList.add("hidden"));
    document.querySelectorAll(".nav-item").forEach(b => b.classList.remove("active"));

    const section = byId(`${name}-section`);
    if (!section) return;
    section.classList.remove("hidden");
    document.querySelector(`.nav-item[data-section="${name}"]`)?.classList.add("active");
    byId("page-title").textContent = sectionTitles[name] || "SmartBus Admin";
    loadSectionData(name);
}

async function loadSectionData(name) {
    try {
        if (name === "overview") await loadOverview();
        else if (name === "drivers") await refreshDrivers();
        else if (name === "buses") await refreshBuses();
        else if (name === "stops") await refreshStops();
        else if (name === "routes") await refreshRoutes();
        else if (name === "trips") await refreshTrips();
        else if (name === "running-trips") await refreshRunningTrips();
    } catch (error) {
        console.error(error);
        notify(error.message);
    }
}

/* OVERVIEW */

async function loadOverview() {
    const resources = [
        ["drivers", API.drivers, "total-drivers"],
        ["buses", API.buses, "total-buses"],
        ["stops", API.adminStops, "total-stops"],
        ["routes", API.routes, "total-routes"],
        ["trips", API.trips, "total-trips"]
    ];

    const results = await Promise.allSettled(resources.map(item => getList(item[1])));
    results.forEach((result, index) => {
        const [key, , elementId] = resources[index];
        const el = byId(elementId);
        if (result.status === "fulfilled") {
            state[key] = result.value;
            if (el) el.textContent = result.value.length;
        } else if (el) {
            el.textContent = "--";
        }
    });

    if (Array.isArray(state.trips)) {
        const scheduled = state.trips.filter(t => t.tripStatus === "SCHEDULED").length;
        const running = state.trips.filter(t => t.tripStatus === "RUNNING" || t.tripStatus === "OVERDUE").length;
        const completed = state.trips.filter(t => t.tripStatus === "COMPLETED").length;
        if (byId("total-scheduled-trips")) byId("total-scheduled-trips").textContent = scheduled;
        if (byId("total-running-trips")) byId("total-running-trips").textContent = running;
        if (byId("total-completed-trips")) byId("total-completed-trips").textContent = completed;
    }
}

/* DRIVER MANAGEMENT */

async function refreshDrivers() {
    showTableMessage("drivers-table", "Loading drivers...", 8);
    try {
        state.drivers = await getList(API.drivers);
        renderDrivers();
    } catch (error) {
        showTableMessage("drivers-table", error.message, 8);
        throw error;
    }
}

function renderDrivers() {
    if (!state.drivers.length) {
        showTableMessage("drivers-table", "No drivers found.", 8);
        return;
    }
    byId("drivers-table").innerHTML = state.drivers.map(driver => `
        <tr>
            <td>${escapeHtml(driver.id)}</td>
            <td><strong>${escapeHtml(driver.driverName)}</strong></td>
            <td>${escapeHtml(driver.driverId)}</td>
            <td>${escapeHtml(driver.phoneNumber)}</td>
            <td>${escapeHtml(driver.licenseNumber)}</td>
            <td>${statusBadge(driver.accountStatus)}</td>
            <td>${statusBadge(driver.availabilityStatus)}</td>
            <td>
                <button class="action-btn" data-action="edit-driver" data-id="${escapeHtml(driver.id)}">Edit</button>
                <button class="secondary-btn" data-action="reset-driver-pin" data-id="${escapeHtml(driver.id)}">Reset PIN</button>
                ${driver.accountStatus === "ACTIVE" ? `<button class="secondary-btn" data-action="deactivate-driver" data-id="${escapeHtml(driver.id)}">Deactivate</button>` : ""}
            </td>
        </tr>
    `).join("");
}

function renderDriverForm(driver = null) {
    const editing = Boolean(driver);
    showForm("driver-form-container", `
        <h2>${editing ? "Update Driver" : "Add New Driver"}</h2>
        <form id="driver-form" data-id="${editing ? escapeHtml(driver.id) : ""}">
            <div class="form-grid">
                <div class="form-group">
                    <label>Driver Name</label>
                    <input name="driverName" type="text" placeholder="e.g. Ramesh Kumar" value="${editing ? escapeHtml(driver.driverName) : ""}" required>
                </div>
                ${editing ? "" : `
                <div class="form-group">
                    <label>Driver Email ID (Login Username)</label>
                    <input name="driverId" type="email" placeholder="e.g. ramesh@smartbus.in" required>
                </div>`}
                <div class="form-group">
                    <label>Phone Number (10 Digits)</label>
                    <input name="phoneNumber" type="tel" pattern="[0-9]{10}" maxlength="10" placeholder="e.g. 9876543210" value="${editing ? escapeHtml(driver.phoneNumber) : ""}" required>
                </div>
                ${editing ? `
                <div class="form-group">
                    <label>Account Status</label>
                    <select name="accountStatus" required>
                        <option value="ACTIVE" ${driver.accountStatus === "ACTIVE" ? "selected" : ""}>ACTIVE</option>
                        <option value="INACTIVE" ${driver.accountStatus === "INACTIVE" ? "selected" : ""}>INACTIVE</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Availability Status</label>
                    <select name="availabilityStatus" required>
                        <option value="AVAILABLE" ${driver.availabilityStatus === "AVAILABLE" ? "selected" : ""}>AVAILABLE</option>
                        <option value="UNAVAILABLE" ${driver.availabilityStatus === "UNAVAILABLE" ? "selected" : ""}>UNAVAILABLE</option>
                        <option value="ON_DUTY" ${driver.availabilityStatus === "ON_DUTY" ? "selected" : ""}>ON_DUTY</option>
                    </select>
                </div>` : `
                <div class="form-group">
                    <label>License Number</label>
                    <input name="licenseNumber" type="text" placeholder="e.g. JH15-2022-00981" required>
                </div>
                <div class="form-group">
                    <label>Login PIN (4 to 6 digits)</label>
                    <input name="pin" type="password" inputmode="numeric" pattern="[0-9]{4,6}" maxlength="6" placeholder="4 to 6 digit PIN" required>
                </div>`}
            </div>
            <div class="form-actions">
                <button type="submit" class="primary-btn">${editing ? "Update Driver" : "Create Driver"}</button>
                <button type="button" class="secondary-btn" data-action="cancel-form" data-target="driver-form-container">Cancel</button>
            </div>
        </form>
    `);
}

function renderDriverPinResetForm(driver) {
    showForm("driver-form-container", `
        <h2>Reset PIN for ${escapeHtml(driver.driverName)} (${escapeHtml(driver.driverId)})</h2>
        <form id="driver-pin-form" data-id="${escapeHtml(driver.id)}">
            <div class="form-grid">
                <div class="form-group">
                    <label>New Login PIN (4 to 6 digits)</label>
                    <input name="pin" type="password" inputmode="numeric" pattern="[0-9]{4,6}" maxlength="6" placeholder="Enter new 4 to 6 digit PIN" required>
                </div>
            </div>
            <div class="form-actions">
                <button type="submit" class="primary-btn">Save New PIN</button>
                <button type="button" class="secondary-btn" data-action="cancel-form" data-target="driver-form-container">Cancel</button>
            </div>
        </form>
    `);
}

function editDriver(id) {
    const driver = state.drivers.find(item => String(item.id) === String(id));
    if (!driver) throw new Error("Driver not found. Refresh the list.");
    renderDriverForm(driver);
}

function openResetDriverPin(id) {
    const driver = state.drivers.find(item => String(item.id) === String(id));
    if (!driver) throw new Error("Driver not found. Refresh the list.");
    renderDriverPinResetForm(driver);
}

async function submitDriverPinForm(form) {
    const id = form.dataset.id;
    const pin = valueOf(form, "pin");
    await apiRequest(`${API.drivers}/${id}/reset-pin`, { method: "PUT", body: { pin } });
    hideForm("driver-form-container");
    notify("Driver PIN reset successfully.");
}

async function deactivateDriverById(id) {
    await apiRequest(`${API.drivers}/${id}`, { method: "DELETE" });
    notify("Driver deactivated.");
    await refreshDrivers();
    await loadOverview();
}

async function submitDriverForm(form) {
    const id = form.dataset.id;
    const payload = {
        driverName: valueOf(form, "driverName"),
        phoneNumber: valueOf(form, "phoneNumber")
    };

    if (id) {
        payload.accountStatus = valueOf(form, "accountStatus");
        payload.availabilityStatus = valueOf(form, "availabilityStatus");
        await apiRequest(`${API.drivers}/${id}`, { method: "PUT", body: payload });
        notify("Driver updated successfully.");
    } else {
        payload.driverId = valueOf(form, "driverId");
        payload.licenseNumber = valueOf(form, "licenseNumber");
        payload.pin = valueOf(form, "pin");
        await apiRequest(API.drivers, { method: "POST", body: payload });
        notify("Driver created successfully.");
    }

    hideForm("driver-form-container");
    await refreshDrivers();
    await loadOverview();
}

/*  BUS MANAGEMENT  */

async function refreshBuses() {
    showTableMessage("buses-table", "Loading buses...", 5);
    try {
        state.buses = await getList(API.buses);
        renderBuses();
    } catch (error) {
        showTableMessage("buses-table", error.message, 5);
        throw error;
    }
}

function renderBuses() {
    if (!state.buses.length) {
        showTableMessage("buses-table", "No buses found.", 5);
        return;
    }
    byId("buses-table").innerHTML = state.buses.map(bus => `
        <tr>
            <td>${escapeHtml(bus.id)}</td>
            <td><strong>${escapeHtml(bus.busNumber)}</strong></td>
            <td>${escapeHtml(bus.registrationNumber)}</td>
            <td>${statusBadge(bus.status)}</td>
            <td>
                <button class="action-btn" data-action="edit-bus" data-id="${escapeHtml(bus.id)}">Edit</button>
                ${bus.status === "ACTIVE" ? `<button class="secondary-btn" data-action="deactivate-bus" data-id="${escapeHtml(bus.id)}">Deactivate</button>` : ""}
            </td>
        </tr>
    `).join("");
}

async function deactivateBusById(id) {
    await apiRequest(`${API.buses}/${id}`, { method: "DELETE" });
    notify("Bus deactivated.");
    await refreshBuses();
    await loadOverview();
}

function renderBusForm(bus = null) {
    const editing = Boolean(bus);
    showForm("bus-form-container", `
        <h2>${editing ? "Update Bus" : "Add New Bus"}</h2>
        <form id="bus-form" data-id="${editing ? escapeHtml(bus.id) : ""}">
            <div class="form-grid">
                <div class="form-group">
                    <label>Bus Number</label>
                    <input name="busNumber" type="text" placeholder="e.g. BUS-101" value="${editing ? escapeHtml(bus.busNumber) : ""}" required>
                </div>
                ${editing ? "" : `
                <div class="form-group">
                    <label>Registration Number</label>
                    <input name="registrationNumber" type="text" placeholder="e.g. JH15AB1234" required>
                </div>`}
                <div class="form-group">
                    <label>Bus Status</label>
                    <select name="status" required>
                        <option value="INACTIVE" ${(!editing || bus.status === "INACTIVE") ? "selected" : ""}>INACTIVE</option>
                        <option value="ACTIVE" ${(editing && bus.status === "ACTIVE") ? "selected" : ""}>ACTIVE</option>
                    </select>
                </div>
            </div>
            <div class="form-actions">
                <button type="submit" class="primary-btn">${editing ? "Update Bus" : "Create Bus"}</button>
                <button type="button" class="secondary-btn" data-action="cancel-form" data-target="bus-form-container">Cancel</button>
            </div>
        </form>
    `);
}

function editBus(id) {
    const bus = state.buses.find(item => String(item.id) === String(id));
    if (!bus) throw new Error("Bus not found. Refresh the list.");
    renderBusForm(bus);
}

async function submitBusForm(form) {
    const id = form.dataset.id;
    const payload = {
        busNumber: valueOf(form, "busNumber"),
        status: valueOf(form, "status")
    };

    if (id) {
        await apiRequest(`${API.buses}/${id}`, { method: "PUT", body: payload });
        notify("Bus updated successfully.");
    } else {
        payload.registrationNumber = valueOf(form, "registrationNumber");
        await apiRequest(API.buses, { method: "POST", body: payload });
        notify("Bus created successfully.");
    }

    hideForm("bus-form-container");
    await refreshBuses();
    await loadOverview();
}

/* STOP MANAGEMENT  */

async function refreshStops() {
    showTableMessage("stops-table", "Loading stops...", 6);
    try {
        state.stops = await getList(API.adminStops);
        renderStops();
    } catch (error) {
        showTableMessage("stops-table", error.message, 6);
        throw error;
    }
}

function renderStops() {
    if (!state.stops.length) {
        showTableMessage("stops-table", "No stops found.", 6);
        return;
    }

    byId("stops-table").innerHTML = state.stops.map(stop => `
        <tr>
            <td>${escapeHtml(stop.id)}</td>
            <td><strong>${escapeHtml(stop.stopName)}</strong></td>
            <td>${escapeHtml(stop.latitude)}</td>
            <td>${escapeHtml(stop.longitude)}</td>
            <td>${stop.active ? "ACTIVE" : "INACTIVE"}</td>
            <td>
                <button class="action-btn" data-action="edit-stop" data-id="${escapeHtml(stop.id)}">Edit</button>
                <button class="secondary-btn" data-action="toggle-stop-status" data-id="${escapeHtml(stop.id)}">
                    ${stop.active ? "Deactivate" : "Activate"}
                </button>
            </td>
        </tr>
    `).join("");
}

async function toggleStopStatusById(id) {
    const stop = state.stops.find(item => String(item.id) === String(id));

    if (!stop) throw new Error("Stop not found. Refresh the list.");

    const active = !stop.active;

    const payload = {
        stopName: stop.stopName,
        latitude: Number(stop.latitude),
        longitude: Number(stop.longitude),
        active: active
    };

    await apiRequest(`${API.stops}/${id}`, {
        method: "PUT",
        body: payload
    });

    notify(active ? "Stop activated." : "Stop deactivated.");

    await refreshStops();
    await loadOverview();
}

function renderStopForm(stop = null) {
    const editing = Boolean(stop);
    showForm("stop-form-container", `
        <h2>${editing ? "Update Stop" : "Add New Stop"}</h2>
        <form id="stop-form" data-id="${editing ? escapeHtml(stop.id) : ""}">
            <div class="form-grid">
                <div class="form-group">
                    <label>Stop Name</label>
                    <input name="stopName" type="text" placeholder="e.g. Tower Chowk" value="${editing ? escapeHtml(stop.stopName) : ""}" required>
                </div>
                <div class="form-group">
                    <label>Latitude</label>
                    <input name="latitude" type="number" min="-90" max="90" step="any" placeholder="e.g. 24.4851" value="${editing ? escapeHtml(stop.latitude) : ""}" required>
                </div>
                <div class="form-group">
                    <label>Longitude</label>
                    <input name="longitude" type="number" min="-180" max="180" step="any" placeholder="e.g. 86.6948" value="${editing ? escapeHtml(stop.longitude) : ""}" required>
                </div>
            </div>
            <div class="form-actions">
                <button type="submit" class="primary-btn">${editing ? "Update Stop" : "Create Stop"}</button>
                <button type="button" class="secondary-btn" data-action="cancel-form" data-target="stop-form-container">Cancel</button>
            </div>
        </form>
    `);
}

function editStop(id) {
    const stop = state.stops.find(item => String(item.id) === String(id));
    if (!stop) throw new Error("Stop not found. Refresh the list.");
    renderStopForm(stop);
}

async function submitStopForm(form) {
    const id = form.dataset.id;
    const payload = {
        stopName: valueOf(form, "stopName"),
        latitude: Number(valueOf(form, "latitude")),
        longitude: Number(valueOf(form, "longitude"))
    };

    if (id) {
        await apiRequest(`${API.stops}/${id}`, { method: "PUT", body: payload });
        notify("Stop updated successfully.");
    } else {
        await apiRequest(API.stops, { method: "POST", body: payload });
        notify("Stop created successfully.");
    }

    hideForm("stop-form-container");
    await refreshStops();
    await loadOverview();
}

/* ROUTE MANAGEMENT  */

async function refreshRoutes() {
    showTableMessage("routes-table", "Loading routes...", 5);
    try {
        state.routes = await getList(API.routes);
        renderRoutes();
    } catch (error) {
        showTableMessage("routes-table", error.message, 5);
        throw error;
    }
}

function renderRoutes() {
    if (!state.routes.length) {
        showTableMessage("routes-table", "No routes found.", 5);
        return;
    }

    byId("routes-table").innerHTML = state.routes.map(route => {
        const stopCount = Array.isArray(route.routeStops) ? route.routeStops.length : 0;
        const stopChain = formatRouteChain(route.routeStops);
        return `
        <tr>
            <td>${escapeHtml(route.id)}</td>
            <td><strong>${escapeHtml(route.routeCode)}</strong></td>
            <td>${statusBadge(route.routeStatus)}</td>
            <td style="white-space: normal; max-width: 460px;">
                <strong>${stopCount} stops:</strong> ${escapeHtml(stopChain)}
            </td>
            <td>
                <button class="action-btn" data-action="edit-route" data-id="${escapeHtml(route.id)}">Edit</button>
                <button class="secondary-btn" data-action="view-route" data-id="${escapeHtml(route.id)}">View</button>
                ${route.routeStatus === "AVAILABLE" ? `<button class="secondary-btn" data-action="deactivate-route" data-id="${escapeHtml(route.id)}">Deactivate</button>` : ""}
            </td>
        </tr>`;
    }).join("");
}

async function deactivateRouteById(id) {
    await apiRequest(`${API.routes}/${id}`, { method: "DELETE" });
    notify("Route deactivated.");
    await refreshRoutes();
    await loadOverview();
}

function renderRouteForm(route = null) {
    const editing = Boolean(route && route.id);
    state.routeStopIds = route && Array.isArray(route.routeStops)
        ? [...route.routeStops].sort((a, b) => a.sequenceNo - b.sequenceNo).map(stop => String(stop.stopId))
        : [];

    showForm("route-form-container", `
        <h2>${editing ? "Update Route" : "Create New Route"}</h2>
        <p style="margin-bottom: 12px; font-size: 13px; color: #64748b;">
            Add stops in travel order (Origin → Intermediate Stops → Destination). Each route has a unique route code; to create a return direction, add a new route with stops in that order.
        </p>
        <form id="route-form" data-id="${editing ? escapeHtml(route.id) : ""}">
            <div class="form-grid">
                <div class="form-group">
                    <label>Route Code</label>
                    <input name="routeCode" type="text" placeholder="e.g. R001 or R002" value="${route?.routeCode ? escapeHtml(route.routeCode) : ""}" required>
                </div>
                <div class="form-group">
                    <label>Route Status</label>
                    <select name="routeStatus" required>
                        <option value="AVAILABLE" ${(!route || route.routeStatus === "AVAILABLE") ? "selected" : ""}>AVAILABLE</option>
                        <option value="UNAVAILABLE" ${(route && route.routeStatus === "UNAVAILABLE") ? "selected" : ""}>UNAVAILABLE</option>
                    </select>
                </div>
                <div class="form-group" style="grid-column: 1 / -1;">
                    <label>Select Stops in Travel Sequence</label>
                    <div style="display: flex; gap: 10px; flex-wrap: wrap; align-items: center;">
                        <select id="route-stop-picker" aria-label="Select a stop" disabled style="flex: 1; min-width: 220px;">
                            <option value="">Loading stops...</option>
                        </select>
                        <button type="button" class="secondary-btn" data-action="add-route-stop" disabled>+ Add Stop to Sequence</button>
                    </div>
                    <ol id="selected-route-stops" style="margin-top: 12px; padding-left: 20px; display: grid; gap: 6px;"></ol>
                </div>
            </div>
            <div class="form-actions">
                <button type="submit" class="primary-btn">${editing ? "Update Route" : "Create Route"}</button>
                <button type="button" class="secondary-btn" data-action="cancel-form" data-target="route-form-container">Cancel</button>
            </div>
        </form>
    `);
}

function renderSelectedRouteStops() {
    const list = byId("selected-route-stops");
    if (!list) return;

    if (!state.routeStopIds.length) {
        list.innerHTML = `<li style="list-style: none; color: #64748b; font-size: 13px;">No stops added yet. Add at least 2 stops in travel order.</li>`;
        return;
    }

    list.innerHTML = state.routeStopIds.map((id, index) => {
        const stop = state.stops.find(item => String(item.id) === id);
        const roleTag = index === 0 ? " (Origin)" : index === state.routeStopIds.length - 1 ? " (Destination)" : "";
        return `
            <li style="display: flex; align-items: center; justify-content: space-between; background: #f8fafc; padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 6px;">
                <span>
                    <strong>#${index + 1}</strong> — ${escapeHtml(stop?.stopName || `Stop ID ${id}`)}
                    <small style="color: #64748b;">(ID: ${escapeHtml(id)})${escapeHtml(roleTag)}</small>
                </span>
                <button type="button" class="action-btn" data-action="remove-route-stop" data-index="${index}">Remove</button>
            </li>`;
    }).join("");
}

function addRouteStop() {
    const picker = byId("route-stop-picker");
    const id = picker?.value;
    if (!id) return;

    if (state.routeStopIds.length > 0 && state.routeStopIds[state.routeStopIds.length - 1] === String(id)) {
        notify("Route cannot contain the same stop twice in a row.");
        return;
    }

    state.routeStopIds.push(String(id));
    picker.value = "";
    renderSelectedRouteStops();
}

function removeRouteStop(index) {
    state.routeStopIds.splice(index, 1);
    renderSelectedRouteStops();
}

async function openRouteForm(route = null) {
    renderRouteForm(route);
    const picker = byId("route-stop-picker");
    const addButton = document.querySelector('[data-action="add-route-stop"]');

    try {
        state.stops = await getList(API.stops);
        picker.innerHTML = `<option value="">Select a stop</option>` + state.stops.map(stop => `
            <option value="${escapeHtml(stop.id)}">
                ${escapeHtml(stop.stopName)} (ID: ${escapeHtml(stop.id)} — ${Number(stop.latitude).toFixed(4)}, ${Number(stop.longitude).toFixed(4)})
            </option>
        `).join("");
        picker.disabled = state.stops.length === 0;
        addButton.disabled = state.stops.length === 0;
        renderSelectedRouteStops();
    } catch (error) {
        picker.innerHTML = `<option value="">Could not load stops</option>`;
        picker.disabled = true;
        addButton.disabled = true;
        notify(`Could not load stops: ${error.message}`);
    }
}

async function editRoute(id) {
    const route = await apiRequest(`${API.routes}/${id}`);
    await openRouteForm(route);
}

async function viewRoute(id) {
    const route = await apiRequest(`${API.routes}/${id}`);
    const stops = [...(route.routeStops || [])].sort((a, b) => a.sequenceNo - b.sequenceNo);
    const items = stops.map(stop => `
        <li style="margin-bottom: 6px;">
            <strong>Stop #${escapeHtml(stop.sequenceNo)}:</strong> ${escapeHtml(stop.stopName)}
            <small style="color: #64748b;">(ID: ${escapeHtml(stop.stopId)} · ${escapeHtml(stop.latitude)}, ${escapeHtml(stop.longitude)})</small>
        </li>
    `).join("");

    showForm("route-form-container", `
        <h2>Route Details: ${escapeHtml(route.routeCode)}</h2>
        <p style="margin-bottom: 12px;">Status: ${statusBadge(route.routeStatus)}</p>
        <h3 style="margin-bottom: 8px; font-size: 15px; color: #172554;">Ordered Stops (${stops.length})</h3>
        <ol style="padding-left: 20px; margin-bottom: 16px;">${items || "<li>No stops available.</li>"}</ol>
        <div class="form-actions">
            <button type="button" class="secondary-btn" data-action="cancel-form" data-target="route-form-container">Close</button>
        </div>
    `);
}

async function submitRouteForm(form) {
    if (state.routeStopIds.length < 2) {
        throw new Error("Add at least two stops (Origin and Destination) to the route.");
    }

    const rawCode = valueOf(form, "routeCode").toUpperCase();
    const id = form.dataset.id;
    const stopIdsNumeric = state.routeStopIds.map(Number);

    for (let i = 1; i < stopIdsNumeric.length; i++) {
        if (stopIdsNumeric[i] === stopIdsNumeric[i - 1]) {
            throw new Error("Route cannot contain the same stop twice in a row.");
        }
    }

    for (const existing of state.routes) {
        if (id && String(existing.id) === String(id)) continue;
        if (String(existing.routeCode || "").trim().toUpperCase() === rawCode) {
            throw new Error(`Route with code '${rawCode}' already exists.`);
        }
    }

    const payload = {
        routeCode: rawCode,
        routeStatus: valueOf(form, "routeStatus") || "AVAILABLE",
        stopIds: stopIdsNumeric
    };

    if (id) {
        await apiRequest(`${API.routes}/${id}`, { method: "PUT", body: payload });
        notify("Route updated successfully.");
    } else {
        await apiRequest(API.routes, { method: "POST", body: payload });
        notify("Route created successfully.");
    }

    hideForm("route-form-container");
    await refreshRoutes();
    await loadOverview();
}

/* TRIP MANAGEMENT */

function renderTripForm() {
    const now = new Date();
    const startDefault = new Date(now.getTime() + 5 * 60000);
    const endDefault = new Date(now.getTime() + 2 * 3600000);
    const pad = n => String(n).padStart(2, "0");
    const toLocalInput = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;

    showForm("trip-form-container", `
        <h2>Assign Driver &amp; Bus and Schedule Trip</h2>
        <form id="trip-form">
            <div class="form-grid">
                <div class="form-group">
                    <label>Assign Active Bus</label>
                    <select name="busId" id="trip-bus" required><option value="">Loading buses...</option></select>
                </div>
                <div class="form-group">
                    <label>Assign Driver</label>
                    <select name="driverId" id="trip-driver" required><option value="">Loading drivers...</option></select>
                </div>
                <div class="form-group" style="grid-column: 1 / -1;">
                    <label>Select Route</label>
                    <select name="routeId" id="trip-route" required><option value="">Loading routes...</option></select>
                </div>
                <div class="form-group">
                    <label>Scheduled Start Time</label>
                    <input type="datetime-local" name="scheduledStartTime" value="${toLocalInput(startDefault)}" required>
                </div>
                <div class="form-group">
                    <label>Scheduled End Time</label>
                    <input type="datetime-local" name="scheduledEndTime" value="${toLocalInput(endDefault)}" required>
                </div>
            </div>
            <div class="form-actions">
                <button type="submit" class="primary-btn">Assign &amp; Schedule Trip</button>
                <button type="button" class="secondary-btn" data-action="cancel-form" data-target="trip-form-container">Cancel</button>
            </div>
        </form>
    `);
}

function fillTripSelect(selectId, items, labelFn, errorMessage) {
    const select = byId(selectId);
    if (!select) return;
    if (errorMessage || !items.length) {
        select.innerHTML = `<option value="">${escapeHtml(errorMessage || "No records available")}</option>`;
        select.disabled = true;
        return;
    }
    select.innerHTML = `<option value="">Select an option</option>` + items.map(item => `
        <option value="${escapeHtml(item.id)}">${escapeHtml(labelFn(item))}</option>
    `).join("");
    select.disabled = false;
}

async function openTripForm() {
    renderTripForm();

    const [busesRes, driversRes, routesRes] = await Promise.allSettled([
        getList(API.buses),
        getList(API.drivers),
        getList(API.routes)
    ]);

    // BUS: Only ACTIVE buses for new trip assignment
    if (busesRes.status === "fulfilled") {
        state.buses = busesRes.value;

        const activeBuses = state.buses.filter(
            bus => String(bus.status || "").toUpperCase() === "ACTIVE"
        );

        fillTripSelect(
            "trip-bus",
            activeBuses,
            bus => `${bus.busNumber} (${bus.registrationNumber}) — ${bus.status}`
        );
    } else {
        fillTripSelect("trip-bus", [], null, "Could not load buses");
    }

    // DRIVER: Only ACTIVE and AVAILABLE drivers
    if (driversRes.status === "fulfilled") {
        state.drivers = driversRes.value;

        const availableDrivers = state.drivers.filter(driver =>
            String(driver.accountStatus || "").toUpperCase() === "ACTIVE" &&
            String(driver.availabilityStatus || "").toUpperCase() === "AVAILABLE"
        );

        fillTripSelect(
            "trip-driver",
            availableDrivers,
            driver => `${driver.driverName} (${driver.driverId}) — ${driver.availabilityStatus}`
        );
    } else {
        fillTripSelect("trip-driver", [], null, "Could not load drivers");
    }

    // ROUTE: Only AVAILABLE routes
    if (routesRes.status === "fulfilled") {
        state.routes = routesRes.value;

        const availableRoutes = state.routes.filter(
            route => String(route.routeStatus || "").toUpperCase() === "AVAILABLE"
        );

        fillTripSelect(
            "trip-route",
            availableRoutes,
            route => `${route.routeCode}: ${formatRouteChain(route.routeStops)} (ID: ${route.id})`
        );
    } else {
        fillTripSelect("trip-route", [], null, "Could not load routes");
    }
}

async function submitTripForm(form) {
    const startDate = new Date(valueOf(form, "scheduledStartTime"));
    const endDate = new Date(valueOf(form, "scheduledEndTime"));

    if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime())) {
        throw new Error("Please enter valid scheduled start and end times.");
    }
    if (endDate <= startDate) {
        throw new Error("Scheduled end time must be after start time.");
    }

    const payload = {
        busId: Number(valueOf(form, "busId")),
        driverId: Number(valueOf(form, "driverId")),
        routeId: Number(valueOf(form, "routeId")),
        scheduledStartTime: startDate.toISOString(),
        scheduledEndTime: endDate.toISOString()
    };

    await apiRequest(API.trips, { method: "POST", body: payload });
    notify("Driver & Bus assigned and trip scheduled successfully.");
    hideForm("trip-form-container");
    await refreshTrips();
    await loadOverview();
}

async function cancelTripById(id) {
    await apiRequest(`${API.trips}/${id}/cancel`, { method: "PUT" });
    notify("Scheduled trip cancelled.");
    await refreshTrips();
    await loadOverview();
}

async function forceCompleteTripById(id) {
    await apiRequest(`${API.trips}/${id}/force-complete`, { method: "PUT" });
    notify("Trip force-completed and driver released.");
    await refreshTrips();
    await loadOverview();
}

async function refreshTrips() {
    showTableMessage("trips-table", "Loading trips...", 9);
    try {
        state.trips = await getList(API.trips);
        renderTrips();
    } catch (error) {
        showTableMessage("trips-table", error.message, 9);
        throw error;
    }
}

function renderTrips() {
    const statusFilter = byId("trip-status-filter")?.value || "ALL";
    const filtered = statusFilter === "ALL" ? state.trips : state.trips.filter(t => t.tripStatus === statusFilter);

    if (!filtered.length) {
        showTableMessage("trips-table", "No trips found matching filter.", 9);
        return;
    }

    byId("trips-table").innerHTML = filtered.map(trip => {
        const busLabel = trip.busNumber ? `${trip.busNumber} (${trip.registrationNumber || ""})` : `Bus #${trip.busId}`;
        const driverLabel = trip.driverName ? `${trip.driverName} (${trip.driverEmail || ""})` : `Driver #${trip.driverId}`;
        const routeLabel = trip.routeCode
            ? `${trip.routeCode}${trip.originStop && trip.destinationStop ? `: ${trip.originStop} → ${trip.destinationStop}` : ""}`
            : `Route #${trip.routeId}`;

        let actions = "—";
        if (trip.tripStatus === "SCHEDULED") {
            actions = `<button class="secondary-btn" data-action="cancel-trip" data-id="${escapeHtml(trip.id)}">Cancel</button>`;
        } else if (trip.tripStatus === "RUNNING" || trip.tripStatus === "OVERDUE") {
            actions = `<button class="action-btn" data-action="force-complete-trip" data-id="${escapeHtml(trip.id)}">Force Complete</button>`;
        }

        return `
        <tr>
            <td>${escapeHtml(trip.id)}</td>
            <td><small>${escapeHtml(trip.tripId)}</small></td>
            <td><strong>${escapeHtml(busLabel)}</strong></td>
            <td>${escapeHtml(driverLabel)}</td>
            <td>${escapeHtml(routeLabel)}</td>
            <td>${statusBadge(trip.tripStatus)}</td>
            <td>${formatDate(trip.scheduledStartTime)}</td>
            <td>${formatDate(trip.scheduledEndTime)}</td>
            <td>${actions}</td>
        </tr>`;
    }).join("");
}

/* RUNNING & COMPLETED TRIPS */

async function refreshRunningTrips() {
    showTableMessage("running-trips-table", "Loading running trips...", 8);
    try {
        state.trips = await getList(API.trips);
        const running = state.trips.filter(t => t.tripStatus === "RUNNING" || t.tripStatus === "OVERDUE");
        if (!running.length) {
            showTableMessage("running-trips-table", "There are currently no running trips.", 8);
            return;
        }

        const results = await Promise.all(running.map(async trip => {
            try {
                const location = await apiRequest(`${API.trips}/${encodeURIComponent(trip.tripId)}/location`);
                return { trip, location, error: null };
            } catch (error) {
                return { trip, location: null, error: error.message };
            }
        }));

        byId("running-trips-table").innerHTML = results.map(({ trip, location, error }) => {
            const speedText = location && location.speedKmh !== null && location.speedKmh !== undefined
                ? `${Number(location.speedKmh).toFixed(1)} km/h`
                : "0.0 km/h";
            return `
            <tr>
                <td><small>${escapeHtml(trip.tripId)}</small></td>
                <td><strong>${escapeHtml(trip.busNumber || `Bus #${trip.busId}`)}</strong></td>
                <td>${escapeHtml(trip.driverName || `Driver #${trip.driverId}`)}</td>
                <td>${escapeHtml(trip.routeCode || `Route #${trip.routeId}`)}</td>
                <td>${escapeHtml(speedText)}</td>
                <td>${escapeHtml(location?.latitude ?? "Waiting for GPS")}</td>
                <td>${escapeHtml(location?.longitude ?? "Waiting for GPS")}</td>
                <td>
                    ${location?.locationUpdatedAt ? formatDate(location.locationUpdatedAt) : "—"}
                    ${error ? `<br><small>${escapeHtml(error)}</small>` : ""}
                </td>
            </tr>`;
        }).join("");
    } catch (error) {
        showTableMessage("running-trips-table", error.message, 8);
        throw error;
    }
}


/* ADMIN ACCOUNT & EVENTS  */

async function submitAdminAccountForm(form) {
    const adminId = valueOf(form, "adminId");
    const password = valueOf(form, "password");
    const oldPassword = valueOf(form, "oldPassword");
    const mode = valueOf(form, "mode") || "POST";

    if (mode === "PUT" && !oldPassword) {
        throw new Error("Current password is required to update an existing Admin password.");
    }
    await apiRequest(API.adminRegister, { method: mode, body: { adminId, password, oldPassword } });
    form.reset();
    notify(mode === "POST" ? `Admin ${adminId} registered successfully.` : `Password updated for Admin ${adminId}.`);
    if (mode === "PUT") await openSection("overview");
}

function handleDynamicClick(event) {
    const button = event.target.closest("[data-action]");
    if (!button) return;
    const id = button.dataset.id;

    try {
        switch (button.dataset.action) {
            case "edit-driver": editDriver(id); break;
            case "reset-driver-pin": openResetDriverPin(id); break;
            case "deactivate-driver": deactivateDriverById(id).catch(e => notify(e.message)); break;
            case "edit-bus": editBus(id); break;
            case "deactivate-bus": deactivateBusById(id).catch(e => notify(e.message)); break;
            case "edit-stop": editStop(id); break;
            case "toggle-stop-status": toggleStopStatusById(id).catch(e => notify(e.message)); break;
            case "edit-route": editRoute(id).catch(e => notify(e.message)); break;
            case "view-route": viewRoute(id).catch(e => notify(e.message)); break;
            case "deactivate-route": deactivateRouteById(id).catch(e => notify(e.message)); break;
            case "cancel-trip": cancelTripById(id).catch(e => notify(e.message)); break;
            case "force-complete-trip": forceCompleteTripById(id).catch(e => notify(e.message)); break;
            case "add-route-stop": addRouteStop(); break;
            case "remove-route-stop": removeRouteStop(Number(button.dataset.index)); break;
            case "cancel-form": hideForm(button.dataset.target); break;
        }
    } catch (error) {
        console.error(error);
        notify(error.message);
    }
}

async function handleFormSubmit(event) {
    const form = event.target;
    if (!(form instanceof HTMLFormElement) || !form.id.endsWith("-form")) return;
    event.preventDefault();

    const button = form.querySelector('button[type="submit"]');
    const originalText = button?.textContent;
    if (button) {
        button.disabled = true;
        button.textContent = "Please wait...";
    }

    try {
        if (form.id === "driver-form") await submitDriverForm(form);
        else if (form.id === "driver-pin-form") await submitDriverPinForm(form);
        else if (form.id === "bus-form") await submitBusForm(form);
        else if (form.id === "stop-form") await submitStopForm(form);
        else if (form.id === "route-form") await submitRouteForm(form);
        else if (form.id === "trip-form") await submitTripForm(form);
        else if (form.id === "admin-account-form") await submitAdminAccountForm(form);
    } catch (error) {
        console.error(error);
        notify(error.message, 6500);
    } finally {
        if (button && button.isConnected) {
            button.disabled = false;
            button.textContent = originalText;
        }
    }
}

function setupEvents() {
    document.querySelectorAll(".nav-item").forEach(btn => {
        btn.addEventListener("click", () => openSection(btn.dataset.section));
    });

    byId("admin-logout-link")?.addEventListener("click", async event => {
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

    byId("show-driver-form")?.addEventListener("click", () => renderDriverForm());
    byId("refresh-drivers")?.addEventListener("click", () => refreshDrivers().catch(e => notify(e.message)));
    byId("show-bus-form")?.addEventListener("click", () => renderBusForm());
    byId("refresh-buses")?.addEventListener("click", () => refreshBuses().catch(e => notify(e.message)));
    byId("show-stop-form")?.addEventListener("click", () => renderStopForm());
    byId("refresh-stops")?.addEventListener("click", () => refreshStops().catch(e => notify(e.message)));
    byId("show-route-form")?.addEventListener("click", () => openRouteForm());
    byId("refresh-routes")?.addEventListener("click", () => refreshRoutes().catch(e => notify(e.message)));
    byId("show-trip-form")?.addEventListener("click", () => openTripForm());
    byId("refresh-trips")?.addEventListener("click", () => refreshTrips().catch(e => notify(e.message)));
    byId("trip-status-filter")?.addEventListener("change", () => renderTrips());
    byId("refresh-running-trips")?.addEventListener("click", () => refreshRunningTrips().catch(e => notify(e.message)));

    document.addEventListener("click", handleDynamicClick);
    document.addEventListener("submit", handleFormSubmit);
}

document.addEventListener("DOMContentLoaded", async () => {
    setupEvents();
    try {
        const me = await apiRequest(API.me);
        if (me && me.mustChangePassword) {
            const form = byId("admin-account-form");
            if (form) {
                if (form.elements.adminId) form.elements.adminId.value = me.username || "";
                if (form.elements.mode) form.elements.mode.value = "PUT";
                form.scrollIntoView({ behavior: "smooth", block: "center" });
            }
            notify("Please update your initial Admin password below before performing administrative actions.", 8000);
            return;
        }
    } catch (_) {}
    await openSection("overview");
});
