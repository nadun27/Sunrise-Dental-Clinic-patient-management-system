const staffForm =
    document.getElementById("staffForm");

const searchForm =
    document.getElementById("searchForm");

const staffRows =
    document.getElementById("staffRows");

const staffMessage =
    document.getElementById("staffMessage");

const searchInput =
    document.getElementById("searchInput");

const includeInactive =
    document.getElementById("includeInactive");

const roleSelect =
    document.getElementById("role");

const dentistFields =
    document.getElementById("dentistFields");

const registrationNumber =
    document.getElementById("registrationNumber");

const consultationFee =
    document.getElementById("consultationFee");

const password =
    document.getElementById("password");

const confirmPassword =
    document.getElementById("confirmPassword");

const saveStaffButton =
    document.getElementById("saveStaffButton");

async function apiRequest(url, options = {}) {
    const response = await fetch(url, {
        credentials: "same-origin",
        ...options
    });

    if (response.status === 401) {
        window.location.replace("login.html");
        throw new Error("Authentication required");
    }

    if (response.status === 403) {
        window.location.replace("dashboard.html");
        throw new Error("Administrator access is required");
    }

    const result = await response.json();

    if (!response.ok) {
        throw new Error(
            result.message || "Request failed"
        );
    }

    return result;
}

async function loadStaff() {
    showMessage("Loading staff members...", "information");

    const parameters = new URLSearchParams();

    parameters.set(
        "search",
        searchInput.value.trim()
    );

    parameters.set(
        "includeInactive",
        includeInactive.checked.toString()
    );

    try {
        const result = await apiRequest(
            "api/v1/staff?" + parameters.toString()
        );

        displayStaff(result.staffMembers);

        showMessage(
            result.count + " staff member(s) found",
            "success"
        );

    } catch (error) {
        staffRows.replaceChildren();
        showMessage(error.message, "error");
    }
}

function displayStaff(staffMembers) {
    staffRows.replaceChildren();

    if (staffMembers.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = 7;
        cell.textContent = "No staff members were found.";

        row.appendChild(cell);
        staffRows.appendChild(row);
        return;
    }

    staffMembers.forEach(staffMember => {
        const row = document.createElement("tr");

        row.appendChild(createCell(staffMember.fullName));
        row.appendChild(createCell(staffMember.username));
        row.appendChild(createCell(formatRole(staffMember.role)));
        row.appendChild(createCell(staffMember.email));
        row.appendChild(createCell(staffMember.contactNumber));
        row.appendChild(createCell(dentistSummary(staffMember)));

        const statusCell = document.createElement("td");
        const statusBadge = document.createElement("span");

        statusBadge.className = staffMember.active
            ? "status-badge active"
            : "status-badge inactive";

        statusBadge.textContent = staffMember.active
            ? "Active"
            : "Inactive";

        statusCell.appendChild(statusBadge);
        row.appendChild(statusCell);
        staffRows.appendChild(row);
    });
}

function createCell(value) {
    const cell = document.createElement("td");
    cell.textContent = value || "-";
    return cell;
}

function formatRole(role) {
    if (!role) {
        return "-";
    }

    return role.charAt(0) +
        role.slice(1).toLowerCase();
}

function dentistSummary(staffMember) {
    if (staffMember.role !== "DENTIST") {
        return "-";
    }

    const details = [staffMember.registrationNumber];

    if (staffMember.specialization) {
        details.push(staffMember.specialization);
    }

    if (staffMember.consultationFee !== null &&
            staffMember.consultationFee !== undefined) {

        const amount = Number(
            staffMember.consultationFee
        ).toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });

        details.push("LKR " + amount);
    }

    return details.filter(Boolean).join(" / ");
}

function updateDentistFields() {
    const isDentist = roleSelect.value === "DENTIST";

    dentistFields.hidden = !isDentist;
    registrationNumber.required = isDentist;
    consultationFee.required = isDentist;

    if (!isDentist) {
        registrationNumber.value = "";
        document.getElementById("specialization").value = "";
        consultationFee.value = "";
    }
}

function validatePasswordConfirmation() {
    confirmPassword.setCustomValidity(
        password.value === confirmPassword.value
            ? ""
            : "Passwords do not match"
    );
}

staffForm.addEventListener("submit", async event => {
    event.preventDefault();
    validatePasswordConfirmation();

    if (!staffForm.reportValidity()) {
        return;
    }

    saveStaffButton.disabled = true;

    const body = new URLSearchParams();

    for (const [name, value] of
        new FormData(staffForm).entries()) {

        body.append(name, value.toString());
    }

    try {
        const result = await apiRequest(
            "api/v1/staff/",
            {
                method: "POST",
                headers: {
                    "Content-Type":
                        "application/x-www-form-urlencoded"
                },
                body: body.toString()
            }
        );

        staffForm.reset();
        updateDentistFields();

        await loadStaff();
        showMessage(result.message, "success");

    } catch (error) {
        showMessage(error.message, "error");

    } finally {
        saveStaffButton.disabled = false;
    }
});

searchForm.addEventListener("submit", event => {
    event.preventDefault();
    loadStaff();
});

includeInactive.addEventListener("change", loadStaff);
roleSelect.addEventListener("change", updateDentistFields);

password.addEventListener(
    "input",
    validatePasswordConfirmation
);

confirmPassword.addEventListener(
    "input",
    validatePasswordConfirmation
);

function showMessage(message, type) {
    staffMessage.textContent = message;
    staffMessage.className = "ui-message " + type;
}

window.clinicShell.session.then(user => {
    if (user.role !== "ADMIN") {
        window.location.replace("dashboard.html");
        return;
    }

    updateDentistFields();
    loadStaff();
});
