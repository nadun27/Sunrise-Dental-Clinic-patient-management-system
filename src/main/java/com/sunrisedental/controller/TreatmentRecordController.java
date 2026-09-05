package com.sunrisedental.controller;

import com.sunrisedental.dao.impl.JdbcAppointmentDao;
import com.sunrisedental.dao.impl.JdbcDentistDao;
import com.sunrisedental.dao.impl.JdbcTreatmentRecordDao;
import com.sunrisedental.dto.request.TreatmentRecordRequest;
import com.sunrisedental.exception.NotFoundException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.Role;
import com.sunrisedental.model.TreatmentRecord;
import com.sunrisedental.service.TreatmentRecordService;
import com.sunrisedental.util.JsonUtil;
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

@WebServlet("/api/v1/treatment-records/*")
public class TreatmentRecordController
        extends HttpServlet {

    private TreatmentRecordService service;

    @Override
    public void init() {
        service = new TreatmentRecordService(
                new JdbcTreatmentRecordDao(),
                new JdbcAppointmentDao(),
                new JdbcDentistDao()
        );
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        try {
            SessionUser user = requireClinicalUser(request);
            String path = request.getPathInfo();

            if (path == null || "/".equals(path)) {
                writeSearch(response, request, user);

            } else if ("/active".equals(path)) {
                writeActiveSessions(response, user);

            } else if (path.matches(
                    "/appointment/\\d+"
            )) {
                long appointmentId = parsePositiveLong(
                        path.substring(
                                "/appointment/".length()
                        ),
                        "Appointment"
                );

                TreatmentRecord record =
                        service.getByAppointmentId(
                                appointmentId,
                                user.userId(),
                                user.role()
                        );

                writeRecordResponse(
                        response,
                        HttpServletResponse.SC_OK,
                        "Treatment record found",
                        record
                );

            } else if (path.matches("/\\d+")) {
                long recordId = parsePositiveLong(
                        path.substring(1),
                        "Treatment record"
                );

                TreatmentRecord record = service.getById(
                        recordId,
                        user.userId(),
                        user.role()
                );

                writeRecordResponse(
                        response,
                        HttpServletResponse.SC_OK,
                        "Treatment record found",
                        record
                );

            } else {
                writeError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "Treatment record API route was not found"
                );
            }

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

        } catch (ForbiddenException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            log("Treatment record database error", exception);

            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Treatment information could not be retrieved"
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
            SessionUser user = requireClinicalUser(request);
            String path = request.getPathInfo();

            if (path != null && path.matches(
                    "/\\d+/start"
            )) {
                long appointmentId = parsePositiveLong(
                        path.split("/")[1],
                        "Appointment"
                );

                Appointment appointment = service.startSession(
                        appointmentId,
                        parsePositiveInt(
                                request.getParameter(
                                        "versionNumber"
                                ),
                                "Appointment version"
                        ),
                        user.userId(),
                        user.role()
                );

                writeAppointmentResponse(
                        response,
                        HttpServletResponse.SC_OK,
                        "Treatment session started successfully",
                        appointment
                );

            } else if ("/complete".equals(path)) {
                TreatmentRecordRequest recordRequest =
                        new TreatmentRecordRequest(
                                parsePositiveLong(
                                        request.getParameter(
                                                "appointmentId"
                                        ),
                                        "Appointment"
                                ),
                                parsePositiveInt(
                                        request.getParameter(
                                                "versionNumber"
                                        ),
                                        "Appointment version"
                                ),
                                request.getParameter("diagnosis"),
                                request.getParameter(
                                        "treatmentPerformed"
                                ),
                                request.getParameter(
                                        "clinicalNotes"
                                ),
                                request.getParameter("prescription"),
                                parseOptionalDate(
                                        request.getParameter(
                                                "followUpDate"
                                        )
                                )
                        );

                TreatmentRecord record =
                        service.completeSession(
                                recordRequest,
                                user.userId(),
                                user.role()
                        );

                writeRecordResponse(
                        response,
                        HttpServletResponse.SC_CREATED,
                        "Treatment session completed successfully",
                        record
                );

            } else {
                writeError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "Treatment record API route was not found"
                );
            }

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

        } catch (ForbiddenException exception) {
            writeError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            log("Treatment record database error", exception);

            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Treatment session could not be saved"
            );
        }
    }

    private void writeSearch(
            HttpServletResponse response,
            HttpServletRequest request,
            SessionUser user
    ) throws SQLException, IOException {

        List<TreatmentRecord> records = service.search(
                request.getParameter("search"),
                user.userId(),
                user.role()
        );

        StringBuilder json = new StringBuilder(
                "{\"success\":true,\"count\":"
        )
                .append(records.size())
                .append(",\"records\":[");

        for (int index = 0; index < records.size(); index++) {
            if (index > 0) {
                json.append(',');
            }

            json.append(recordToJson(records.get(index)));
        }

        json.append("]}");

        writeJson(
                response,
                HttpServletResponse.SC_OK,
                json.toString()
        );
    }

    private void writeActiveSessions(
            HttpServletResponse response,
            SessionUser user
    ) throws SQLException, IOException {

        List<Appointment> appointments =
                service.getActiveSessions(
                        user.userId(),
                        user.role()
                );

        StringBuilder json = new StringBuilder(
                "{\"success\":true,\"count\":"
        )
                .append(appointments.size())
                .append(",\"appointments\":[");

        for (
                int index = 0;
                index < appointments.size();
                index++
        ) {
            if (index > 0) {
                json.append(',');
            }

            json.append(
                    appointmentToJson(
                            appointments.get(index)
                    )
            );
        }

        json.append("]}");

        writeJson(
                response,
                HttpServletResponse.SC_OK,
                json.toString()
        );
    }

    private String appointmentToJson(
            Appointment appointment
    ) {
        return """
                {
                    "appointmentId":%d,
                    "appointmentNumber":%s,
                    "patientCode":%s,
                    "patientName":%s,
                    "dentistId":%d,
                    "dentistName":%s,
                    "treatmentName":%s,
                    "startAt":%s,
                    "status":%s,
                    "versionNumber":%d
                }
                """.formatted(
                appointment.appointmentId(),
                JsonUtil.quote(
                        appointment.appointmentNumber()
                ),
                JsonUtil.quote(appointment.patientCode()),
                JsonUtil.quote(appointment.patientName()),
                appointment.dentistId(),
                JsonUtil.quote(appointment.dentistName()),
                JsonUtil.quote(appointment.treatmentName()),
                JsonUtil.quote(
                        appointment.startAt().toString()
                ),
                JsonUtil.quote(appointment.status().name()),
                appointment.versionNumber()
        );
    }

    private String recordToJson(
            TreatmentRecord record
    ) {
        return """
                {
                    "treatmentRecordId":%d,
                    "appointmentId":%d,
                    "appointmentNumber":%s,
                    "patientCode":%s,
                    "patientName":%s,
                    "dentistId":%d,
                    "dentistName":%s,
                    "treatmentName":%s,
                    "diagnosis":%s,
                    "treatmentPerformed":%s,
                    "clinicalNotes":%s,
                    "prescription":%s,
                    "followUpDate":%s,
                    "createdAt":%s,
                    "updatedAt":%s
                }
                """.formatted(
                record.treatmentRecordId(),
                record.appointmentId(),
                JsonUtil.quote(record.appointmentNumber()),
                JsonUtil.quote(record.patientCode()),
                JsonUtil.quote(record.patientName()),
                record.dentistId(),
                JsonUtil.quote(record.dentistName()),
                JsonUtil.quote(record.treatmentName()),
                JsonUtil.quote(record.diagnosis()),
                JsonUtil.quote(record.treatmentPerformed()),
                JsonUtil.quote(record.clinicalNotes()),
                JsonUtil.quote(record.prescription()),
                JsonUtil.quote(
                        record.followUpDate() == null
                                ? null
                                : record.followUpDate().toString()
                ),
                JsonUtil.quote(
                        record.createdAt() == null
                                ? null
                                : record.createdAt().toString()
                ),
                JsonUtil.quote(
                        record.updatedAt() == null
                                ? null
                                : record.updatedAt().toString()
                )
        );
    }

    private void writeRecordResponse(
            HttpServletResponse response,
            int status,
            String message,
            TreatmentRecord record
    ) throws IOException {

        writeJson(
                response,
                status,
                "{\"success\":true,\"message\":" +
                        JsonUtil.quote(message) +
                        ",\"record\":" +
                        recordToJson(record) +
                        "}"
        );
    }

    private void writeAppointmentResponse(
            HttpServletResponse response,
            int status,
            String message,
            Appointment appointment
    ) throws IOException {

        writeJson(
                response,
                status,
                "{\"success\":true,\"message\":" +
                        JsonUtil.quote(message) +
                        ",\"appointment\":" +
                        appointmentToJson(appointment) +
                        "}"
        );
    }

    private SessionUser requireClinicalUser(
            HttpServletRequest request
    ) {
        HttpSession session = request.getSession(false);

        if (session == null ||
                session.getAttribute("userId") == null) {

            throw new ForbiddenException(
                    "Authentication required"
            );
        }

        long userId = ((Number) session.getAttribute(
                "userId"
        )).longValue();

        Role role;

        try {
            role = Role.valueOf(String.valueOf(
                    session.getAttribute("role")
            ));
        } catch (IllegalArgumentException exception) {
            throw new ForbiddenException(
                    "Your user role is invalid"
            );
        }

        if (role != Role.ADMIN && role != Role.DENTIST) {
            throw new ForbiddenException(
                    "Only an administrator or dentist can " +
                            "access clinical treatment records"
            );
        }

        return new SessionUser(userId, role);
    }

    private long parsePositiveLong(
            String value,
            String fieldName
    ) {
        try {
            long result = Long.parseLong(value);

            if (result <= 0) {
                throw new NumberFormatException();
            }

            return result;

        } catch (RuntimeException exception) {
            throw new ValidationException(
                    fieldName + " must be valid"
            );
        }
    }

    private int parsePositiveInt(
            String value,
            String fieldName
    ) {
        try {
            int result = Integer.parseInt(value);

            if (result <= 0) {
                throw new NumberFormatException();
            }

            return result;

        } catch (RuntimeException exception) {
            throw new ValidationException(
                    fieldName + " is invalid"
            );
        }
    }

    private LocalDate parseOptionalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value);

        } catch (DateTimeParseException exception) {
            throw new ValidationException(
                    "Enter a valid follow-up date"
            );
        }
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {

        writeJson(
                response,
                status,
                "{\"success\":false,\"message\":" +
                        JsonUtil.quote(message) +
                        "}"
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
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(json);
    }

    private record SessionUser(long userId, Role role) {
    }

    private static class ForbiddenException
            extends RuntimeException {

        private ForbiddenException(String message) {
            super(message);
        }
    }
}
