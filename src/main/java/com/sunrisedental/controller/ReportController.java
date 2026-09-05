package com.sunrisedental.controller;

import com.google.gson.Gson;
import com.sunrisedental.dao.impl.JdbcReportDao;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.ClinicReport;
import com.sunrisedental.service.ReportService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/v1/reports/*")
public class ReportController extends HttpServlet {

    private final Gson gson = new Gson();

    private ReportService reportService;

    @Override
    public void init() {
        reportService = new ReportService(
                new JdbcReportDao()
        );
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        try {
            if (!canViewReports(request)) {
                writeError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "Only administrators can view reports"
                );

                return;
            }

            String path = request.getPathInfo();

            if (path != null
                    && !"/".equals(path)
                    && !"/summary".equals(path)) {

                writeError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "Reporting API route was not found"
                );

                return;
            }

            LocalDate today = LocalDate.now();

            LocalDate fromDate =
                    parseDate(
                            request.getParameter("from"),
                            today.withDayOfMonth(1),
                            "From date"
                    );

            LocalDate toDate =
                    parseDate(
                            request.getParameter("to"),
                            today,
                            "To date"
                    );

            ClinicReport report =
                    reportService.generate(
                            fromDate,
                            toDate
                    );

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put("success", true);
            result.put("report", reportToMap(report));

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
            log(
                    "Reporting database error",
                    exception
            );

            writeError(
                    response,
                    HttpServletResponse
                            .SC_INTERNAL_SERVER_ERROR,
                    "Report information could not be retrieved"
            );
        }
    }

    private Map<String, Object> reportToMap(
            ClinicReport report
    ) {
        Map<String, Object> value =
                new LinkedHashMap<>();

        value.put(
                "fromDate",
                report.fromDate().toString()
        );

        value.put(
                "toDate",
                report.toDate().toString()
        );

        value.put(
                "totalAppointments",
                report.totalAppointments()
        );

        value.put(
                "completedAppointments",
                report.completedAppointments()
        );

        value.put(
                "cancelledAppointments",
                report.cancelledAppointments()
        );

        value.put(
                "newPatients",
                report.newPatients()
        );

        value.put(
                "generatedBills",
                report.generatedBills()
        );

        value.put(
                "billedAmount",
                report.billedAmount()
        );

        value.put(
                "collectedAmount",
                report.collectedAmount()
        );

        value.put(
                "outstandingAmount",
                report.outstandingAmount()
        );

        value.put(
                "appointmentStatuses",
                appointmentStatusesToList(report)
        );

        value.put(
                "treatments",
                treatmentsToList(report)
        );

        value.put(
                "dentists",
                dentistsToList(report)
        );

        return value;
    }

    private List<Map<String, Object>>
    appointmentStatusesToList(
            ClinicReport report
    ) {
        List<Map<String, Object>> values =
                new ArrayList<>();

        for (
                ClinicReport.AppointmentStatusTotal total
                : report.appointmentStatuses()
        ) {
            Map<String, Object> value =
                    new LinkedHashMap<>();

            value.put("status", total.status());

            value.put(
                    "appointmentCount",
                    total.appointmentCount()
            );

            values.add(value);
        }

        return values;
    }

    private List<Map<String, Object>>
    treatmentsToList(
            ClinicReport report
    ) {
        List<Map<String, Object>> values =
                new ArrayList<>();

        for (
                ClinicReport.TreatmentTotal total
                : report.treatments()
        ) {
            Map<String, Object> value =
                    new LinkedHashMap<>();

            value.put(
                    "treatmentName",
                    total.treatmentName()
            );

            value.put(
                    "appointmentCount",
                    total.appointmentCount()
            );

            value.put(
                    "billedAmount",
                    total.billedAmount()
            );

            values.add(value);
        }

        return values;
    }

    private List<Map<String, Object>>
    dentistsToList(
            ClinicReport report
    ) {
        List<Map<String, Object>> values =
                new ArrayList<>();

        for (
                ClinicReport.DentistTotal total
                : report.dentists()
        ) {
            Map<String, Object> value =
                    new LinkedHashMap<>();

            value.put(
                    "dentistName",
                    total.dentistName()
            );

            value.put(
                    "appointmentCount",
                    total.appointmentCount()
            );

            value.put(
                    "completedCount",
                    total.completedCount()
            );

            values.add(value);
        }

        return values;
    }

    private LocalDate parseDate(
            String value,
            LocalDate defaultValue,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return LocalDate.parse(value.trim());

        } catch (DateTimeParseException exception) {
            throw new ValidationException(
                    fieldName + " must be a valid date"
            );
        }
    }

    private boolean canViewReports(
            HttpServletRequest request
    ) {
        HttpSession session =
                request.getSession(false);

        if (session == null) {
            return false;
        }

        return "ADMIN".equals(
                session.getAttribute("role")
        );
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

        response.setHeader(
                "Cache-Control",
                "no-store"
        );

        response.getWriter().write(json);
    }
}