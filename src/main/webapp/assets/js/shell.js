/* ==========================================================================
   Shared application shell.

   Renders the sidebar (brand, navigation, profile, sign-out) into
   <aside id="appSidebar"> on every signed-in page, so the markup lives in one
   place instead of being duplicated across the signed-in HTML pages.

   Page scripts should await window.clinicShell.session rather than calling
   api/v1/auth/session themselves:

       const user = await window.clinicShell.session;

   Load this script BEFORE the page's own script.
   ========================================================================== */

(function () {
    "use strict";

    /*
       Mirrors the role checks enforced by the controllers, so the menu never
       offers a page the server would refuse:
         PatientController / AppointmentController -> ADMIN, RECEPTIONIST
         BillingController / PaymentController     -> ADMIN, CASHIER
         TreatmentRecordController                 -> ADMIN, DENTIST
         ReportController                          -> ADMIN
         StaffController                           -> ADMIN
       Dashboard and Help are available to every signed-in user.
    */
    const NAV = [
        {
            href: "dashboard.html",
            label: "Dashboard",
            roles: null,
            icon: '<rect x="3" y="3" width="7.5" height="8.5" rx="2" stroke="currentColor" stroke-width="1.7"/><rect x="13.5" y="3" width="7.5" height="5" rx="2" stroke="currentColor" stroke-width="1.7"/><rect x="13.5" y="10.5" width="7.5" height="10.5" rx="2" stroke="currentColor" stroke-width="1.7"/><rect x="3" y="14" width="7.5" height="7" rx="2" stroke="currentColor" stroke-width="1.7"/>'
        },
        {
            href: "staff.html",
            label: "Staff",
            roles: ["ADMIN"],
            icon: '<circle cx="8" cy="8" r="3.2" stroke="currentColor" stroke-width="1.7"/><path d="M2.5 20c0-3.1 2.4-5 5.5-5s5.5 1.9 5.5 5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/><circle cx="17" cy="9" r="2.6" stroke="currentColor" stroke-width="1.7"/><path d="M15.5 15.5c3.3-.6 6 1.2 6 4.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>'
        },
        {
            href: "patients.html",
            label: "Patients",
            roles: ["ADMIN", "RECEPTIONIST"],
            icon: '<circle cx="9" cy="8" r="3.4" stroke="currentColor" stroke-width="1.7"/><path d="M3 20c0-3.2 2.7-5.2 6-5.2s6 2 6 5.2" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/><path d="M17 11h4M19 9v4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>'
        },
        {
            href: "appointments.html",
            label: "Appointments",
            roles: ["ADMIN", "RECEPTIONIST"],
            icon: '<rect x="3" y="5" width="18" height="16" rx="2.5" stroke="currentColor" stroke-width="1.7"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/><path d="m9 15 2 2 4-4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/>'
        },
        {
            href: "treatments.html",
            label: "Treatments",
            roles: ["ADMIN", "DENTIST"],
            icon: '<path d="M8 3v5a4 4 0 0 0 8 0V3" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/><path d="M12 12v4a4 4 0 0 1-8 0v-1" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/><circle cx="4" cy="13" r="1.9" stroke="currentColor" stroke-width="1.7"/>'
        },
        {
            href: "billing.html",
            label: "Billing",
            roles: ["ADMIN", "CASHIER"],
            icon: '<path d="M5 3h14v18l-2.3-1.6-2.4 1.6-2.3-1.6L9.7 21l-2.4-1.6L5 21V3Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><path d="M9 8h6M9 12h6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>'
        },
        {
            href: "reports.html",
            label: "Reports",
            roles: ["ADMIN"],
            icon: '<path d="M4 19V9m5 10V5m5 14v-7m5 7V8" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>'
        },
        {
            href: "help.html",
            label: "Help",
            roles: null,
            icon: '<circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.7"/><path d="M9.6 9.3a2.5 2.5 0 0 1 4.8.9c0 1.7-2.4 2.1-2.4 3.6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/><circle cx="12" cy="17" r="1" fill="currentColor"/>'
        }
    ];

    const TOOTH_PATH =
        "M12 3.6c-1.5-.9-3-1.3-4.3-1.3C5 2.3 3.2 4.2 3.2 7.2c0 2 .5 3.7 1 5.5.5 1.7.8 3.4 1 5.1.2 1.7.9 2.6 1.9 2.6 1.1 0 1.6-1 1.9-2.7l.5-3c.2-1.1.7-1.7 1.5-1.7s1.3.6 1.5 1.7l.5 3c.3 1.7.8 2.7 1.9 2.7 1 0 1.7-.9 1.9-2.6.2-1.7.5-3.4 1-5.1.5-1.8 1-3.5 1-5.5 0-3-1.8-4.9-4.5-4.9-1.3 0-2.8.4-4.3 1.3Z";

    function currentPage() {
        const last = window.location.pathname.split("/").pop();
        return last || "dashboard.html";
    }

    function initials(name) {
        const parts = String(name || "")
            .replace(/\b(dr|mr|mrs|ms|prof)\.?\s+/gi, "")
            .trim()
            .split(/\s+/)
            .filter(Boolean);

        if (parts.length === 0) {
            return "--";
        }

        if (parts.length === 1) {
            return parts[0].slice(0, 2).toUpperCase();
        }

        return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    }

    function render(sidebar) {
        const page = currentPage();

        const items = NAV.map(entry => {
            const active = entry.href === page;

            return `
                <li class="nav-item" data-roles="${entry.roles ? entry.roles.join(",") : ""}">
                    <a class="nav-link${active ? " is-active" : ""}"
                       href="${entry.href}"${active ? ' aria-current="page"' : ""}>
                        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">${entry.icon}</svg>
                        ${entry.label}
                    </a>
                </li>`;
        }).join("");

        /* Static markup only - user data is inserted with textContent below. */
        sidebar.innerHTML = `
            <a class="sidebar-brand" href="dashboard.html">
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="${TOOTH_PATH}" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
                </svg>
                <div>
                    <div class="brand-name">Sunrise Dental</div>
                    <div class="brand-sub">Clinic System</div>
                </div>
            </a>

            <nav class="sidebar-nav" aria-label="Main navigation">
                <p class="nav-heading">Menu</p>
                <ul>${items}</ul>
            </nav>

            <div class="sidebar-profile">
                <div class="profile-identity">
                    <div class="avatar" id="shellAvatar" aria-hidden="true">--</div>
                    <div class="profile-text">
                        <div class="profile-name" id="shellFullName">Loading...</div>
                        <div class="profile-username">@<span id="shellUsername">-</span></div>
                    </div>
                </div>

                <button id="shellLogout" class="logout-button" type="button">
                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                        <path d="M15 17v1.5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2V7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                        <path d="M10 12h11m0 0-3-3m3 3-3 3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    Sign out
                </button>
            </div>`;

        const logout = sidebar.querySelector("#shellLogout");

        logout.addEventListener("click", async () => {
            logout.disabled = true;

            try {
                await fetch("api/v1/auth/logout", {
                    method: "POST",
                    credentials: "same-origin"
                });
            } finally {
                window.location.replace("login.html");
            }
        });
    }

    function applyUser(sidebar, user) {
        sidebar.querySelector("#shellFullName").textContent = user.fullName;
        sidebar.querySelector("#shellUsername").textContent = user.username;
        sidebar.querySelector("#shellAvatar").textContent =
            initials(user.fullName);

        sidebar.querySelectorAll(".nav-item").forEach(item => {
            const roles = item.dataset.roles;
            item.hidden = roles !== "" && !roles.split(",").includes(user.role);
        });
    }

    async function start() {
        const sidebar = document.getElementById("appSidebar");

        if (sidebar) {
            render(sidebar);
        }

        const response = await fetch("api/v1/auth/session", {
            credentials: "same-origin"
        });

        if (!response.ok) {
            window.location.replace("login.html");
            throw new Error("Not authenticated");
        }

        const result = await response.json();

        if (sidebar) {
            applyUser(sidebar, result.user);
        }

        return result.user;
    }

    const session = start().catch(error => {
        window.location.replace("login.html");
        throw error;
    });

    window.clinicShell = { session: session, initials: initials };
}());
