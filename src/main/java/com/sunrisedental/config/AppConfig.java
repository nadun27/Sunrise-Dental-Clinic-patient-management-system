package com.sunrisedental.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {

    private static final String CONFIG_FILE = "application.properties";
    private static final Properties PROPERTIES = loadProperties();

    private AppConfig() {
    }

    public static String getRequired(String key) {
        String value = PROPERTIES.getProperty(key);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required configuration: " + key
            );
        }

        return value.trim();
    }

    public static int getInt(String key, int defaultValue) {
        String value = PROPERTIES.getProperty(key);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Configuration must be a number: " + key,
                    exception
            );
        }
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();

        try (InputStream inputStream =
                     AppConfig.class
                             .getClassLoader()
                             .getResourceAsStream(CONFIG_FILE)) {

            if (inputStream == null) {
                throw new IllegalStateException(
                        CONFIG_FILE + " was not found"
                );
            }

            properties.load(inputStream);
            return properties;

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load " + CONFIG_FILE,
                    exception
            );
        }
    }
}