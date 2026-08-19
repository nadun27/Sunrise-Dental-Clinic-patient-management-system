package com.sunrisedental.controller;

import com.sunrisedental.dao.impl.JdbcUserDao;
import com.sunrisedental.model.User;
import com.sunrisedental.service.AuthResult;
import com.sunrisedental.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

@WebServlet("/api/v1/auth/*")
public class AuthController extends HttpServlet {

    private AuthService authService;

    @Override
    public void init() throws ServletException {
        authService = new AuthService(
                new JdbcUserDao()
        );
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        String path = request.getPathInfo();

        if ("/login".equals(path)) {
            login(request, response);
        } else if ("/logout".equals(path)) {
            logout(request, response);
        } else {
            sendJson(
                    response,
                    HttpServletResponse.SC_NOT_FOUND,
                    """
                    {
                        "success": false,
                        "message": "Authentication endpoint not found"
                    }
                    """
            );
        }
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        if ("/session".equals(request.getPathInfo())) {
            getSession(request, response);
        } else {
            sendJson(
                    response,
                    HttpServletResponse.SC_NOT_FOUND,
                    """
                    {
                        "success": false,
                        "message": "Authentication endpoint not found"
                    }
                    """
            );
        }
    }

    private void login(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        String username =
                request.getParameter("username");

        String password =
                request.getParameter("password");

        try {
            AuthResult result =
                    authService.authenticate(
                            username,
                            password
                    );

            if (!result.success()) {
                sendJson(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        """
                        {
                            "success": false,
                            "message": "%s"
                        }
                        """.formatted(
                                escapeJson(result.message())
                        )
                );

                return;
            }

            HttpSession existingSession =
                    request.getSession(false);

            if (existingSession != null) {
                existingSession.invalidate();
            }

            HttpSession session =
                    request.getSession(true);

            User user = result.user();

            session.setAttribute(
                    "userId",
                    user.userId()
            );

            session.setAttribute(
                    "username",
                    user.username()
            );

            session.setAttribute(
                    "fullName",
                    user.fullName()
            );

            session.setAttribute(
                    "role",
                    user.role().name()
            );

            session.setMaxInactiveInterval(30 * 60);

            sendJson(
                    response,
                    HttpServletResponse.SC_OK,
                    """
                    {
                        "success": true,
                        "message": "Login successful",
                        "user": {
                            "username": "%s",
                            "fullName": "%s",
                            "role": "%s"
                        }
                    }
                    """.formatted(
                            escapeJson(user.username()),
                            escapeJson(user.fullName()),
                            escapeJson(user.role().name())
                    )
            );

        } catch (SQLException exception) {
            getServletContext().log(
                    "Authentication database error",
                    exception
            );

            sendJson(
                    response,
                    HttpServletResponse
                            .SC_INTERNAL_SERVER_ERROR,
                    """
                    {
                        "success": false,
                        "message": "Unable to complete login"
                    }
                    """
            );
        }
    }

    private void getSession(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        HttpSession session =
                request.getSession(false);

        if (session == null
                || session.getAttribute("userId") == null) {

            sendJson(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    """
                    {
                        "success": false,
                        "message": "Not authenticated"
                    }
                    """
            );

            return;
        }

        sendJson(
                response,
                HttpServletResponse.SC_OK,
                """
                {
                    "success": true,
                    "user": {
                        "username": "%s",
                        "fullName": "%s",
                        "role": "%s"
                    }
                }
                """.formatted(
                        escapeJson(
                                String.valueOf(
                                        session.getAttribute(
                                                "username"
                                        )
                                )
                        ),
                        escapeJson(
                                String.valueOf(
                                        session.getAttribute(
                                                "fullName"
                                        )
                                )
                        ),
                        escapeJson(
                                String.valueOf(
                                        session.getAttribute(
                                                "role"
                                        )
                                )
                        )
                )
        );
    }

    private void logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        HttpSession session =
                request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        sendJson(
                response,
                HttpServletResponse.SC_OK,
                """
                {
                    "success": true,
                    "message": "Logout successful"
                }
                """
        );
    }

    private void sendJson(
            HttpServletResponse response,
            int status,
            String json
    ) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(
                "Cache-Control",
                "no-store"
        );

        response.getWriter().write(json);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}