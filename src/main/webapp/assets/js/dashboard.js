/* ==========================================================================
   Dashboard - greeting, date and role-filtered quick-access cards.
   The sidebar, profile block, sign-out and menu filtering are handled by
   shell.js, which must be loaded first.
   ========================================================================== */

const greeting = document.getElementById("greeting");
const subtitle = document.getElementById("subtitle");
const todayDate = document.getElementById("todayDate");

/* Same permission matrix the shell uses, applied to the cards. */
const CARD_ACCESS = {
    Patients: ["ADMIN", "RECEPTIONIST"],
    Appointments: ["ADMIN", "RECEPTIONIST"],
    Treatments: ["ADMIN", "DENTIST"],
    Billing: ["ADMIN", "CASHIER"],
    Reports: ["ADMIN"]
};

const ROLE_SUBTITLE = {
    ADMIN: "You have full access to every module in the clinic system.",
    RECEPTIONIST: "Register patients and manage the appointment diary.",
    DENTIST: "Review your sessions and record clinical treatment details.",
    CASHIER: "Generate invoices, record payments and issue receipts."
};

function greetingFor(hour) {
    if (hour < 12) {
        return "Good morning";
    }

    if (hour < 17) {
        return "Good afternoon";
    }

    return "Good evening";
}

function firstName(name) {
    return String(name || "")
        .replace(/\b(dr|mr|mrs|ms|prof)\.?\s+/gi, "")
        .trim()
        .split(/\s+/)[0] || "";
}

const now = new Date();

todayDate.textContent = now.toLocaleDateString(undefined, {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric"
});

window.clinicShell.session.then(user => {
    greeting.textContent =
        `${greetingFor(now.getHours())}, ${firstName(user.fullName)}`;

    subtitle.textContent =
        ROLE_SUBTITLE[user.role] || "Here is everything you have access to.";

    Object.entries(CARD_ACCESS).forEach(([key, allowedRoles]) => {
        const card = document.getElementById("card" + key);

        if (card) {
            card.hidden = !allowedRoles.includes(user.role);
        }
    });
});
