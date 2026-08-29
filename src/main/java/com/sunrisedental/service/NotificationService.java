package com.sunrisedental.service;

import com.sunrisedental.dao.NotificationDao;
import com.sunrisedental.event.ClinicEvent;
import com.sunrisedental.exception.NotFoundException;
import com.sunrisedental.model.Notification;
import com.sunrisedental.model.NotificationRecipient;
import com.sunrisedental.model.NotificationStatus;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class NotificationService {

    private final NotificationDao notificationDao;

    public NotificationService(
            NotificationDao notificationDao
    ) {
        this.notificationDao = notificationDao;
    }

    public Optional<Notification> queue(
            ClinicEvent event
    ) throws SQLException {

        if (event == null || event.appointmentId() <= 0) {
            throw new IllegalArgumentException(
                    "A valid clinic event is required"
            );
        }

        NotificationRecipient recipient =
                notificationDao.findRecipient(
                        event.appointmentId()
                ).orElseThrow(() ->
                        new NotFoundException(
                                "Appointment recipient was not found"
                        )
                );

        if (recipient.email() == null ||
                recipient.email().isBlank()) {

            return Optional.empty();
        }

        Map<String, String> payload = new HashMap<>();

        payload.put("patientName", recipient.patientName());
        payload.put(
                "appointmentNumber",
                recipient.appointmentNumber()
        );
        payload.put("dentistName", recipient.dentistName());
        payload.put(
                "treatmentName",
                recipient.treatmentName()
        );
        payload.put(
                "appointmentStart",
                recipient.appointmentStart().toString()
        );
        payload.putAll(event.values());

        Notification notification = new Notification(
                0,
                recipient.appointmentId(),
                recipient.email().trim(),
                event.type().name(),
                payload,
                NotificationStatus.PENDING,
                0,
                LocalDateTime.now(),
                null,
                null,
                null
        );

        return Optional.of(
                notificationDao.enqueue(notification)
        );
    }
}
