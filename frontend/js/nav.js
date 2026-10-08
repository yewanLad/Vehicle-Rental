
const ADMIN_ONLY_PAGES = ["users.html", "maintenance.html"];

function renderNav(activePage) {
    const user = Session.requireLogin();
    if (!user) return; // requireLogin already redirected

    if (ADMIN_ONLY_PAGES.includes(activePage) && user.role !== "ADMIN") {
        window.location.href = "dashboard.html";
        return;
    }

    const links = [
        { page: "dashboard.html", label: "Dashboard" },
        { page: "vehicles.html", label: "Vehicles" },
        { page: "bookings.html", label: "Bookings" },
        { page: "payments.html", label: "Payments" },
        { page: "maintenance.html", label: "Maintenance", adminOnly: true },
        { page: "users.html", label: "Users", adminOnly: true },
        { page: "reports.html", label: "Reports" },
    ];

    const navHtml = links
        .filter(l => !l.adminOnly || user.role === "ADMIN")
        .map(l => `<a href="${l.page}" class="${l.page === activePage ? "active" : ""}">${l.label}</a>`)
        .join("");

    const shell = document.getElementById("app-shell");
    shell.insertAdjacentHTML("afterbegin", `
        <div class="sidebar">
            <div class="brand">Rental System</div>
            <div class="session"><strong>${user.username}</strong><br>${user.role}</div>
            <nav>${navHtml}</nav>
            <button class="logout" onclick="doLogout()">Log out</button>
        </div>
    `);
}

function doLogout() {
    Session.clear();
    window.location.href = "index.html";
}
