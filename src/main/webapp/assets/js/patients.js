const patientForm =
    document.getElementById("patientForm");

const searchForm =
    document.getElementById("searchForm");

const patientRows =
    document.getElementById("patientRows");

const patientMessage =
    document.getElementById("patientMessage");

const patientDialog =
    document.getElementById("patientDialog");

const formPanel =
    document.getElementById("formPanel");

const patientIdInput =
    document.getElementById("patientId");

const includeInactive =
    document.getElementById("includeInactive");

const searchInput =
    document.getElementById("searchInput");

const cancelEditButton =
    document.getElementById("cancelEditButton");

const savePatientButton =
    document.getElementById("savePatientButton");

const formTitle =
    document.getElementById("formTitle");

let canManagePatients = false;

async function apiRequest(url, options = {}) {

    const response = await fetch(url, {
        credentials: "same-origin",
        ...options
    });

    if (response.status === 401) {
        window.location.replace("login.html");
        throw new Error("Authentication required");
    }

    const result = await response.json();

    if (!response.ok) {
        throw new Error(
            result.message || "Request failed"
        );
    }

    return result;
}

async function loadSession() {

    /*
       shell.js has already fetched the session to build the sidebar, so reuse
       its promise instead of calling api/v1/auth/session a second time. The
       signed-in user is shown in the sidebar profile block, not on this page.
    */
    const user = await window.clinicShell.session;

    canManagePatients =
        user.role === "ADMIN" ||
        user.role === "RECEPTIONIST";

    formPanel.hidden = !canManagePatients;
}

async function loadPatients() {

    showMessage("Loading patients...", "information");

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
            `api/v1/patients?${parameters}`
        );

        displayPatients(result.patients);

        showMessage(
            `${result.count} patient(s) found`,
            "success"
        );

    } catch (error) {
        patientRows.replaceChildren();

        showMessage(error.message, "error");
    }
}

function displayPatients(patients) {

    patientRows.replaceChildren();

    if (patients.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = 5;
        cell.textContent = "No patients were found.";

        row.appendChild(cell);
        patientRows.appendChild(row);
        return;
    }

    patients.forEach(patient => {

        const row = document.createElement("tr");

        row.appendChild(
            createCell(patient.patientCode)
        );

        row.appendChild(
            createCell(patient.fullName)
        );

        row.appendChild(
            createCell(patient.contactNumber)
        );

        const statusCell =
            document.createElement("td");

        const statusBadge =
            document.createElement("span");

        statusBadge.className =
            patient.active
                ? "status-badge active"
                : "status-badge inactive";

        statusBadge.textContent =
            patient.active ? "Active" : "Inactive";

        statusCell.appendChild(statusBadge);
        row.appendChild(statusCell);

        const actionCell =
            document.createElement("td");

        actionCell.className = "table-actions";

        actionCell.appendChild(
            createActionButton(
                "Details",
                () => openPatientDetails(
                    patient.patientId
                )
            )
        );

        if (canManagePatients) {
            actionCell.appendChild(
                createActionButton(
                    "Edit",
                    () => editPatient(
                        patient.patientId
                    )
                )
            );

            actionCell.appendChild(
                createActionButton(
                    patient.active
                        ? "Deactivate"
                        : "Reactivate",
                    () => changePatientStatus(
                        patient.patientId,
                        !patient.active
                    ),
                    patient.active
                        ? "danger-button"
                        : "success-button"
                )
            );
        }

        row.appendChild(actionCell);
        patientRows.appendChild(row);
    });
}

function createCell(value) {

    const cell = document.createElement("td");
    cell.textContent = value || "-";

    return cell;
}

function createActionButton(
    text,
    action,
    className = ""
) {
    const button = document.createElement("button");

    button.type = "button";
    button.textContent = text;
    button.className =
        `small-button ${className}`.trim();

    button.addEventListener("click", action);

    return button;
}

async function getPatient(patientId) {

    const result = await apiRequest(
        `api/v1/patients/${patientId}`
    );

    return result.patient;
}

async function openPatientDetails(patientId) {

    try {
        const patient = await getPatient(patientId);

        setText("detailName", patient.fullName);
        setText("detailCode", patient.patientCode);
        setText("detailContact", patient.contactNumber);
        setText("detailEmail", patient.email);
        setText("detailAddress", patient.address);
        setText(
            "detailDateOfBirth",
            patient.dateOfBirth
        );

        setText(
            "detailGender",
            formatGender(patient.gender)
        );

        setText(
            "detailAllergies",
            patient.allergies
        );

        setText(
            "detailMedicalNotes",
            patient.medicalNotes
        );

        setText(
            "detailStatus",
            patient.active ? "Active" : "Inactive"
        );

        patientDialog.showModal();

    } catch (error) {
        showMessage(error.message, "error");
    }
}

async function editPatient(patientId) {

    try {
        const patient = await getPatient(patientId);

        patientIdInput.value = patient.patientId;

        setInputValue("fullName", patient.fullName);
        setInputValue("address", patient.address);

        setInputValue(
            "contactNumber",
            patient.contactNumber
        );

        setInputValue("email", patient.email);

        setInputValue(
            "dateOfBirth",
            patient.dateOfBirth
        );

        setInputValue("gender", patient.gender);
        setInputValue("allergies", patient.allergies);

        setInputValue(
            "medicalNotes",
            patient.medicalNotes
        );

        formTitle.textContent = "Update Patient";
        savePatientButton.textContent = "Save Changes";
        cancelEditButton.hidden = false;

        formPanel.scrollIntoView({
            behavior: "smooth",
            block: "start"
        });

    } catch (error) {
        showMessage(error.message, "error");
    }
}

patientForm.addEventListener(
    "submit",
    async event => {

        event.preventDefault();

        if (!patientForm.reportValidity()) {
            return;
        }

        savePatientButton.disabled = true;

        const patientId = patientIdInput.value;

        const url = patientId
            ? `api/v1/patients/${patientId}/update`
            : "api/v1/patients/";

        const body = new URLSearchParams();

        for (
            const [name, value]
            of new FormData(patientForm).entries()
            ) {
            body.append(name, value.toString());
        }

        try {
            const result = await apiRequest(url, {
                method: "POST",

                headers: {
                    "Content-Type":
                        "application/x-www-form-urlencoded"
                },

                body
            });

            showMessage(result.message, "success");

            resetPatientForm();
            await loadPatients();

        } catch (error) {
            showMessage(error.message, "error");

        } finally {
            savePatientButton.disabled = false;
        }
    }
);

async function changePatientStatus(
    patientId,
    active
) {
    const action =
        active ? "reactivate" : "deactivate";

    const confirmed = window.confirm(
        `Are you sure you want to ${action} this patient?`
    );

    if (!confirmed) {
        return;
    }

    const body = new URLSearchParams();
    body.set("active", active.toString());

    try {
        const result = await apiRequest(
            `api/v1/patients/${patientId}/status`,
            {
                method: "POST",

                headers: {
                    "Content-Type":
                        "application/x-www-form-urlencoded"
                },

                body
            }
        );

        showMessage(result.message, "success");
        await loadPatients();

    } catch (error) {
        showMessage(error.message, "error");
    }
}

function resetPatientForm() {

    patientForm.reset();
    patientIdInput.value = "";

    formTitle.textContent = "Register Patient";

    savePatientButton.textContent =
        "Register Patient";

    cancelEditButton.hidden = true;
}

function showMessage(message, type) {

    patientMessage.textContent = message;
    patientMessage.className =
        `ui-message ${type}`;
}

function setText(elementId, value) {

    document.getElementById(elementId)
        .textContent = value || "Not provided";
}

function setInputValue(elementId, value) {

    document.getElementById(elementId)
        .value = value || "";
}

function formatGender(gender) {

    if (!gender) {
        return null;
    }

    return gender
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(/\b\w/g, letter =>
            letter.toUpperCase()
        );
}

searchForm.addEventListener(
    "submit",
    event => {
        event.preventDefault();
        loadPatients();
    }
);

includeInactive.addEventListener(
    "change",
    loadPatients
);

cancelEditButton.addEventListener(
    "click",
    resetPatientForm
);

document.getElementById("closeDialogButton")
    .addEventListener(
        "click",
        () => patientDialog.close()
    );

document.getElementById("dateOfBirth").max =
    new Date().toISOString().split("T")[0];

async function initializePage() {

    await loadSession();
    await loadPatients();
}

initializePage().catch(error => {
    showMessage(error.message, "error");
});