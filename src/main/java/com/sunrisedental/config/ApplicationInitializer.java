package com.sunrisedental.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class ApplicationInitializer
        implements ServletContextListener {

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
        DatabaseConfig.close();

        event.getServletContext().log(
                "MySQL connection pool closed."
        );
    }
}