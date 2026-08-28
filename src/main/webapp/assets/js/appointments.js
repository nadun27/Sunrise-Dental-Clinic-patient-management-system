const appointmentForm = document.getElementById("appointmentForm");
const appointmentRows = document.getElementById("appointmentRows");
const appointmentMessage = document.getElementById("appointmentMessage");
const appointmentDialog = document.getElementById("appointmentDialog");
const patientSelect = document.getElementById("patientId");
const dentistSelect = document.getElementById("dentistId");
const treatmentSelect = document.getElementById("treatmentTypeId");
const appointmentId = document.getElementById("appointmentId");
const versionNumber = document.getElementById("versionNumber");
const saveButton = document.getElementById("saveAppointmentButton");
const cancelEditButton = document.getElementById(
    "cancelAppointmentEditButton"
);
const formTitle = document.getElementById("appointmentFormTitle");

let treatments = [];
let canManageAppointments = false;
let canManageTreatments = false;

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
        throw new Error(result.message || "Request failed");
    }

    return result;
}

async function loadSession() {
    const result = await apiRequest("api/v1/auth/session");

    canManageAppointments =
        result.user.role === "ADMIN" ||
        result.user.role === "RECEPTIONIST";

    canManageTreatments =
        result.user.role === "ADMIN" ||
        result.user.role === "DENTIST";

    document.getElementById("appointmentFormPanel").hidden =
        !canManageAppointments;
}

async function loadOptions() {
    const [patientResult, optionResult] = await Promise.all([
        apiRequest("api/v1/patients?search=&includeInactive=false"),
        apiRequest("api/v1/appointments/options")
    ]);

    fillSelect(
        patientSelect,
        patientResult.patients,
        patient => patient.patientId,
        patient =>
            `${patient.patientCode} — ${patient.fullName} ` +
            `(${patient.contactNumber})`
    );

    fillSelect(
        dentistSelect,
        optionResult.dentists,
        dentist => dentist.dentistId,
        dentist =>
            `${dentist.fullName} — ` +
            `${dentist.specialization || "General Dentistry"}`
    );

    treatments = optionResult.treatments;

    fillSelect(
        treatmentSelect,
        treatments,
        treatment => treatment.treatmentTypeId,
        treatment =>
            `${treatment.treatmentName} — ` +
            `${treatment.durationMinutes} min`
    );
}

function fillSelect(select, items, valueFunction, textFunction) {
    const firstOption = select.options[0];
    select.replaceChildren(firstOption);

    items.forEach(item => {
        const option = document.createElement("option");

        option.value = valueFunction(item);
        option.textContent = textFunction(item);

        select.appendChild(option);
    });
}

async function loadAppointments() {
    showMessage("Loading appointments...", "information");

    const parameters = new URLSearchParams({
        search: document
            .getElementById("appointmentSearch")
            .value
            .trim(),

        date: document
            .getElementById("appointmentDate")
            .value,

        status: document
            .getElementById("appointmentStatus")
            .value
    });

    try {
        const result = await apiRequest(
            `api/v1/appointments?${parameters}`
        );

        renderAppointments(result.appointments);

        showMessage(
            `${result.count} appointment(s) found`,
            "success"
        );
    } catch (error) {
        appointmentRows.replaceChildren();
        showMessage(error.message, "error");
    }
}

function renderAppointments(appointments) {
    appointmentRows.replaceChildren();

    if (appointments.length === 0) {
        const row = document.createElement("tr");
        const tableCell = document.createElement("td");

        tableCell.colSpan = 6;
        tableCell.textContent = "No appointments were found.";

        row.appendChild(tableCell);
        appointmentRows.appendChild(row);

        return;
    }

    appointments.forEach(appointment => {
        const row = document.createElement("tr");

        row.appendChild(
            cell(appointment.appointmentNumber)
        );

        row.appendChild(
            cell(
                `${appointment.patientName}\n` +
                `${appointment.patientCode}`
            )
        );

        row.appendChild(
            cell(appointment.dentistName)
        );

        row.appendChild(
            cell(formatDateTime(appointment.startAt))
        );

        const statusCell = document.createElement("td");
        const badge = document.createElement("span");

        badge.className =
            `status-badge status-${appointment.status.toLowerCase()}`;

        badge.textContent = formatStatus(appointment.status);

        statusCell.appendChild(badge);
        row.appendChild(statusCell);

        const actions = document.createElement("td");
        actions.className = "table-actions";

        actions.appendChild(
            actionButton(
                "Details",
                () => showDetails(appointment.appointmentId)
            )
        );

        if (canManageAppointments) {
            if (
                ["SCHEDULED", "CONFIRMED"]
                    .includes(appointment.status)
            ) {
                actions.appendChild(
                    actionButton(
                        "Reschedule",
                        () => editAppointment(
                            appointment.appointmentId
                        )
                    )
                );
            }

        }

        addStatusButtons(actions, appointment);

        row.appendChild(actions);
        appointmentRows.appendChild(row);
    });
}

function addStatusButtons(container, appointment) {
    const administrativeTransitions = {
        SCHEDULED: [
            ["Confirm", "CONFIRMED"],
            ["Cancel", "CANCELLED"]
        ],

        CONFIRMED: [
            ["Check in", "CHECKED_IN"],
            ["No show", "NO_SHOW"],
            ["Cancel", "CANCELLED"]
        ],

        CHECKED_IN: [
            ["Cancel", "CANCELLED"]
        ]
    };

    if (canManageAppointments) {
        (administrativeTransitions[appointment.status] || [])
            .forEach(([label, status]) => {
                container.appendChild(
                    actionButton(
                        label,
                        () => changeStatus(appointment, status),
                        status === "CANCELLED"
                            ? "danger-button"
                            : ""
                    )
                );
            });
    }

    if (
        canManageTreatments &&
        appointment.status === "CHECKED_IN"
    ) {
        container.appendChild(
            actionButton(
                "Open Session",
                () => openTreatmentSession(appointment)
            )
        );
    }

    if (
        canManageTreatments &&
        appointment.status === "IN_TREATMENT"
    ) {
        container.appendChild(
            actionButton(
                "Complete Record",
                () => openTreatmentSession(appointment)
            )
        );
    }
}

function openTreatmentSession(appointment) {
    window.location.href =
        "treatments.html?appointmentId=" +
        encodeURIComponent(appointment.appointmentId);
}

function cell(value) {
    const element = document.createElement("td");

    element.textContent = value || "-";
    element.style.whiteSpace = "pre-line";

    return element;
}

function actionButton(label, action, className = "") {
    const button = document.createElement("button");

    button.type = "button";
    button.textContent = label;
    button.className =
        `small-button ${className}`.trim();

    button.addEventListener("click", action);

    return button;
}

async function getAppointment(id) {
    const result = await apiRequest(
        `api/v1/appointments/${id}`
    );

    return result.appointment;
}

async function showDetails(id) {
    try {
        const item = await getAppointment(id);

        setText(
            "detailAppointmentNumber",
            item.appointmentNumber
        );

        setText(
            "detailAppointmentPatient",
            `${item.patientName} (${item.patientCode})`
        );

        setText(
            "detailAppointmentContact",
            item.patientContact
        );

        setText(
            "detailAppointmentDentist",
            item.dentistName
        );

        setText(
            "detailAppointmentTreatment",
            `${item.treatmentName} ` +
            `(${item.durationMinutes} minutes)`
        );

        setText(
            "detailAppointmentStart",
            formatDateTime(item.startAt)
        );

        setText(
            "detailAppointmentEnd",
            formatDateTime(item.endAt)
        );

        setText(
            "detailAppointmentFees",
            `Consultation: ${currency(item.consultationFee)} | ` +
            `Treatment: ${currency(item.treatmentFee)}`
        );

        setText(
            "detailAppointmentStatus",
            formatStatus(item.status)
        );

        setText(
            "detailAppointmentReason",
            item.patientReason
        );

        setText(
            "detailAppointmentNotes",
            item.internalNotes
        );

        setText(
            "detailCancellationReason",
            item.cancellationReason
        );

        appointmentDialog.showModal();
    } catch (error) {
        showMessage(error.message, "error");
    }
}

async function editAppointment(id) {
    try {
        const item = await getAppointment(id);

        appointmentId.value = item.appointmentId;
        versionNumber.value = item.versionNumber;
        patientSelect.value = item.patientId;
        dentistSelect.value = item.dentistId;
        treatmentSelect.value = item.treatmentTypeId;

        document.getElementById("startAt").value =
            item.startAt.slice(0, 16);

        document.getElementById("patientReason").value =
            item.patientReason || "";

        document.getElementById("internalNotes").value =
            item.internalNotes || "";

        formTitle.textContent = "Reschedule Appointment";
        saveButton.textContent = "Save Appointment";
        cancelEditButton.hidden = false;

        updateTreatmentSummary();

        document
            .getElementById("appointmentFormPanel")
            .scrollIntoView({
                behavior: "smooth"
            });
    } catch (error) {
        showMessage(error.message, "error");
    }
}

appointmentForm.addEventListener(
    "submit",
    async event => {
        event.preventDefault();

        if (!appointmentForm.reportValidity()) {
            return;
        }

        saveButton.disabled = true;

        const body = new URLSearchParams();

        for (
            const [name, value]
            of new FormData(appointmentForm).entries()
            ) {
            body.append(name, value.toString());
        }

        const id = appointmentId.value;

        if (id) {
            body.set(
                "versionNumber",
                versionNumber.value
            );
        }

        const url = id
            ? `api/v1/appointments/${id}/update`
            : "api/v1/appointments/";

        try {
            const result = await apiRequest(url, {
                method: "POST",
                headers: {
                    "Content-Type":
                        "application/x-www-form-urlencoded"
                },
                body
            });

            resetForm();
            await loadAppointments();

            showMessage(result.message, "success");
        } catch (error) {
            showMessage(error.message, "error");
        } finally {
            saveButton.disabled = false;
        }
    }
);

async function changeStatus(appointment, status) {
    let reason = "";

    if (status === "CANCELLED") {
        reason =
            window.prompt(
                "Enter the cancellation reason:"
            ) || "";

        if (!reason.trim()) {
            return;
        }
    } else if (
        !window.confirm(
            `Change status to ${formatStatus(status)}?`
        )
    ) {
        return;
    }

    const body = new URLSearchParams({
        status,
        cancellationReason: reason,
        versionNumber:
            appointment.versionNumber.toString()
    });

    try {
        const result = await apiRequest(
            `api/v1/appointments/` +
            `${appointment.appointmentId}/status`,
            {
                method: "POST",
                headers: {
                    "Content-Type":
                        "application/x-www-form-urlencoded"
                },
                body
            }
        );

        await loadAppointments();
        showMessage(result.message, "success");
    } catch (error) {
        showMessage(error.message, "error");
    }
}

function resetForm() {
    appointmentForm.reset();

    appointmentId.value = "";
    versionNumber.value = "";

    formTitle.textContent = "Register Appointment";
    saveButton.textContent = "Register Appointment";
    cancelEditButton.hidden = true;

    updateTreatmentSummary();
}

function updateTreatmentSummary() {
    const treatment = treatments.find(
        item =>
            item.treatmentTypeId.toString() ===
            treatmentSelect.value
    );

    document.getElementById(
        "treatmentSummary"
    ).textContent = treatment
        ? `${treatment.durationMinutes} minutes | ` +
        `${currency(treatment.defaultFee)}`
        : "";
}

function setText(id, value) {
    document.getElementById(id).textContent =
        value || "Not provided";
}

function showMessage(message, type) {
    appointmentMessage.textContent = message;
    appointmentMessage.className =
        `ui-message ${type}`;
}

function formatDateTime(value) {
    return new Intl.DateTimeFormat("en-LK", {
        dateStyle: "medium",
        timeStyle: "short"
    }).format(new Date(value));
}

function formatStatus(value) {
    return value
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(
            /\b\w/g,
            letter => letter.toUpperCase()
        );
}

function currency(value) {
    return new Intl.NumberFormat("en-LK", {
        style: "currency",
        currency: "LKR"
    }).format(value);
}

function setMinimumDateTime() {
    const now = new Date();

    now.setMinutes(
        Math.ceil((now.getMinutes() + 1) / 15) * 15,
        0,
        0
    );

    const local = new Date(
        now.getTime() -
        now.getTimezoneOffset() * 60000
    )
        .toISOString()
        .slice(0, 16);

    document.getElementById("startAt").min = local;
}

document
    .getElementById("appointmentSearchForm")
    .addEventListener("submit", event => {
        event.preventDefault();
        loadAppointments();
    });

treatmentSelect.addEventListener(
    "change",
    updateTreatmentSummary
);

cancelEditButton.addEventListener(
    "click",
    resetForm
);

document
    .getElementById("closeAppointmentDialog")
    .addEventListener(
        "click",
        () => appointmentDialog.close()
    );

async function initialize() {
    setMinimumDateTime();
    await loadSession();
    await loadOptions();
    await loadAppointments();
}

initialize().catch(
    error => showMessage(error.message, "error")
);
