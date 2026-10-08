
const API = {
    async post(path, data) {
        const body = new URLSearchParams(data || {});
        const res = await fetch(path, {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: body.toString(),
        });
        const json = await res.json().catch(() => ({}));
        return { ok: res.ok, status: res.status, data: json };
    },

    async get(path) {
        // Tell the server who is asking so it can return only their own data
        const u = Session.get();
        if (u && u.id) {
            path += (path.includes("?") ? "&" : "?") + "userId=" + encodeURIComponent(u.id);
        }
        const res = await fetch(path);
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

function showAlert(containerId, message, type) {
    const el = document.getElementById(containerId);
    if (!el) return;
    el.textContent = message;
    el.className = "alert " + (type === "error" ? "alert-error" : "alert-success");
    el.classList.remove("hidden");
}

function hideAlert(containerId) {
    const el = document.getElementById(containerId);
    if (el) el.classList.add("hidden");
}