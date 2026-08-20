package com.sunrisedental.controller;

import com.sunrisedental.dao.impl.JdbcPatientDao;
import com.sunrisedental.dto.request.PatientRequest;
import com.sunrisedental.exception.NotFoundException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Patient;
import com.sunrisedental.service.PatientService;
import com.sunrisedental.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@WebServlet("/api/v1/patients/*")
public class PatientController extends HttpServlet {

    private PatientService patientService;

    @Override
    public void init() {
        patientService =
                new PatientService(new JdbcPatientDao());
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        try {
            String path = request.getPathInfo();

            if (path == null || path.equals("/")) {
                searchPatients(request, response);
                return;
            }

            if (path.matches("/\\d+")) {
                long patientId =
                        Long.parseLong(path.substring(1));

                Patient patient =
                        patientService.getById(patientId);

                writePatientResponse(
                        response,
                        HttpServletResponse.SC_OK,
                        "Patient found",
                        patient
                );
                return;
            }

            writeError(
                    response,
                    HttpServletResponse.SC_NOT_FOUND,
                    "Patient API route was not found"
            );

        } catch (ValidationException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (NotFoundException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_NOT_FOUND,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            log("Patient database error", exception);

            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Patient information could not be retrieved"
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
            if (!canManagePatients(request)) {
                writeError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "You do not have permission to manage patients"
                );
                return;
            }

            String path = request.getPathInfo();

            if (path == null || path.equals("/")) {
                createPatient(request, response);
                return;
            }

            if (path.matches("/\\d+/update")) {
                long patientId = extractPatientId(path);

                Patient patient =
                        patientService.update(
                                patientId,
                                createPatientRequest(request)
                        );

                writePatientResponse(
                        response,
                        HttpServletResponse.SC_OK,
                        "Patient updated successfully",
                        patient
                );
                return;
            }

            if (path.matches("/\\d+/status")) {
                updatePatientStatus(request, response, path);
                return;
            }

            writeError(
                    response,
                    HttpServletResponse.SC_NOT_FOUND,
                    "Patient API route was not found"
            );

        } catch (ValidationException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (NotFoundException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_NOT_FOUND,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            log("Patient database error", exception);

            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Patient information could not be saved"
            );
        }
    }

    private void createPatient(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws SQLException, IOException {

        HttpSession session = request.getSession(false);

        long createdBy =
                ((Number) session.getAttribute("userId"))
                        .longValue();

        Patient patient =
                patientService.create(
                        createPatientRequest(request),
                        createdBy
                );

        writePatientResponse(
                response,
                HttpServletResponse.SC_CREATED,
                "Patient registered successfully",
                patient
        );
    }

    private void searchPatients(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws SQLException, IOException {

        String searchTerm =
                request.getParameter("search");

        boolean includeInactive =
                Boolean.parseBoolean(
                        request.getParameter("includeInactive")
                );

        List<Patient> patients =
                patientService.search(
                        searchTerm,
                        includeInactive
                );

        StringBuilder json = new StringBuilder();

        json.append("""
                {
                    "success": true,
                    "count":
                """);

        json.append(patients.size());
        json.append(",\"patients\":[");

        for (int index = 0;
             index < patients.size();
             index++) {

            if (index > 0) {
                json.append(",");
            }

            json.append(patientToJson(
                    patients.get(index)
            ));
        }

        json.append("]}");

        writeJson(
                response,
                HttpServletResponse.SC_OK,
                json.toString()
        );
    }

    private void updatePatientStatus(
            HttpServletRequest request,
            HttpServletResponse response,
            String path
    ) throws SQLException, IOException {

        long patientId = extractPatientId(path);

        String activeValue =
                request.getParameter("active");

        if (!"true".equalsIgnoreCase(activeValue) &&
                !"false".equalsIgnoreCase(activeValue)) {

            throw new ValidationException(
                    "A valid active status is required"
            );
        }

        boolean active =
                Boolean.parseBoolean(activeValue);

        patientService.setActive(patientId, active);

        String message =
                active
                        ? "Patient reactivated successfully"
                        : "Patient deactivated successfully";

        writeJson(
                response,
                HttpServletResponse.SC_OK,
                """
                {
                    "success": true,
                    "message": %s
                }
                """.formatted(JsonUtil.quote(message))
        );
    }

    private PatientRequest createPatientRequest(
            HttpServletRequest request
    ) {
        return new PatientRequest(
                request.getParameter("fullName"),
                request.getParameter("address"),
                request.getParameter("contactNumber"),
                request.getParameter("email"),
                parseOptionalDate(
                        request.getParameter("dateOfBirth")
                ),
                request.getParameter("gender"),
                request.getParameter("allergies"),
                request.getParameter("medicalNotes")
        );
    }

    private LocalDate parseOptionalDate(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value);

        } catch (DateTimeParseException exception) {
            throw new ValidationException(
                    "Enter a valid date of birth"
            );
        }
    }

    private long extractPatientId(String path) {

        try {
            return Long.parseLong(path.split("/")[1]);

        } catch (RuntimeException exception) {
            throw new ValidationException(
                    "Invalid patient ID"
            );
        }
    }

    private boolean canManagePatients(
            HttpServletRequest request
    ) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return false;
        }

        Object role = session.getAttribute("role");

        return "ADMIN".equals(role) ||
                "RECEPTIONIST".equals(role);
    }

    private void writePatientResponse(
            HttpServletResponse response,
            int status,
            String message,
            Patient patient
    ) throws IOException {

        String json = """
                {
                    "success": true,
                    "message": %s,
                    "patient": %s
                }
                """.formatted(
                JsonUtil.quote(message),
                patientToJson(patient)
        );

        writeJson(response, status, json);
    }

    private String patientToJson(Patient patient) {

        return """
                {
                    "patientId": %d,
                    "patientCode": %s,
                    "fullName": %s,
                    "address": %s,
                    "contactNumber": %s,
                    "email": %s,
                    "dateOfBirth": %s,
                    "gender": %s,
                    "allergies": %s,
                    "medicalNotes": %s,
                    "active": %s,
                    "createdBy": %d,
                    "createdAt": %s,
                    "updatedAt": %s
                }
                """.formatted(
                patient.patientId(),
                JsonUtil.quote(patient.patientCode()),
                JsonUtil.quote(patient.fullName()),
                JsonUtil.quote(patient.address()),
                JsonUtil.quote(patient.contactNumber()),
                JsonUtil.quote(patient.email()),
                patient.dateOfBirth() == null
                        ? "null"
                        : JsonUtil.quote(
                        patient.dateOfBirth().toString()
                ),
                JsonUtil.quote(patient.gender()),
                JsonUtil.quote(patient.allergies()),
                JsonUtil.quote(patient.medicalNotes()),
                patient.active(),
                patient.createdBy(),
                patient.createdAt() == null
                        ? "null"
                        : JsonUtil.quote(
                        patient.createdAt().toString()
                ),
                patient.updatedAt() == null
                        ? "null"
                        : JsonUtil.quote(
                        patient.updatedAt().toString()
                )
        );
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {

        String json = """
                {
                    "success": false,
                    "message": %s
                }
                """.formatted(JsonUtil.quote(message));

        writeJson(response, status, json);
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