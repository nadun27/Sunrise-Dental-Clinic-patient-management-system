const activeSessionRows = document.getElementById(
    "activeSessionRows"
);

const treatmentRecordRows = document.getElementById(
    "treatmentRecordRows"
);

const sessionMessage = document.getElementById(
    "sessionMessage"
);

const recordMessage = document.getElementById(
    "recordMessage"
);

const completeSessionDialog = document.getElementById(
    "completeSessionDialog"
);

const completeSessionForm = document.getElementById(
    "completeSessionForm"
);

const recordDialog = document.getElementById(
    "recordDialog"
);

const completeSessionButton = document.getElementById(
    "completeSessionButton"
);

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
    const role = result.user.role;

    if (!["ADMIN", "DENTIST"].includes(role)) {
        throw new Error(
            "Only an administrator or dentist can access " +
            "clinical treatment records"
        );
    }
}

async function loadActiveSessions() {
    showMessage(
        sessionMessage,
        "Loading active treatment sessions...",
        "information"
    );

    try {
        const result = await apiRequest(
            "api/v1/treatment-records/active"
        );

        renderActiveSessions(result.appointments);

        showMessage(
            sessionMessage,
            `${result.count} active session(s) found`,
            "success"
        );

        openRequestedAppointment(result.appointments);

    } catch (error) {
        activeSessionRows.replaceChildren();

        showMessage(
            sessionMessage,
            error.message,
            "error"
        );
    }
}

function renderActiveSessions(appointments) {
    activeSessionRows.replaceChildren();

    if (appointments.length === 0) {
        appendEmptyRow(
            activeSessionRows,
            "No checked-in or active treatment sessions were found."
        );

        return;
    }

    appointments.forEach(appointment => {
        const row = document.createElement("tr");

        row.dataset.appointmentId =
            appointment.appointmentId.toString();

        row.appendChild(
            cell(
                `${appointment.appointmentNumber}\n` +
                formatDateTime(appointment.startAt)
            )
        );

        row.appendChild(
            cell(
                `${appointment.patientName}\n` +
                appointment.patientCode
            )
        );

        row.appendChild(cell(appointment.dentistName));
        row.appendChild(cell(appointment.treatmentName));

        const statusCell = document.createElement("td");
        const badge = document.createElement("span");

        badge.className =
            `status-badge status-${appointment.status.toLowerCase()}`;

        badge.textContent = formatStatus(appointment.status);

        statusCell.appendChild(badge);
        row.appendChild(statusCell);

        const actionCell = document.createElement("td");
        actionCell.className = "table-actions";

        if (appointment.status === "CHECKED_IN") {
            actionCell.appendChild(
                actionButton(
                    "Start Session",
                    () => startSession(appointment)
                )
            );
        }

        if (appointment.status === "IN_TREATMENT") {
            actionCell.appendChild(
                actionButton(
                    "Complete Session",
                    () => openCompleteDialog(appointment)
                )
            );
        }

        row.appendChild(actionCell);
        activeSessionRows.appendChild(row);
    });
}

async function startSession(appointment) {
    const confirmed = window.confirm(
        `Start treatment for ${appointment.patientName}?`
    );

    if (!confirmed) {
        return;
    }

    const body = new URLSearchParams({
        versionNumber: appointment.versionNumber.toString()
    });

    try {
        const result = await apiRequest(
            `api/v1/treatment-records/` +
            `${appointment.appointmentId}/start`,
            {
                method: "POST",
                headers: {
                    "Content-Type":
                        "application/x-www-form-urlencoded"
                },
                body
            }
        );

        await loadActiveSessions();

        showMessage(
            sessionMessage,
            result.message,
            "success"
        );

    } catch (error) {
        showMessage(
            sessionMessage,
            error.message,
            "error"
        );
    }
}

function openCompleteDialog(appointment) {
    completeSessionForm.reset();

    setValue(
        "completeAppointmentId",
        appointment.appointmentId
    );

    setValue(
        "completeVersionNumber",
        appointment.versionNumber
    );

    setText(
        "completeAppointmentNumber",
        appointment.appointmentNumber
    );

    setText(
        "completePatientName",
        `${appointment.patientName} (${appointment.patientCode})`
    );

    setText(
        "completeTreatmentName",
        appointment.treatmentName
    );

    setText(
        "completeDentistName",
        appointment.dentistName
    );

    completeSessionDialog.showModal();
}

completeSessionForm.addEventListener(
    "submit",
    async event => {
        event.preventDefault();

        if (!completeSessionForm.reportValidity()) {
            return;
        }

        if (!window.confirm(
            "Save this clinical record and complete the appointment?"
        )) {
            return;
        }

        completeSessionButton.disabled = true;

        const body = new URLSearchParams();

        for (
            const [name, value]
            of new FormData(completeSessionForm).entries()
            ) {
            body.append(name, value.toString());
        }

        try {
            const result = await apiRequest(
                "api/v1/treatment-records/complete",
                {
                    method: "POST",
                    headers: {
                        "Content-Type":
                            "application/x-www-form-urlencoded"
                    },
                    body
                }
            );

            completeSessionDialog.close();

            await Promise.all([
                loadActiveSessions(),
                loadTreatmentRecords()
            ]);

            showMessage(
                sessionMessage,
                result.message,
                "success"
            );

            showRecord(result.record);

        } catch (error) {
            showMessage(
                sessionMessage,
                error.message,
                "error"
            );

        } finally {
            completeSessionButton.disabled = false;
        }
    }
);

async function loadTreatmentRecords() {
    showMessage(
        recordMessage,
        "Loading treatment records...",
        "information"
    );

    const parameters = new URLSearchParams({
        search: document.getElementById(
            "recordSearch"
        ).value.trim()
    });

    try {
        const result = await apiRequest(
            `api/v1/treatment-records?${parameters}`
        );

        renderTreatmentRecords(result.records);

        showMessage(
            recordMessage,
            `${result.count} treatment record(s) found`,
            "success"
        );

    } catch (error) {
        treatmentRecordRows.replaceChildren();

        showMessage(
            recordMessage,
            error.message,
            "error"
        );
    }
}

function renderTreatmentRecords(records) {
    treatmentRecordRows.replaceChildren();

    if (records.length === 0) {
        appendEmptyRow(
            treatmentRecordRows,
            "No treatment records were found."
        );

        return;
    }

    records.forEach(record => {
        const row = document.createElement("tr");

        row.appendChild(cell(record.appointmentNumber));

        row.appendChild(
            cell(
                `${record.patientName}\n${record.patientCode}`
            )
        );

        row.appendChild(cell(record.treatmentName));
        row.appendChild(cell(record.diagnosis));

        row.appendChild(
            cell(formatDate(record.followUpDate))
        );

        const actionCell = document.createElement("td");
        actionCell.className = "table-actions";

        actionCell.appendChild(
            actionButton(
                "View Record",
                () => showRecord(record)
            )
        );

        row.appendChild(actionCell);
        treatmentRecordRows.appendChild(row);
    });
}

async function loadRecord(recordId) {
    try {
        const result = await apiRequest(
            `api/v1/treatment-records/${recordId}`
        );

        showRecord(result.record);

    } catch (error) {
        showMessage(
            recordMessage,
            error.message,
            "error"
        );
    }
}

function showRecord(record) {
    setText(
        "detailRecordAppointment",
        record.appointmentNumber
    );

    setText(
        "detailRecordPatient",
        `${record.patientName} (${record.patientCode})`
    );

    setText(
        "detailRecordDentist",
        record.dentistName
    );

    setText(
        "detailRecordTreatment",
        record.treatmentName
    );

    setText(
        "detailRecordDiagnosis",
        record.diagnosis
    );

    setText(
        "detailRecordPerformed",
        record.treatmentPerformed
    );

    setText(
        "detailRecordNotes",
        record.clinicalNotes
    );

    setText(
        "detailRecordPrescription",
        record.prescription
    );

    setText(
        "detailRecordFollowUp",
        formatDate(record.followUpDate)
    );

    setText(
        "detailRecordCreatedAt",
        formatDateTime(record.createdAt)
    );

    recordDialog.showModal();
}

function openRequestedAppointment(appointments) {
    const appointmentId = new URLSearchParams(
        window.location.search
    ).get("appointmentId");

    if (!appointmentId) {
        return;
    }

    const appointment = appointments.find(item =>
        item.appointmentId.toString() === appointmentId
    );

    if (appointment?.status === "IN_TREATMENT") {
        openCompleteDialog(appointment);
    }
}

function actionButton(label, action) {
    const button = document.createElement("button");

    button.type = "button";
    button.textContent = label;
    button.className = "small-button";
    button.addEventListener("click", action);

    return button;
}

function cell(value) {
    const tableCell = document.createElement("td");

    tableCell.textContent = value || "-";
    tableCell.style.whiteSpace = "pre-line";

    return tableCell;
}

function appendEmptyRow(container, message) {
    const row = document.createElement("tr");
    const tableCell = document.createElement("td");

    tableCell.colSpan = 6;
    tableCell.textContent = message;

    row.appendChild(tableCell);
    container.appendChild(row);
}

function setText(id, value) {
    document.getElementById(id).textContent = value || "-";
}

function setValue(id, value) {
    document.getElementById(id).value = value ?? "";
}

function showMessage(element, message, type) {
    element.textContent = message;
    element.className = `ui-message ${type}`;
}

function formatStatus(value) {
    return value
        .toLowerCase()
        .split("_")
        .map(word =>
            word.charAt(0).toUpperCase() + word.slice(1)
        )
        .join(" ");
}

function formatDate(value) {
    if (!value) {
        return "Not scheduled";
    }

    return new Intl.DateTimeFormat("en-LK", {
        dateStyle: "medium"
    }).format(new Date(`${value}T00:00:00`));
}

function formatDateTime(value) {
    if (!value) {
        return "-";
    }

    return new Intl.DateTimeFormat("en-LK", {
        dateStyle: "medium",
        timeStyle: "short"
    }).format(new Date(value));
}

const localToday = new Date();

localToday.setMinutes(
    localToday.getMinutes() -
    localToday.getTimezoneOffset()
);

document.getElementById("followUpDate").min =
    localToday.toISOString().slice(0, 10);

document.getElementById("refreshSessionsButton")
    .addEventListener("click", loadActiveSessions);

document.getElementById("recordSearchForm")
    .addEventListener("submit", event => {
        event.preventDefault();
        loadTreatmentRecords();
    });

document.getElementById("closeCompleteSessionDialog")
    .addEventListener(
        "click",
        () => completeSessionDialog.close()
    );

document.getElementById("cancelCompleteSessionButton")
    .addEventListener(
        "click",
        () => completeSessionDialog.close()
    );

document.getElementById("closeRecordDialog")
    .addEventListener(
        "click",
        () => recordDialog.close()
    );

loadSession()
    .then(() => Promise.all([
        loadActiveSessions(),
        loadTreatmentRecords()
    ]))
    .catch(error => {
        showMessage(
            sessionMessage,
            error.message,
            "error"
        );

        activeSessionRows.replaceChildren();
        treatmentRecordRows.replaceChildren();
    });
