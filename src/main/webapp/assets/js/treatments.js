const activeSessions =
    document.getElementById("activeSessions");

const treatmentRecordRows =
    document.getElementById("treatmentRecordRows");

const sessionMessage =
    document.getElementById("sessionMessage");

const recordMessage =
    document.getElementById("recordMessage");

const completeSessionDialog =
    document.getElementById("completeSessionDialog");

const completeSessionForm =
    document.getElementById("completeSessionForm");

const completeSessionButton =
    document.getElementById("completeSessionButton");

const recordDialog =
    document.getElementById("recordDialog");

const recordSearch =
    document.getElementById("recordSearch");

const refreshSessionsButton =
    document.getElementById("refreshSessionsButton");

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
       controller enforces the same rule server side.
    */
    const user = await window.clinicShell.session;

    if (!["ADMIN", "DENTIST"].includes(user.role)) {
        throw new Error(
            "Only an administrator or dentist can access " +
            "clinical treatment records"
        );
    }
}

/* ============================================================ live queue = */

async function loadActiveSessions() {

    showMessage(
        sessionMessage,
        "Loading active sessions...",
        "information"
    );

    try {
        const result = await apiRequest(
            "api/v1/treatment-records/active"
        );

        const rendered = displayActiveSessions(
            result.appointments
        );

        /*
           An empty queue already explains itself in the panel, so the status
           line stays quiet rather than repeating it as "0 active session(s)".
           If the queue could not be drawn at all, its message stands.
        */
        if (rendered) {
            showMessage(
                sessionMessage,
                result.count === 0
                    ? ""
                    : `${result.count} active session(s)`,
                "success"
            );
        }

        openRequestedAppointment(result.appointments);

    } catch (error) {
        showEmptyQueue(
            "Active sessions could not be loaded."
        );

        showMessage(
            sessionMessage,
            error.message,
            "error"
        );
    }
}

function displayActiveSessions(appointments) {

    if (appointments.length === 0) {
        return showEmptyQueue(
            "There are no active sessions available.",
            "Patients appear here once reception has " +
            "checked them in."
        );
    }

    /* Built off-document so the queue is replaced in one step */
    const cards = document.createDocumentFragment();

    appointments.forEach(appointment => {
        cards.appendChild(
            createSessionCard(appointment)
        );
    });

    return setQueueContent(cards);
}

function createSessionCard(appointment) {

    const card = document.createElement("article");

    card.className = "session-card";
    card.dataset.status = appointment.status;

    const head = document.createElement("div");
    head.className = "session-card-head";

    const reference = document.createElement("div");

    reference.appendChild(
        createElement(
            "p",
            "session-number",
            appointment.appointmentNumber
        )
    );

    reference.appendChild(
        createElement(
            "p",
            "session-time",
            formatDateTime(appointment.startAt)
        )
    );

    head.appendChild(reference);

    head.appendChild(
        createElement(
            "span",
            `status-badge status-${appointment.status.toLowerCase()}`,
            formatStatus(appointment.status)
        )
    );

    card.appendChild(head);

    card.appendChild(
        createElement(
            "p",
            "session-patient",
            appointment.patientName
        )
    );

    card.appendChild(
        createElement(
            "p",
            "session-patient-code",
            appointment.patientCode
        )
    );

    const facts = document.createElement("dl");
    facts.className = "session-facts";

    facts.appendChild(
        createFact("Dentist", appointment.dentistName)
    );

    facts.appendChild(
        createFact("Treatment", appointment.treatmentName)
    );

    card.appendChild(facts);

    if (appointment.status === "CHECKED_IN") {
        card.appendChild(
            createCardButton(
                "Start Session",
                () => startSession(appointment)
            )
        );
    }

    if (appointment.status === "IN_TREATMENT") {
        card.appendChild(
            createCardButton(
                "Complete Session",
                () => openCompleteDialog(appointment)
            )
        );
    }

    return card;
}

function createFact(label, value) {

    const row = document.createElement("div");

    row.appendChild(
        createElement("dt", "", label)
    );

    row.appendChild(
        createElement("dd", "", value || "-")
    );

    return row;
}

function createCardButton(text, action) {

    const button = document.createElement("button");

    button.type = "button";
    button.className = "button";
    button.textContent = text;

    button.addEventListener("click", action);

    return button;
}

/*
   Every queue render goes through here. If the container is missing - an
   out-of-date copy of treatments.html left in the browser cache, for example -
   the page says so in the status line instead of failing on a null reference.
*/
function setQueueContent(content) {

    if (!activeSessions) {
        showMessage(
            sessionMessage,
            "The active sessions panel is missing from this " +
            "page. Reload to get the latest version.",
            "error"
        );

        return false;
    }

    activeSessions.replaceChildren(content);

    return true;
}

/* Returns false when the queue could not be drawn, so the caller leaves the
   explanation from setQueueContent in place instead of overwriting it. */
function showEmptyQueue(message, hint = "") {

    const empty = document.createElement("div");
    empty.className = "queue-empty";

    /* Static markup only - the text is set with textContent below. */
    empty.innerHTML = `
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="12" cy="12" r="9" stroke="currentColor"
                    stroke-width="1.7"/>
            <path d="M12 7v5.2l3.2 2" stroke="currentColor"
                  stroke-width="1.7" stroke-linecap="round"
                  stroke-linejoin="round"/>
        </svg>
        <p></p>`;

    empty.querySelector("p").textContent = message;

    if (hint) {
        empty.appendChild(
            createElement("p", "queue-empty-hint", hint)
        );
    }

    return setQueueContent(empty);
}

async function startSession(appointment) {

    const confirmed = window.confirm(
        `Start treatment for ${appointment.patientName}?`
    );

    if (!confirmed) {
        return;
    }

    const body = new URLSearchParams({
        versionNumber:
            appointment.versionNumber.toString()
    });

    try {
        const result = await apiRequest(
            "api/v1/treatment-records/" +
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

/* ====================================================== completion dialog = */

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
        `${appointment.patientName} ` +
        `(${appointment.patientCode})`
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

        const confirmed = window.confirm(
            "Save this clinical record and complete " +
            "the appointment?"
        );

        if (!confirmed) {
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

/* ====================================================== clinical history = */

async function loadTreatmentRecords() {

    showMessage(
        recordMessage,
        "Loading treatment records...",
        "information"
    );

    const parameters = new URLSearchParams({
        search: recordSearch.value.trim()
    });

    try {
        const result = await apiRequest(
            `api/v1/treatment-records?${parameters}`
        );

        displayTreatmentRecords(result.records);

        showMessage(
            recordMessage,
            `${result.count} treatment record(s) found`,
            "success"
        );

    } catch (error) {
        showMessage(
            recordMessage,
            error.message,
            "error"
        );

        setRecordRows(document.createDocumentFragment());
    }
}

/* The table counterpart of setQueueContent */
function setRecordRows(content) {

    if (!treatmentRecordRows) {
        showMessage(
            recordMessage,
            "The treatment records table is missing from " +
            "this page. Reload to get the latest version.",
            "error"
        );

        return;
    }

    treatmentRecordRows.replaceChildren(content);
}

function displayTreatmentRecords(records) {

    if (records.length === 0) {
        const row = document.createElement("tr");
        const tableCell = document.createElement("td");

        tableCell.colSpan = 6;
        tableCell.textContent =
            "No treatment records were found.";

        row.appendChild(tableCell);
        setRecordRows(row);

        return;
    }

    const rows = document.createDocumentFragment();

    records.forEach(record => {

        const row = document.createElement("tr");

        row.appendChild(
            createCell(record.appointmentNumber)
        );

        row.appendChild(
            createCell(
                `${record.patientName}\n` +
                `${record.patientCode}`,
                "stacked-cell"
            )
        );

        row.appendChild(
            createCell(
                record.treatmentName,
                "treatment-cell"
            )
        );

        row.appendChild(
            createDiagnosisCell(record.diagnosis)
        );

        row.appendChild(
            createCell(
                formatDate(record.followUpDate)
            )
        );

        const actionCell = document.createElement("td");
        actionCell.className = "table-actions";

        actionCell.appendChild(
            createActionButton(
                "View Record",
                () => showRecord(record)
            )
        );

        row.appendChild(actionCell);
        rows.appendChild(row);
    });

    setRecordRows(rows);
}

function createCell(value, className = "") {

    const cell = document.createElement("td");

    cell.textContent = value || "-";

    if (className) {
        cell.className = className;
    }

    return cell;
}

/* The span carries the two-line clamp; the cell itself must stay a table cell */
function createDiagnosisCell(diagnosis) {

    const cell = document.createElement("td");
    cell.className = "diagnosis-cell";

    cell.appendChild(
        createElement("span", "", diagnosis || "-")
    );

    cell.title = diagnosis || "";

    return cell;
}

function createActionButton(text, action) {

    const button = document.createElement("button");

    button.type = "button";
    button.className = "small-button";
    button.textContent = text;

    button.addEventListener("click", action);

    return button;
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

/*
   appointments.html links here as treatments.html?appointmentId=... so the
   dentist lands straight on the record form for that visit.
*/
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

/* ================================================================ helpers = */

function createElement(tag, className, text) {

    const element = document.createElement(tag);

    if (className) {
        element.className = className;
    }

    element.textContent = text;

    return element;
}

function setText(elementId, value) {

    document.getElementById(elementId)
        .textContent = value || "-";
}

function setValue(elementId, value) {

    document.getElementById(elementId)
        .value = value ?? "";
}

function showMessage(element, message, type) {

    /* Guarded so reporting a problem can never itself throw */
    if (!element) {
        return;
    }

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

/* ================================================================= events = */

refreshSessionsButton.addEventListener(
    "click",
    loadActiveSessions
);

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

/* A follow-up visit cannot be booked in the past */
const localToday = new Date();

localToday.setMinutes(
    localToday.getMinutes() -
    localToday.getTimezoneOffset()
);

document.getElementById("followUpDate").min =
    localToday.toISOString().slice(0, 10);

async function initializePage() {

    await loadSession();

    await Promise.all([
        loadActiveSessions(),
        loadTreatmentRecords()
    ]);
}

initializePage().catch(error => {

    showMessage(sessionMessage, error.message, "error");
    showMessage(recordMessage, error.message, "error");

    showEmptyQueue("Active sessions are unavailable.");
    setRecordRows(document.createDocumentFragment());
});
