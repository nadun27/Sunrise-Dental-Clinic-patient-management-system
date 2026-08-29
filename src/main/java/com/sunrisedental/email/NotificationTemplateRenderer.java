package com.sunrisedental.email;

import com.sunrisedental.event.ClinicEventType;
import com.sunrisedental.model.Notification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

public class NotificationTemplateRenderer {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd MMMM yyyy 'at' hh:mm a"
            );

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMMM yyyy");

    public EmailMessage render(Notification notification) {
        ClinicEventType type;

        try {
            type = ClinicEventType.valueOf(
                    notification.templateCode()
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported notification template: " +
                            notification.templateCode(),
                    exception
            );
        }

        return switch (type) {
            case APPOINTMENT_CREATED ->
                    appointmentCreated(notification);

            case APPOINTMENT_RESCHEDULED ->
                    appointmentRescheduled(notification);

            case APPOINTMENT_CANCELLED ->
                    appointmentCancelled(notification);

            case BILL_CREATED ->
                    billCreated(notification);

            case PAYMENT_RECEIVED ->
                    paymentReceived(notification);

            case TREATMENT_COMPLETED ->
                    treatmentCompleted(notification);
        };
    }

    private EmailMessage appointmentCreated(
            Notification notification
    ) {
        Map<String, String> values = notification.payload();
        String number = value(values, "appointmentNumber");

        String body = greeting(values) + "\n\n" +
                "Your dental appointment has been registered successfully." +
                "\n\nAppointment: " + number +
                "\nDate and time: " + appointmentTime(values) +
                "\nDentist: " + value(values, "dentistName") +
                "\nTreatment: " + value(values, "treatmentName") +
                closing();

        return new EmailMessage(
                notification.recipient(),
                "Appointment Registered - " + number,
                body
        );
    }

    private EmailMessage appointmentRescheduled(
            Notification notification
    ) {
        Map<String, String> values = notification.payload();
        String number = value(values, "appointmentNumber");

        String body = greeting(values) + "\n\n" +
                "Your dental appointment has been rescheduled." +
                "\n\nAppointment: " + number +
                "\nNew date and time: " + appointmentTime(values) +
                "\nDentist: " + value(values, "dentistName") +
                "\nTreatment: " + value(values, "treatmentName") +
                closing();

        return new EmailMessage(
                notification.recipient(),
                "Appointment Rescheduled - " + number,
                body
        );
    }

    private EmailMessage appointmentCancelled(
            Notification notification
    ) {
        Map<String, String> values = notification.payload();
        String number = value(values, "appointmentNumber");

        String body = greeting(values) + "\n\n" +
                "Your dental appointment has been cancelled." +
                "\n\nAppointment: " + number +
                "\nReason: " + optional(
                        values,
                        "cancellationReason",
                        "Not provided"
                ) +
                "\n\nPlease contact the clinic if you would like " +
                "to arrange another appointment." +
                closing();

        return new EmailMessage(
                notification.recipient(),
                "Appointment Cancelled - " + number,
                body
        );
    }

    private EmailMessage billCreated(
            Notification notification
    ) {
        Map<String, String> values = notification.payload();
        String invoice = value(values, "invoiceNumber");

        String body = greeting(values) + "\n\n" +
                "Your bill has been generated." +
                "\n\nInvoice: " + invoice +
                "\nAppointment: " +
                value(values, "appointmentNumber") +
                "\nTotal amount: LKR " +
                value(values, "totalAmount") +
                "\nPayment status: Unpaid" +
                closing();

        return new EmailMessage(
                notification.recipient(),
                "Dental Bill Generated - " + invoice,
                body
        );
    }

    private EmailMessage paymentReceived(
            Notification notification
    ) {
        Map<String, String> values = notification.payload();
        String receipt = value(values, "receiptNumber");

        String body = greeting(values) + "\n\n" +
                "We have received your payment." +
                "\n\nReceipt: " + receipt +
                "\nInvoice: " + value(values, "invoiceNumber") +
                "\nAmount received: LKR " +
                value(values, "amount") +
                "\nPayment method: " +
                value(values, "paymentMethod") +
                "\nRemaining balance: LKR " +
                value(values, "remainingBalance") +
                closing();

        return new EmailMessage(
                notification.recipient(),
                "Payment Receipt - " + receipt,
                body
        );
    }

    private EmailMessage treatmentCompleted(
            Notification notification
    ) {
        Map<String, String> values = notification.payload();
        String number = value(values, "appointmentNumber");

        String followUp = optional(
                values,
                "followUpDate",
                "No follow-up date scheduled"
        );

        if (!"No follow-up date scheduled".equals(followUp)) {
            followUp = formatDate(followUp);
        }

        String body = greeting(values) + "\n\n" +
                "Your dental treatment session has been completed." +
                "\n\nAppointment: " + number +
                "\nTreatment: " + value(values, "treatmentName") +
                "\nDentist: " + value(values, "dentistName") +
                "\nFollow-up: " + followUp +
                "\n\nPlease follow the care and prescription " +
                "instructions provided by your dentist." +
                closing();

        return new EmailMessage(
                notification.recipient(),
                "Treatment Completed - " + number,
                body
        );
    }

    private String greeting(Map<String, String> values) {
        return "Dear " + value(values, "patientName") + ",";
    }

    private String closing() {
        return "\n\nRegards,\nSunrise Dental Clinic\nColombo, Sri Lanka";
    }

    private String appointmentTime(
            Map<String, String> values
    ) {
        String value = value(values, "appointmentStart");

        try {
            return LocalDateTime.parse(value)
                    .format(DATE_TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            return value;
        }
    }

    private String formatDate(String value) {
        try {
            return LocalDate.parse(value).format(DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            return value;
        }
    }

    private String value(
            Map<String, String> values,
            String key
    ) {
        return optional(values, key, "-");
    }

    private String optional(
            Map<String, String> values,
            String key,
            String fallback
    ) {
        String value = values.get(key);

        return value == null || value.isBlank()
                ? fallback
                : value.trim();
    }
}
