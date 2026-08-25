const billingForm =
    document.getElementById("billingForm");

const billingFormPanel =
    document.getElementById("billingFormPanel");

const appointmentSelect =
    document.getElementById("billAppointmentId");

const discountAmount =
    document.getElementById("discountAmount");

const discountReason =
    document.getElementById("discountReason");

const createBillButton =
    document.getElementById("createBillButton");

const billingMessage =
    document.getElementById("billingMessage");

const billRows =
    document.getElementById("billRows");

const billDialog =
    document.getElementById("billDialog");

let completedAppointments = [];
let canCreateBills = false;

async function apiRequest(url, options = {}) {
    const response = await fetch(url, {
        credentials: "same-origin",
        ...options
    });

    if (response.status === 401) {
        window.location.replace("login.html");

        throw new Error(
            "Authentication required"
        );
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
    const result =
        await apiRequest(
            "api/v1/auth/session"
        );

    canCreateBills =
        result.user.role === "ADMIN" ||
        result.user.role === "CASHIER";

    billingFormPanel.hidden =
        !canCreateBills;
}

async function loadBillableAppointments() {
    if (!canCreateBills) {
        return;
    }

    const [appointmentResult, billResult] =
        await Promise.all([
            apiRequest(
                "api/v1/appointments?" +
                "search=&date=&status=COMPLETED"
            ),

            apiRequest(
                "api/v1/bills/?search=&status="
            )
        ]);

    const billedAppointmentIds =
        new Set(
            billResult.bills.map(
                bill => bill.appointmentId
            )
        );

    completedAppointments =
        appointmentResult.appointments.filter(
            appointment =>
                !billedAppointmentIds.has(
                    appointment.appointmentId
                )
        );

    fillAppointmentSelect();
}

function fillAppointmentSelect() {
    const firstOption =
        appointmentSelect.options[0];

    appointmentSelect.replaceChildren(
        firstOption
    );

    completedAppointments.forEach(
        appointment => {
            const option =
                document.createElement("option");

            option.value =
                appointment.appointmentId;

            option.textContent =
                `${appointment.appointmentNumber} — ` +
                `${appointment.patientName} — ` +
                `${appointment.treatmentName}`;

            appointmentSelect.appendChild(
                option
            );
        }
    );

    if (completedAppointments.length === 0) {
        firstOption.textContent =
            "No completed unbilled appointments";
    } else {
        firstOption.textContent =
            "Select completed appointment";
    }
}

async function loadBills() {
    showMessage(
        "Loading bills...",
        "information"
    );

    const parameters =
        new URLSearchParams({
            search: document
                .getElementById("billSearch")
                .value
                .trim(),

            status: document
                .getElementById("billStatus")
                .value
        });

    try {
        const result =
            await apiRequest(
                `api/v1/bills/?${parameters}`
            );

        renderBills(result.bills);

        showMessage(
            `${result.count} bill(s) found`,
            "success"
        );

    } catch (error) {
        billRows.replaceChildren();

        showMessage(
            error.message,
            "error"
        );
    }
}

function renderBills(bills) {
    billRows.replaceChildren();

    if (bills.length === 0) {
        const row =
            document.createElement("tr");

        const tableCell =
            document.createElement("td");

        tableCell.colSpan = 6;
        tableCell.textContent =
            "No bills were found.";

        row.appendChild(tableCell);
        billRows.appendChild(row);

        return;
    }

    bills.forEach(bill => {
        const row =
            document.createElement("tr");

        row.appendChild(
            cell(bill.invoiceNumber)
        );

        row.appendChild(
            cell(bill.patientName)
        );

        row.appendChild(
            cell(bill.appointmentNumber)
        );

        row.appendChild(
            cell(currency(bill.totalAmount))
        );

        const statusCell =
            document.createElement("td");

        const statusBadge =
            document.createElement("span");

        statusBadge.className =
            "status-badge " +
            `payment-${bill.paymentStatus
                .toLowerCase()}`;

        statusBadge.textContent =
            formatStatus(
                bill.paymentStatus
            );

        statusCell.appendChild(
            statusBadge
        );

        row.appendChild(statusCell);

        const actions =
            document.createElement("td");

        actions.className =
            "table-actions";

        actions.appendChild(
            actionButton(
                "View / Print",
                () => showBill(bill)
            )
        );

        row.appendChild(actions);
        billRows.appendChild(row);
    });
}

function cell(value) {
    const element =
        document.createElement("td");

    element.textContent =
        value ?? "-";

    return element;
}

function actionButton(label, action) {
    const button =
        document.createElement("button");

    button.type = "button";
    button.textContent = label;
    button.className = "small-button";

    button.addEventListener(
        "click",
        action
    );

    return button;
}

function selectedAppointment() {
    return completedAppointments.find(
        appointment =>
            appointment.appointmentId
                .toString() ===
            appointmentSelect.value
    );
}

function updateAppointmentSummary() {
    const appointment =
        selectedAppointment();

    const summary =
        document.getElementById(
            "selectedAppointmentSummary"
        );

    if (!appointment) {
        summary.hidden = true;

        discountAmount.max = "";

        updatePreview();
        return;
    }

    summary.hidden = false;

    setText(
        "summaryPatient",
        appointment.patientName
    );

    setText(
        "summaryTreatment",
        appointment.treatmentName
    );

    setText(
        "summaryConsultationFee",
        currency(
            appointment.consultationFee
        )
    );

    setText(
        "summaryTreatmentFee",
        currency(
            appointment.treatmentFee
        )
    );

    const subtotal =
        Number(appointment.consultationFee) +
        Number(appointment.treatmentFee);

    discountAmount.max =
        subtotal.toFixed(2);

    updatePreview();
}

function updatePreview() {
    const appointment =
        selectedAppointment();

    const consultationFee =
        appointment
            ? Number(
                appointment.consultationFee
            )
            : 0;

    const treatmentFee =
        appointment
            ? Number(
                appointment.treatmentFee
            )
            : 0;

    const subtotal =
        consultationFee +
        treatmentFee;

    const discount =
        Number(discountAmount.value) || 0;

    const tax = 0;

    const total =
        Math.max(
            subtotal - discount + tax,
            0
        );

    setText(
        "previewSubtotal",
        currency(subtotal)
    );

    setText(
        "previewDiscount",
        currency(discount)
    );

    setText(
        "previewTax",
        currency(tax)
    );

    setText(
        "previewTotal",
        currency(total)
    );

    discountReason.required =
        discount > 0;
}

billingForm.addEventListener(
    "submit",
    async event => {
        event.preventDefault();

        if (!billingForm.reportValidity()) {
            return;
        }

        const appointment =
            selectedAppointment();

        if (!appointment) {
            showMessage(
                "Select a completed appointment",
                "error"
            );

            return;
        }

        const subtotal =
            Number(
                appointment.consultationFee
            ) +
            Number(
                appointment.treatmentFee
            );

        const discount =
            Number(discountAmount.value) || 0;

        if (discount > subtotal) {
            showMessage(
                "Discount cannot exceed the subtotal",
                "error"
            );

            return;
        }

        createBillButton.disabled = true;

        const body =
            new URLSearchParams(
                new FormData(billingForm)
            );

        try {
            const result =
                await apiRequest(
                    "api/v1/bills/",
                    {
                        method: "POST",

                        headers: {
                            "Content-Type":
                                "application/" +
                                "x-www-form-urlencoded"
                        },

                        body
                    }
                );

            billingForm.reset();
            discountAmount.value = "0.00";

            document.getElementById(
                "selectedAppointmentSummary"
            ).hidden = true;

            updatePreview();

            await Promise.all([
                loadBills(),
                loadBillableAppointments()
            ]);

            showMessage(
                result.message,
                "success"
            );

            showBill(result.bill);

        } catch (error) {
            showMessage(
                error.message,
                "error"
            );

        } finally {
            createBillButton.disabled = false;
        }
    }
);

function showBill(bill) {
    setText(
        "detailInvoiceNumber",
        bill.invoiceNumber
    );

    setText(
        "detailBillAppointment",
        bill.appointmentNumber
    );

    setText(
        "detailBillPatient",
        bill.patientName
    );

    setText(
        "detailBillCreatedAt",
        formatDateTime(
            bill.createdAt
        )
    );

    setText(
        "detailConsultationFee",
        currency(
            bill.consultationFee
        )
    );

    setText(
        "detailTreatmentFee",
        currency(
            bill.treatmentFee
        )
    );

    setText(
        "detailDiscountAmount",
        currency(
            bill.discountAmount
        )
    );

    setText(
        "detailTaxAmount",
        currency(
            bill.taxAmount
        )
    );

    setText(
        "detailTotalAmount",
        currency(
            bill.totalAmount
        )
    );

    setText(
        "detailPaymentStatus",
        formatStatus(
            bill.paymentStatus
        )
    );

    setText(
        "detailDiscountReason",
        bill.discountReason ||
        "Not provided"
    );

    billDialog.showModal();
}

function setText(id, value) {
    document.getElementById(id)
        .textContent = value ?? "-";
}

function showMessage(message, type) {
    billingMessage.textContent =
        message;

    billingMessage.className =
        `ui-message ${type}`;
}

function formatStatus(value) {
    return value
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(
            /\b\w/g,
            letter =>
                letter.toUpperCase()
        );
}

function formatDateTime(value) {
    if (!value) {
        return "-";
    }

    return new Intl.DateTimeFormat(
        "en-LK",
        {
            dateStyle: "medium",
            timeStyle: "short"
        }
    ).format(new Date(value));
}

function currency(value) {
    return new Intl.NumberFormat(
        "en-LK",
        {
            style: "currency",
            currency: "LKR"
        }
    ).format(Number(value) || 0);
}

document
    .getElementById("billSearchForm")
    .addEventListener(
        "submit",
        event => {
            event.preventDefault();
            loadBills();
        }
    );

appointmentSelect.addEventListener(
    "change",
    updateAppointmentSummary
);

discountAmount.addEventListener(
    "input",
    updatePreview
);

document
    .getElementById("closeBillDialog")
    .addEventListener(
        "click",
        () => billDialog.close()
    );

document
    .getElementById("printBillButton")
    .addEventListener(
        "click",
        () => window.print()
    );

async function initialize() {
    await loadSession();

    await Promise.all([
        loadBills(),
        loadBillableAppointments()
    ]);

    updatePreview();
}

initialize().catch(error => {
    showMessage(
        error.message,
        "error"
    );
});