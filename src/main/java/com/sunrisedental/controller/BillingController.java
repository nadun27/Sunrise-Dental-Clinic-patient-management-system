package com.sunrisedental.controller;

import com.sunrisedental.dao.impl.JdbcAppointmentDao;
import com.sunrisedental.dao.impl.JdbcBillDao;
import com.sunrisedental.dto.request.BillRequest;
import com.sunrisedental.exception.NotFoundException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Bill;
import com.sunrisedental.service.BillingService;
import com.sunrisedental.service.billing.StandardBillingStrategy;
import com.sunrisedental.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/api/v1/bills/*")
public class BillingController extends HttpServlet {

    private BillingService service;

    @Override
    public void init() {
        service = new BillingService(
                new JdbcBillDao(),
                new JdbcAppointmentDao(),

                new StandardBillingStrategy(
                        BigDecimal.ZERO
                )
        );
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        try {
            String path = request.getPathInfo();

            if (path == null || "/".equals(path)) {
                writeSearch(request, response);

            } else if (path.matches(
                    "/appointment/\\d+"
            )) {
                long appointmentId =
                        parsePositiveLong(
                                path.substring(
                                        "/appointment/".length()
                                ),
                                "Appointment"
                        );

                Bill bill =
                        service.getByAppointmentId(
                                appointmentId
                        );

                writeBillResponse(
                        response,
                        HttpServletResponse.SC_OK,
                        "Bill found",
                        bill
                );

            } else if (path.matches("/\\d+")) {
                long billId =
                        parsePositiveLong(
                                path.substring(1),
                                "Bill"
                        );

                Bill bill =
                        service.getById(billId);

                writeBillResponse(
                        response,
                        HttpServletResponse.SC_OK,
                        "Bill found",
                        bill
                );

            } else {
                writeError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "Billing API route was not found"
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

        } catch (SQLException exception) {
            log("Billing database error", exception);

            writeError(
                    response,
                    HttpServletResponse
                            .SC_INTERNAL_SERVER_ERROR,
                    "Billing information could not be retrieved"
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
            if (!canManageBilling(request)) {
                writeError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "You do not have permission " +
                                "to create bills"
                );

                return;
            }

            String path = request.getPathInfo();

            if (path == null || "/".equals(path)) {
                HttpSession session =
                        request.getSession(false);

                long createdBy =
                        ((Number) session.getAttribute(
                                "userId"
                        )).longValue();

                BillRequest billRequest =
                        new BillRequest(
                                parsePositiveLong(
                                        request.getParameter(
                                                "appointmentId"
                                        ),
                                        "Appointment"
                                ),

                                parseMoney(
                                        request.getParameter(
                                                "discountAmount"
                                        ),
                                        "Discount amount"
                                ),

                                request.getParameter(
                                        "discountReason"
                                )
                        );

                Bill bill =
                        service.create(
                                billRequest,
                                createdBy
                        );

                writeBillResponse(
                        response,
                        HttpServletResponse.SC_CREATED,
                        "Bill created successfully",
                        bill
                );

            } else {
                writeError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "Billing API route was not found"
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

        } catch (SQLException exception) {
            log("Billing database error", exception);

            writeError(
                    response,
                    HttpServletResponse
                            .SC_INTERNAL_SERVER_ERROR,
                    "Bill could not be created"
            );
        }
    }

    private void writeSearch(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws SQLException, IOException {

        List<Bill> bills =
                service.search(
                        request.getParameter("search"),
                        request.getParameter("status")
                );

        StringBuilder json =
                new StringBuilder(
                        "{\"success\":true,\"count\":"
                )
                        .append(bills.size())
                        .append(",\"bills\":[");

        for (
                int index = 0;
                index < bills.size();
                index++
        ) {
            if (index > 0) {
                json.append(',');
            }

            json.append(
                    billToJson(bills.get(index))
            );
        }

        json.append("]}");

        writeJson(
                response,
                HttpServletResponse.SC_OK,
                json.toString()
        );
    }

    private String billToJson(Bill bill) {
        return """
                {
                    "billId":%d,
                    "invoiceNumber":%s,
                    "appointmentId":%d,
                    "appointmentNumber":%s,
                    "patientName":%s,
                    "consultationFee":%s,
                    "treatmentFee":%s,
                    "discountAmount":%s,
                    "taxAmount":%s,
                    "totalAmount":%s,
                    "paymentStatus":%s,
                    "discountReason":%s,
                    "createdBy":%d,
                    "createdAt":%s,
                    "updatedAt":%s
                }
                """.formatted(
                bill.billId(),
                JsonUtil.quote(
                        bill.invoiceNumber()
                ),

                bill.appointmentId(),
                JsonUtil.quote(
                        bill.appointmentNumber()
                ),

                JsonUtil.quote(
                        bill.patientName()
                ),

                bill.consultationFee()
                        .toPlainString(),

                bill.treatmentFee()
                        .toPlainString(),

                bill.discountAmount()
                        .toPlainString(),

                bill.taxAmount()
                        .toPlainString(),

                bill.totalAmount()
                        .toPlainString(),

                JsonUtil.quote(
                        bill.paymentStatus().name()
                ),

                JsonUtil.quote(
                        bill.discountReason()
                ),

                bill.createdBy(),

                JsonUtil.quote(
                        bill.createdAt() == null
                                ? null
                                : bill.createdAt()
                                .toString()
                ),

                JsonUtil.quote(
                        bill.updatedAt() == null
                                ? null
                                : bill.updatedAt()
                                .toString()
                )
        );
    }

    private void writeBillResponse(
            HttpServletResponse response,
            int status,
            String message,
            Bill bill
    ) throws IOException {

        writeJson(
                response,
                status,

                "{\"success\":true,\"message\":" +
                        JsonUtil.quote(message) +
                        ",\"bill\":" +
                        billToJson(bill) +
                        "}"
        );
    }

    private BigDecimal parseMoney(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(value.trim());

        } catch (NumberFormatException exception) {
            throw new ValidationException(
                    fieldName + " must be a valid amount"
            );
        }
    }

    private long parsePositiveLong(
            String value,
            String fieldName
    ) {
        try {
            long result =
                    Long.parseLong(value);

            if (result <= 0) {
                throw new NumberFormatException();
            }

            return result;

        } catch (RuntimeException exception) {
            throw new ValidationException(
                    "Select a valid " +
                            fieldName.toLowerCase()
            );
        }
    }

    private boolean canManageBilling(
            HttpServletRequest request
    ) {
        HttpSession session =
                request.getSession(false);

        if (session == null) {
            return false;
        }

        Object role =
                session.getAttribute("role");

        return "ADMIN".equals(role) ||
                "CASHIER".equals(role);
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

        response.setContentType(
                "application/json"
        );

        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(json);
    }
}