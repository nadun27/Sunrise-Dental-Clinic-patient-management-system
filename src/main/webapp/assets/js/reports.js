const reportForm =
    document.getElementById("reportForm");

const fromDateInput =
    document.getElementById("fromDate");

const toDateInput =
    document.getElementById("toDate");

const generateReportButton =
    document.getElementById("generateReportButton");

const printReportButton =
    document.getElementById("printReportButton");

const reportMessage =
    document.getElementById("reportMessage");

const reportPeriod =
    document.getElementById("reportPeriod");

const reportGeneratedAt =
    document.getElementById("reportGeneratedAt");

const appointmentStatusRows =
    document.getElementById("appointmentStatusRows");

const treatmentRows =
    document.getElementById("treatmentRows");

const dentistRows =
    document.getElementById("dentistRows");

const currencyFormatter =
    new Intl.NumberFormat(
        "en-LK",
        {
            style: "currency",
            currency: "LKR",
            minimumFractionDigits: 2
        }
    );

function formatInputDate(date) {
    const year = date.getFullYear();

    const month =
        String(date.getMonth() + 1)
            .padStart(2, "0");

    const day =
        String(date.getDate())
            .padStart(2, "0");

    return `${year}-${month}-${day}`;
}

function setDefaultDates() {
    const today = new Date();

    const firstDay =
        new Date(
            today.getFullYear(),
            today.getMonth(),
            1
        );

    fromDateInput.value =
        formatInputDate(firstDay);

    toDateInput.value =
        formatInputDate(today);
}

function formatDisplayDate(value) {
    if (!value) {
        return "-";
    }

    return new Date(
        `${value}T00:00:00`
    ).toLocaleDateString(
        "en-LK",
        {
            year: "numeric",
            month: "long",
            day: "numeric"
        }
    );
}

function formatCurrency(value) {
    const amount = Number(value);

    return currencyFormatter.format(
        Number.isFinite(amount)
            ? amount
            : 0
    );
}

function formatStatus(value) {
    return String(value || "")
        .toLowerCase()
        .split("_")
        .map(word =>
            word.charAt(0).toUpperCase() +
            word.slice(1)
        )
        .join(" ");
}

function showMessage(message, type = "") {
    reportMessage.textContent = message;
    reportMessage.className = "ui-message";

    if (type) {
        reportMessage.classList.add(type);
    }
}

function createCell(value) {
    const cell =
        document.createElement("td");

    cell.textContent = value;

    return cell;
}

function renderEmptyRow(
    tableBody,
    columnCount,
    message
) {
    tableBody.replaceChildren();

    const row =
        document.createElement("tr");

    const cell =
        createCell(message);

    cell.colSpan = columnCount;
    cell.className = "report-empty-cell";

    row.appendChild(cell);
    tableBody.appendChild(row);
}

function renderAppointmentStatuses(statuses) {
    if (!statuses || statuses.length === 0) {
        renderEmptyRow(
            appointmentStatusRows,
            2,
            "No appointments were found"
        );

        return;
    }

    appointmentStatusRows.replaceChildren();

    statuses.forEach(status => {
        const row =
            document.createElement("tr");

        row.appendChild(
            createCell(
                formatStatus(status.status)
            )
        );

        row.appendChild(
            createCell(
                status.appointmentCount
            )
        );

        appointmentStatusRows.appendChild(row);
    });
}

function renderTreatments(treatments) {
    if (!treatments || treatments.length === 0) {
        renderEmptyRow(
            treatmentRows,
            3,
            "No treatment activity was found"
        );

        return;
    }

    treatmentRows.replaceChildren();

    treatments.forEach(treatment => {
        const row =
            document.createElement("tr");

        row.appendChild(
            createCell(treatment.treatmentName)
        );

        row.appendChild(
            createCell(treatment.appointmentCount)
        );

        row.appendChild(
            createCell(
                formatCurrency(
                    treatment.billedAmount
                )
            )
        );

        treatmentRows.appendChild(row);
    });
}

function renderDentists(dentists) {
    if (!dentists || dentists.length === 0) {
        renderEmptyRow(
            dentistRows,
            4,
            "No dentist activity was found"
        );

        return;
    }

    dentistRows.replaceChildren();

    dentists.forEach(dentist => {
        const appointmentCount =
            Number(dentist.appointmentCount) || 0;

        const completedCount =
            Number(dentist.completedCount) || 0;

        const completionRate =
            appointmentCount === 0
                ? 0
                : (
                    completedCount /
                    appointmentCount *
                    100
                );

        const row =
            document.createElement("tr");

        row.appendChild(
            createCell(dentist.dentistName)
        );

        row.appendChild(
            createCell(appointmentCount)
        );

        row.appendChild(
            createCell(completedCount)
        );

        row.appendChild(
            createCell(
                `${completionRate.toFixed(1)}%`
            )
        );

        dentistRows.appendChild(row);
    });
}

function renderReport(report) {
    document.getElementById(
        "totalAppointments"
    ).textContent = report.totalAppointments;

    document.getElementById(
        "completedAppointments"
    ).textContent = report.completedAppointments;

    document.getElementById(
        "cancelledAppointments"
    ).textContent = report.cancelledAppointments;

    document.getElementById(
        "newPatients"
    ).textContent = report.newPatients;

    document.getElementById(
        "generatedBills"
    ).textContent = report.generatedBills;

    document.getElementById(
        "billedAmount"
    ).textContent =
        formatCurrency(report.billedAmount);

    document.getElementById(
        "collectedAmount"
    ).textContent =
        formatCurrency(report.collectedAmount);

    document.getElementById(
        "outstandingAmount"
    ).textContent =
        formatCurrency(report.outstandingAmount);

    reportPeriod.textContent =
        `${formatDisplayDate(report.fromDate)} ` +
        `to ${formatDisplayDate(report.toDate)}`;

    reportGeneratedAt.textContent =
        `Generated: ${
            new Date().toLocaleString("en-LK")
        }`;

    renderAppointmentStatuses(
        report.appointmentStatuses
    );

    renderTreatments(report.treatments);
    renderDentists(report.dentists);
}

async function loadSession() {
    const response = await fetch(
        "api/v1/auth/session",
        {
            credentials: "same-origin"
        }
    );

    if (!response.ok) {
        window.location.replace("login.html");
        return false;
    }

    const result = await response.json();

    if (result.user.role !== "ADMIN") {
        window.location.replace("dashboard.html");
        return false;
    }

    return true;
}

async function loadReport() {
    generateReportButton.disabled = true;

    showMessage(
        "Generating report...",
        "information"
    );

    try {
        const query =
            new URLSearchParams({
                from: fromDateInput.value,
                to: toDateInput.value
            });

        const response = await fetch(
            `api/v1/reports?${query.toString()}`,
            {
                credentials: "same-origin"
            }
        );

        const result = await response.json();

        if (!response.ok) {
            throw new Error(
                result.message ||
                "Report could not be generated"
            );
        }

        renderReport(result.report);

        showMessage(
            "Report generated successfully.",
            "success"
        );

    } catch (error) {
        showMessage(
            error.message ||
            "Report could not be generated",
            "error"
        );

    } finally {
        generateReportButton.disabled = false;
    }
}

reportForm.addEventListener(
    "submit",
    event => {
        event.preventDefault();
        loadReport();
    }
);

printReportButton.addEventListener(
    "click",
    () => {
        window.print();
    }
);

async function initializeReports() {
    const authorized = await loadSession();

    if (!authorized) {
        return;
    }

    setDefaultDates();
    await loadReport();
}

initializeReports().catch(() => {
    showMessage(
        "Reports could not be initialized.",
        "error"
    );
});