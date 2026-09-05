package com.sunrisedental.controller;

import com.google.gson.Gson;
import com.sunrisedental.dao.impl.JdbcStaffDao;
import com.sunrisedental.dto.request.StaffRequest;
import com.sunrisedental.exception.ConflictException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.StaffMember;
import com.sunrisedental.service.StaffService;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/v1/staff/*")
public class StaffController extends HttpServlet {

    private final Gson gson = new Gson();

    private StaffService staffService;

    @Override
    public void init() {
        staffService = new StaffService(
                new JdbcStaffDao()
        );
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        try {
            if (!isAdministrator(request)) {
                writeError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "Only administrators can view staff members"
                );
                return;
            }

            String path = request.getPathInfo();

            if (path != null && !path.equals("/")) {
                writeError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "Staff API route was not found"
                );
                return;
            }

            String searchTerm =
                    request.getParameter("search");

            boolean includeInactive =
                    Boolean.parseBoolean(
                            request.getParameter(
                                    "includeInactive"
                            )
                    );

            List<StaffMember> staffMembers =
                    staffService.search(
                            searchTerm,
                            includeInactive
                    );

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put("success", true);
            result.put("count", staffMembers.size());
            result.put(
                    "staffMembers",
                    staffMembers.stream()
                            .map(this::staffMemberToMap)
                            .toList()
            );

            writeJson(
                    response,
                    HttpServletResponse.SC_OK,
                    gson.toJson(result)
            );

        } catch (ValidationException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            log("Staff database error", exception);

            writeError(
                    response,
                    HttpServletResponse
                            .SC_INTERNAL_SERVER_ERROR,
                    "Staff information could not be retrieved"
            );
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        request.setCharacterEncoding("UTF-8");

        try {
            if (!isAdministrator(request)) {
                writeError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "Only administrators can add staff members"
                );
                return;
            }

            String path = request.getPathInfo();

            if (path != null && !path.equals("/")) {
                writeError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "Staff API route was not found"
                );
                return;
            }

            StaffMember staffMember =
                    staffService.create(
                            createStaffRequest(request)
                    );

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put("success", true);
            result.put(
                    "message",
                    "Staff member added successfully"
            );
            result.put(
                    "staffMember",
                    staffMemberToMap(staffMember)
            );

            writeJson(
                    response,
                    HttpServletResponse.SC_CREATED,
                    gson.toJson(result)
            );

        } catch (ValidationException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (ConflictException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_CONFLICT,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            log("Staff database error", exception);

            writeError(
                    response,
                    HttpServletResponse
                            .SC_INTERNAL_SERVER_ERROR,
                    "Staff member could not be added"
            );
        }
    }

    private StaffRequest createStaffRequest(
            HttpServletRequest request
    ) {
        return new StaffRequest(
                request.getParameter("username"),
                request.getParameter("password"),
                request.getParameter("confirmPassword"),
                request.getParameter("fullName"),
                request.getParameter("email"),
                request.getParameter("contactNumber"),
                request.getParameter("role"),
                request.getParameter("registrationNumber"),
                request.getParameter("specialization"),
                parseOptionalAmount(
                        request.getParameter(
                                "consultationFee"
                        )
                )
        );
    }

    private BigDecimal parseOptionalAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new ValidationException(
                    "Enter a valid consultation fee"
            );
        }
    }

    private boolean isAdministrator(
            HttpServletRequest request
    ) {
        HttpSession session = request.getSession(false);

        return session != null &&
                "ADMIN".equals(
                        session.getAttribute("role")
                );
    }

    private Map<String, Object> staffMemberToMap(
            StaffMember staffMember
    ) {
        Map<String, Object> value =
                new LinkedHashMap<>();

        value.put("userId", staffMember.userId());
        value.put("username", staffMember.username());
        value.put("fullName", staffMember.fullName());
        value.put("email", staffMember.email());
        value.put(
                "contactNumber",
                staffMember.contactNumber()
        );
        value.put("role", staffMember.role().name());
        value.put("active", staffMember.active());
        value.put(
                "registrationNumber",
                staffMember.registrationNumber()
        );
        value.put(
                "specialization",
                staffMember.specialization()
        );
        value.put(
                "consultationFee",
                staffMember.consultationFee()
        );
        value.put(
                "createdAt",
                staffMember.createdAt() == null
                        ? null
                        : staffMember.createdAt().toString()
        );

        return value;
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("success", false);
        result.put("message", message);

        writeJson(
                response,
                status,
                gson.toJson(result)
        );
    }

    private void writeJson(
            HttpServletResponse response,
            int status,
            String json
    ) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }
}
