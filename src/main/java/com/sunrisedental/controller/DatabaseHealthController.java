package com.sunrisedental.controller;

import com.sunrisedental.config.DatabaseConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/api/v1/health/database")
public class DatabaseHealthController extends HttpServlet {

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        boolean databaseAvailable =
                DatabaseConfig.isHealthy();

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (databaseAvailable) {
            response.setStatus(HttpServletResponse.SC_OK);

            response.getWriter().write("""
                    {
                        "success": true,
                        "database": "sunrise_dental",
                        "status": "UP"
                    }
                    """);

        } else {
            response.setStatus(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE
            );

            response.getWriter().write("""
                    {
                        "success": false,
                        "database": "sunrise_dental",
                        "status": "DOWN",
                        "message": "Database connection is unavailable"
                    }
                    """);
        }
    }
}