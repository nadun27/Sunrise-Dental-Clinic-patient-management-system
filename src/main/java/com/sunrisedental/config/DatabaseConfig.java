package com.sunrisedental.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseConfig {

    private static final HikariDataSource DATA_SOURCE =
            createDataSource();

    private DatabaseConfig() {
    }

    private static HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(
                AppConfig.getRequired("db.url")
        );

        config.setUsername(
                AppConfig.getRequired("db.username")
        );

        config.setPassword(
                AppConfig.getRequired("db.password")
        );

        config.setDriverClassName(
                "com.mysql.cj.jdbc.Driver"
        );

        config.setMaximumPoolSize(
                AppConfig.getInt("db.pool.maximumSize", 10)
        );

        config.setMinimumIdle(
                AppConfig.getInt("db.pool.minimumIdle", 2)
        );

        config.setConnectionTimeout(
                AppConfig.getInt("db.pool.connectionTimeout", 10000)
        );

        config.setPoolName("SunriseDentalPool");

        return new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    public static boolean isHealthy() {
        String query = "SELECT 1";

        try (
                Connection connection = getConnection();
                var statement = connection.prepareStatement(query);
                var resultSet = statement.executeQuery()
        ) {
            return resultSet.next()
                    && resultSet.getInt(1) == 1;

        } catch (SQLException exception) {
            return false;
        }
    }

    public static void close() {
        if (!DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
        }
    }
}