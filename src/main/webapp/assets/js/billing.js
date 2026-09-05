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

const paymentDialog =
    document.getElementById("paymentDialog");

const paymentReceiptDialog =
    document.getElementById(
        "paymentReceiptDialog"
    );

const paymentForm =
    document.getElementById("paymentForm");

const paymentMethod =
    document.getElementById("paymentMethod");

const paymentReferenceNumber =
    document.getElementById(
        "paymentReferenceNumber"
    );

const paymentAmount =
    document.getElementById("paymentAmount");

const paymentMessage =
    document.getElementById("paymentMessage");

const recordPaymentButton =
    document.getElementById(
        "recordPaymentButton"
    );

let completedAppointments = [];
let canCreateBills = false;
let selectedPaymentBill = null;

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

    /*
       shell.js has already fetched the session to build the sidebar, so reuse
       its promise instead of calling api/v1/auth/session a second time. The
       signed-in user is shown in the sidebar profile block, not on this page.
    */
    const user = await window.clinicShell.session;

    canCreateBills =
        user.role === "ADMIN" ||
        user.role === "CASHIER";

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
        showMessage(
            error.message,
            "error"
        );

        setBillRows(
            document.createDocumentFragment()
        );
    }
}

/*
   Every write to the table goes through here, so a page that is missing the
   table body reports it instead of failing on a null reference.
*/
function setBillRows(content) {
    if (!billRows) {
        showMessage(
            "The bills table is missing from this page. " +
            "Reload to get the latest version.",
            "error"
        );

        return false;
    }

    billRows.replaceChildren(content);

    return true;
}

function renderBills(bills) {
    if (bills.length === 0) {
        const row =
            document.createElement("tr");

        const tableCell =
            document.createElement("td");

        tableCell.colSpan = 5;
        tableCell.textContent =
            "No bills were found.";

        row.appendChild(tableCell);

        return setBillRows(row);
    }

    const rows =
        document.createDocumentFragment();

    bills.forEach(bill => {
        const row =
            document.createElement("tr");

        row.appendChild(
            cell(bill.invoiceNumber)
        );

        /* Patient name over the appointment it was billed for */
        row.appendChild(
            cell(
                `${bill.patientName}\n` +
                `${bill.appointmentNumber}`,
                "stacked-cell"
            )
        );

        row.appendChild(
            cell(
                currency(bill.totalAmount),
                "amount-cell"
            )
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

        if (
            canCreateBills &&
            (
                bill.paymentStatus === "UNPAID" ||
                bill.paymentStatus ===
                    "PARTIALLY_PAID"
            )
        ) {
            actions.appendChild(
                actionButton(
                    "Record Payment",
                    () => openPaymentDialog(bill)
                )
            );
        }

        row.appendChild(actions);
        rows.appendChild(row);
    });

    return setBillRows(rows);
}

function cell(value, className = "") {
    const element =
        document.createElement("td");

    element.textContent =
        value ?? "-";

    if (className) {
        element.className = className;
    }

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

async function openPaymentDialog(bill) {
    selectedPaymentBill = bill;

    paymentForm.reset();
    paymentMethod.value = "CASH";
    updateReferenceRequirement();

    setText(
        "paymentInvoiceNumber",
        bill.invoiceNumber
    );

    setText(
        "paymentPatientName",
        bill.patientName
    );

    setText(
        "paymentBillTotal",
        currency(bill.totalAmount)
    );

    document.getElementById(
        "paymentBillId"
    ).value = bill.billId;

    paymentMessage.textContent =
        "Loading payment information...";

    paymentMessage.className =
        "ui-message information";

    paymentDialog.showModal();

    try {
        const result = await apiRequest(
            `api/v1/payments/?billId=${bill.billId}`
        );

        setText(
            "paymentAlreadyPaid",
            currency(result.totalPaid)
        );

        setText(
            "paymentRemaining",
            currency(result.remainingBalance)
        );

        paymentAmount.max =
            Number(result.remainingBalance)
                .toFixed(2);

        paymentAmount.value =
            Number(result.remainingBalance)
                .toFixed(2);

        renderPaymentHistory(
            result.payments
        );

        paymentMessage.textContent = "";
        paymentMessage.className = "ui-message";

    } catch (error) {
        paymentMessage.textContent =
            error.message;

        paymentMessage.className =
            "ui-message error";
    }
}

function renderPaymentHistory(payments) {
    const rows = document.getElementById(
        "paymentHistoryRows"
    );

    rows.replaceChildren();

    if (payments.length === 0) {
        const row = document.createElement("tr");
        const tableCell =
            document.createElement("td");

        tableCell.colSpan = 4;
        tableCell.textContent =
            "No payments recorded.";

        row.appendChild(tableCell);
        rows.appendChild(row);
        return;
    }

    payments.forEach(payment => {
        const row = document.createElement("tr");

        row.appendChild(
            cell(payment.receiptNumber)
        );

        row.appendChild(
            cell(formatStatus(
                payment.paymentMethod
            ))
        );

        row.appendChild(
            cell(
                currency(payment.amount),
                "amount-cell"
            )
        );

        row.appendChild(
            cell(formatDateTime(payment.paidAt))
        );

        rows.appendChild(row);
    });
}

function updateReferenceRequirement() {
    const nonCash =
        paymentMethod.value !== "CASH";

    paymentReferenceNumber.required = nonCash;

    paymentReferenceNumber.placeholder = nonCash
        ? "Enter transaction reference"
        : "Optional for cash payments";
}

paymentForm.addEventListener(
    "submit",
    async event => {
        event.preventDefault();

        if (!paymentForm.reportValidity()) {
            return;
        }

        const amount = Number(
            paymentAmount.value
        );

        const maximum = Number(
            paymentAmount.max
        );

        if (amount <= 0 || amount > maximum) {
            paymentMessage.textContent =
                "Enter an amount within the " +
                "remaining balance";

            paymentMessage.className =
                "ui-message error";

            return;
        }

        recordPaymentButton.disabled = true;

        try {
            const result = await apiRequest(
                "api/v1/payments/",
                {
                    method: "POST",
                    headers: {
                        "Content-Type":
                            "application/" +
                            "x-www-form-urlencoded"
                    },
                    body: new URLSearchParams(
                        new FormData(paymentForm)
                    )
                }
            );

            paymentDialog.close();

            await loadBills();

            showMessage(
                result.message,
                "success"
            );

            showPaymentReceipt(
                result.payment
            );

        } catch (error) {
            paymentMessage.textContent =
                error.message;

            paymentMessage.className =
                "ui-message error";

        } finally {
            recordPaymentButton.disabled = false;
        }
    }
);

function showPaymentReceipt(payment) {
    setText(
        "receiptPaymentNumber",
        payment.receiptNumber
    );

    setText(
        "receiptInvoiceNumber",
        payment.invoiceNumber
    );

    setText(
        "receiptPatientName",
        payment.patientName
    );

    setText(
        "receiptPaidAt",
        formatDateTime(payment.paidAt)
    );

    setText(
        "receiptPaymentMethod",
        formatStatus(payment.paymentMethod)
    );

    setText(
        "receiptReferenceNumber",
        payment.referenceNumber ||
        "Not applicable"
    );

    setText(
        "receiptPaymentAmount",
        currency(payment.amount)
    );

    paymentReceiptDialog.showModal();
}

function setText(id, value) {
    document.getElementById(id)
        .textContent = value ?? "-";
}

function showMessage(message, type) {

    /* Guarded so reporting a problem can never itself throw */
    if (!billingMessage) {
        return;
    }

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

paymentMethod.addEventListener(
    "change",
    updateReferenceRequirement
);

/* The header close button and the form's Cancel button both dismiss it */
[
    "closePaymentDialog",
    "cancelPaymentButton"
].forEach(id =>
    document
        .getElementById(id)
        .addEventListener(
            "click",
            () => paymentDialog.close()
        )
);

document
    .getElementById("closePaymentReceipt")
    .addEventListener(
        "click",
        () => paymentReceiptDialog.close()
    );

document
    .getElementById("printPaymentReceipt")
    .addEventListener(
        "click",
        () => {
            document.body.classList.add(
                "printing-payment"
            );

            window.print();
        }
    );

window.addEventListener(
    "afterprint",
    () => document.body.classList.remove(
        "printing-payment"
    )
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
