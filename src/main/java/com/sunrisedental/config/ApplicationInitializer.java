package com.sunrisedental.config;

import com.sunrisedental.dao.impl.JdbcNotificationDao;
import com.sunrisedental.email.NotificationDispatcher;
import com.sunrisedental.email.NotificationScheduler;
import com.sunrisedental.email.NotificationTemplateRenderer;
import com.sunrisedental.email.SmtpEmailSender;
import com.sunrisedental.event.ClinicEventPublisher;
import com.sunrisedental.service.NotificationEventListener;
import com.sunrisedental.service.NotificationService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class ApplicationInitializer
        implements ServletContextListener {

    private NotificationEventListener notificationListener;
    private NotificationScheduler notificationScheduler;

    @Override
    public void contextInitialized(
            ServletContextEvent event
    ) {
        boolean databaseAvailable =
                DatabaseConfig.isHealthy();

        if (databaseAvailable) {
            event.getServletContext().log(
                    "MySQL database connection established successfully."
            );

            configureNotifications(event);
        } else {
            event.getServletContext().log(
                    "MySQL database connection failed."
            );
        }
    }

    @Override
    public void contextDestroyed(
            ServletContextEvent event
    ) {
        if (notificationScheduler != null) {
            notificationScheduler.stop();
        }

        if (notificationListener != null) {
            ClinicEventPublisher.getInstance().unregister(
                    notificationListener
            );
        }

        DatabaseConfig.close();

        event.getServletContext().log(
                "MySQL connection pool closed."
        );
    }

    private void configureNotifications(
            ServletContextEvent event
    ) {
        JdbcNotificationDao notificationDao =
                new JdbcNotificationDao();

        notificationListener =
                new NotificationEventListener(
                        new NotificationService(
                                notificationDao
                        )
                );

        ClinicEventPublisher.getInstance().register(
                notificationListener
        );

        if (!AppConfig.getBoolean(
                "mail.enabled",
                false
        )) {
            event.getServletContext().log(
                    "Patient emails are queued, but SMTP delivery " +
                            "is disabled by configuration."
            );

            return;
        }

        NotificationDispatcher dispatcher =
                new NotificationDispatcher(
                        notificationDao,
                        new SmtpEmailSender(),
                        new NotificationTemplateRenderer(),
                        AppConfig.getInt(
                                "mail.batchSize",
                                10
                        )
                );

        notificationScheduler =
                new NotificationScheduler(
                        dispatcher,
                        AppConfig.getInt(
                                "mail.intervalSeconds",
                                30
                        ),
                        event.getServletContext()::log
                );

        notificationScheduler.start();
    }
}
