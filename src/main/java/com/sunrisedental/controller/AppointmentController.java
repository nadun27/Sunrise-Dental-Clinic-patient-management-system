package com.sunrisedental.controller;

import com.sunrisedental.dao.impl.JdbcAppointmentDao;
import com.sunrisedental.dao.impl.JdbcDentistDao;
import com.sunrisedental.dao.impl.JdbcPatientDao;
import com.sunrisedental.dao.impl.JdbcTreatmentDao;
import com.sunrisedental.dto.request.AppointmentRequest;
import com.sunrisedental.exception.NotFoundException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.Dentist;
import com.sunrisedental.model.Treatment;
import com.sunrisedental.service.AppointmentService;
import com.sunrisedental.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@WebServlet("/api/v1/appointments/*")
public class AppointmentController extends HttpServlet {
    private AppointmentService service;

    @Override
    public void init() {
        service = new AppointmentService(
                new JdbcAppointmentDao(), new JdbcPatientDao(),
                new JdbcDentistDao(), new JdbcTreatmentDao());
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            String path = request.getPathInfo();
            if ("/options".equals(path)) {
                writeOptions(response);
            } else if (path == null || "/".equals(path)) {
                writeSearch(request, response);
            } else if (path.matches("/\\d+")) {
                Appointment appointment = service.getById(
                        Long.parseLong(path.substring(1)));
                writeAppointmentResponse(response, HttpServletResponse.SC_OK,
                        "Appointment found", appointment);
            } else {
                writeError(response, HttpServletResponse.SC_NOT_FOUND,
                        "Appointment API route was not found");
            }
        } catch (ValidationException exception) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage());
        } catch (NotFoundException exception) {
            writeError(response, HttpServletResponse.SC_NOT_FOUND,
                    exception.getMessage());
        } catch (SQLException exception) {
            log("Appointment database error", exception);
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Appointment information could not be retrieved");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        request.setCharacterEncoding("UTF-8");
        try {
            if (!canManageAppointments(request)) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        "You do not have permission to manage appointments");
                return;
            }

            String path = request.getPathInfo();
            if (path == null || "/".equals(path)) {
                long createdBy = ((Number) request.getSession(false)
                        .getAttribute("userId")).longValue();
                Appointment appointment = service.create(
                        createRequest(request), createdBy);
                writeAppointmentResponse(response, HttpServletResponse.SC_CREATED,
                        "Appointment registered successfully", appointment);
            } else if (path.matches("/\\d+/update")) {
                Appointment appointment = service.reschedule(
                        extractId(path), createRequest(request),
                        parsePositiveInt(request.getParameter("versionNumber"),
                                "Appointment version"));
                writeAppointmentResponse(response, HttpServletResponse.SC_OK,
                        "Appointment updated successfully", appointment);
            } else if (path.matches("/\\d+/status")) {
                Appointment appointment = service.changeStatus(
                        extractId(path), request.getParameter("status"),
                        request.getParameter("cancellationReason"),
                        parsePositiveInt(request.getParameter("versionNumber"),
                                "Appointment version"));
                writeAppointmentResponse(response, HttpServletResponse.SC_OK,
                        "Appointment status updated successfully", appointment);
            } else {
                writeError(response, HttpServletResponse.SC_NOT_FOUND,
                        "Appointment API route was not found");
            }
        } catch (ValidationException exception) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage());
        } catch (NotFoundException exception) {
            writeError(response, HttpServletResponse.SC_NOT_FOUND,
                    exception.getMessage());
        } catch (SQLException exception) {
            log("Appointment database error", exception);
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Appointment information could not be saved");
        }
    }

    private AppointmentRequest createRequest(HttpServletRequest request) {
        return new AppointmentRequest(
                parsePositiveLong(request.getParameter("patientId"), "Patient"),
                parsePositiveLong(request.getParameter("dentistId"), "Dentist"),
                parsePositiveLong(request.getParameter("treatmentTypeId"), "Treatment"),
                parseDateTime(request.getParameter("startAt")),
                request.getParameter("patientReason"),
                request.getParameter("internalNotes")
        );
    }

    private void writeSearch(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        LocalDate date = null;
        String dateValue = request.getParameter("date");
        if (dateValue != null && !dateValue.isBlank()) {
            try {
                date = LocalDate.parse(dateValue);
            } catch (DateTimeParseException exception) {
                throw new ValidationException("Enter a valid appointment date");
            }
        }

        List<Appointment> appointments = service.search(
                request.getParameter("search"), date,
                request.getParameter("status"));
        StringBuilder json = new StringBuilder(
                "{\"success\":true,\"count\":")
                .append(appointments.size()).append(",\"appointments\":[");
        for (int index = 0; index < appointments.size(); index++) {
            if (index > 0) json.append(',');
            json.append(appointmentToJson(appointments.get(index)));
        }
        json.append("]}");
        writeJson(response, HttpServletResponse.SC_OK, json.toString());
    }

    private void writeOptions(HttpServletResponse response)
            throws SQLException, IOException {
        List<Dentist> dentists = service.getActiveDentists();
        List<Treatment> treatments = service.getActiveTreatments();
        StringBuilder json = new StringBuilder(
                "{\"success\":true,\"dentists\":[");

        for (int index = 0; index < dentists.size(); index++) {
            if (index > 0) json.append(',');
            Dentist dentist = dentists.get(index);
            json.append("""
                    {"dentistId":%d,"fullName":%s,"registrationNumber":%s,
                     "specialization":%s,"consultationFee":%s}
                    """.formatted(
                    dentist.dentistId(), JsonUtil.quote(dentist.fullName()),
                    JsonUtil.quote(dentist.registrationNumber()),
                    JsonUtil.quote(dentist.specialization()),
                    dentist.consultationFee().toPlainString()));
        }

        json.append("],\"treatments\":[");
        for (int index = 0; index < treatments.size(); index++) {
            if (index > 0) json.append(',');
            Treatment treatment = treatments.get(index);
            json.append("""
                    {"treatmentTypeId":%d,"treatmentCode":%s,"treatmentName":%s,
                     "description":%s,"defaultFee":%s,"durationMinutes":%d}
                    """.formatted(
                    treatment.treatmentTypeId(),
                    JsonUtil.quote(treatment.treatmentCode()),
                    JsonUtil.quote(treatment.treatmentName()),
                    JsonUtil.quote(treatment.description()),
                    treatment.defaultFee().toPlainString(),
                    treatment.defaultDurationMinutes()));
        }
        json.append("]}");
        writeJson(response, HttpServletResponse.SC_OK, json.toString());
    }

    private String appointmentToJson(Appointment appointment) {
        return """
                {"appointmentId":%d,"appointmentNumber":%s,
                 "patientId":%d,"patientCode":%s,"patientName":%s,
                 "patientContact":%s,"dentistId":%d,"dentistName":%s,
                 "treatmentTypeId":%d,"treatmentName":%s,
                 "treatmentFee":%s,"durationMinutes":%d,
                 "consultationFee":%s,"startAt":%s,"endAt":%s,
                 "status":%s,"patientReason":%s,"internalNotes":%s,
                 "cancellationReason":%s,"versionNumber":%d}
                """.formatted(
                appointment.appointmentId(),
                JsonUtil.quote(appointment.appointmentNumber()),
                appointment.patientId(), JsonUtil.quote(appointment.patientCode()),
                JsonUtil.quote(appointment.patientName()),
                JsonUtil.quote(appointment.patientContact()),
                appointment.dentistId(), JsonUtil.quote(appointment.dentistName()),
                appointment.treatmentTypeId(),
                JsonUtil.quote(appointment.treatmentName()),
                appointment.treatmentFee().toPlainString(),
                appointment.durationMinutes(),
                appointment.consultationFee().toPlainString(),
                JsonUtil.quote(appointment.startAt().toString()),
                JsonUtil.quote(appointment.endAt().toString()),
                JsonUtil.quote(appointment.status().name()),
                JsonUtil.quote(appointment.patientReason()),
                JsonUtil.quote(appointment.internalNotes()),
                JsonUtil.quote(appointment.cancellationReason()),
                appointment.versionNumber());
    }

    private void writeAppointmentResponse(
            HttpServletResponse response, int status,
            String message, Appointment appointment
    ) throws IOException {
        writeJson(response, status,
                "{\"success\":true,\"message\":" + JsonUtil.quote(message)
                        + ",\"appointment\":" + appointmentToJson(appointment) + "}");
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Appointment date and time are required");
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ValidationException("Enter a valid appointment date and time");
        }
    }

    private long parsePositiveLong(String value, String field) {
        try {
            long result = Long.parseLong(value);
            if (result <= 0) throw new NumberFormatException();
            return result;
        } catch (RuntimeException exception) {
            throw new ValidationException("Select a valid " + field.toLowerCase());
        }
    }

    private int parsePositiveInt(String value, String field) {
        try {
            int result = Integer.parseInt(value);
            if (result <= 0) throw new NumberFormatException();
            return result;
        } catch (RuntimeException exception) {
            throw new ValidationException(field + " is invalid");
        }
    }

    private long extractId(String path) {
        return parsePositiveLong(path.split("/")[1], "appointment");
    }

    private boolean canManageAppointments(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        Object role = session.getAttribute("role");
        return "ADMIN".equals(role) || "RECEPTIONIST".equals(role);
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        writeJson(response, status,
                "{\"success\":false,\"message\":" + JsonUtil.quote(message) + "}");
    }

    private void writeJson(HttpServletResponse response, int status, String json)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }
}