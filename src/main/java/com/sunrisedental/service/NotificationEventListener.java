package com.sunrisedental.service;

import com.sunrisedental.event.ClinicEvent;
import com.sunrisedental.event.ClinicEventListener;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NotificationEventListener
        implements ClinicEventListener {

    private static final Logger LOGGER = Logger.getLogger(
            NotificationEventListener.class.getName()
    );

    private final NotificationService notificationService;

    public NotificationEventListener(
            NotificationService notificationService
    ) {
        this.notificationService = notificationService;
    }

    @Override
    public void onEvent(ClinicEvent event) {
        try {
            notificationService.queue(event);
        } catch (SQLException | RuntimeException exception) {
            LOGGER.log(
                    Level.WARNING,
                    "Unable to queue patient notification for " +
                            event.type(),
                    exception
            );
        }
    }
}
