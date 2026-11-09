
const API = {
    // Tell the server who is asking (used for per-user data and admin checks).
    withUser(path) {
        const u = Session.get();
        if (u && u.id) {
            path += (path.includes("?") ? "&" : "?") + "userId=" + encodeURIComponent(u.id);
        }
        return path;
    },

    async post(path, data) {
        const body = new URLSearchParams(data || {});
        const res = await fetch(API.withUser(path), {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: body.toString(),
        });
        const json = await res.json().catch(() => ({}));
        return { ok: res.ok, status: res.status, data: json };
    },

    // Sends a file (Blob) as the raw request body, e.g. a vehicle photo.
    async upload(path, blob) {
        const res = await fetch(API.withUser(path), {
            method: "POST",
            headers: { "Content-Type": blob.type || "application/octet-stream" },
            body: blob,
        });
        const json = await res.json().catch(() => ({}));
        return { ok: res.ok, status: res.status, data: json };
    },

    async get(path) {
        const res = await fetch(API.withUser(path));
        const json = await res.json().catch(() => ({}));
        return { ok: res.ok, status: res.status, data: json };
    },
};

// ----- Session helpers (stored client-side only, for UI purposes) -----
const Session = {
    save(user) {
        localStorage.setItem("rentalUser", JSON.stringify(user));
    },
    get() {
        const raw = localStorage.getItem("rentalUser");
        return raw ? JSON.parse(raw) : null;
    },
    clear() {
        localStorage.removeItem("rentalUser");
    },
    requireLogin() {
        const u = Session.get();
        if (!u) {
            window.location.href = "index.html";
        }
        return u;
    },
};

// ----- Small UI helpers -----
function showAlert(containerId, message, type) {
    const el = document.getElementById(containerId);
    if (!el) return;
    el.textContent = message;
    el.className = "alert " + (type === "error" ? "alert-error" : "alert-success");
    el.classList.remove("hidden");
    window.scrollTo({ top: 0, behavior: "smooth" });
}

function hideAlert(containerId) {
    const el = document.getElementById(containerId);
    if (el) el.classList.add("hidden");
}

/** Escape user-supplied text before putting it into innerHTML. */
function esc(value) {
    return String(value ?? "").replace(/[&<>"']/g, c => (
        { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]
    ));
}

function money(n) {
    return "Rs." + Number(n || 0).toFixed(2);
}

function openModal(id) { document.getElementById(id).classList.remove("hidden"); }
function closeModal(id) { document.getElementById(id).classList.add("hidden"); }

/** Last day of a booking: start date + number of days. */
function endDate(startDate, days) {
    if (!startDate) return "";
    const d = new Date(startDate + "T00:00:00");
    d.setDate(d.getDate() + Number(days || 0));
    return d.toISOString().split("T")[0];
}
