const fullName =
    document.getElementById("fullName");

const username =
    document.getElementById("username");

const role =
    document.getElementById("role");

const logoutButton =
    document.getElementById("logoutButton");

async function loadSession() {
    const response = await fetch(
        "api/v1/auth/session",
        {
            credentials: "same-origin"
        }
    );

    if (!response.ok) {
        window.location.replace("login.html");
        return;
    }

    const result = await response.json();

    fullName.textContent =
        result.user.fullName;

    username.textContent =
        result.user.username;

    role.textContent =
        result.user.role;

    const treatmentLink =
        document.getElementById(
            "treatmentLink"
        );

    const reportLink =
        document.getElementById(
            "reportLink"
        );

    if (treatmentLink) {
        treatmentLink.hidden = ![
            "ADMIN",
            "DENTIST"
        ].includes(result.user.role);
    }

    if (reportLink) {
        reportLink.hidden =
            result.user.role !== "ADMIN";
    }
}

logoutButton.addEventListener(
    "click",
    async () => {
        await fetch(
            "api/v1/auth/logout",
            {
                method: "POST",
                credentials: "same-origin"
            }
        );

        window.location.replace("login.html");
    }
);

loadSession().catch(() => {
    window.location.replace("login.html");
});