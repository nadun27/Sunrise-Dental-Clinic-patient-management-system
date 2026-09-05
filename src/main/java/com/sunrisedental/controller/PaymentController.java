package com.sunrisedental.controller;

import com.sunrisedental.dao.impl.JdbcBillDao;
import com.sunrisedental.dao.impl.JdbcPaymentDao;
import com.sunrisedental.dto.request.PaymentRequest;
import com.sunrisedental.exception.NotFoundException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Payment;
import com.sunrisedental.service.PaymentService;
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

@WebServlet("/api/v1/payments/*")
public class PaymentController extends HttpServlet {

    private PaymentService service;

    @Override
    public void init() {
        service = new PaymentService(
                new JdbcPaymentDao(),
                new JdbcBillDao()
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
                long billId = parsePositiveLong(
                        request.getParameter("billId"),
                        "Bill"
                );

                List<Payment> payments =
                        service.getForBill(billId);

                writePaymentList(
                        response,
                        billId,
                        payments
                );

            } else if (path.matches("/\\d+")) {
                long paymentId = parsePositiveLong(
                        path.substring(1),
                        "Payment"
                );

                writePaymentResponse(
                        response,
                        HttpServletResponse.SC_OK,
                        "Payment found",
                        service.getById(paymentId)
                );

            } else {
                writeError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "Payment API route was not found"
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
            log("Payment database error", exception);

            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Payment information could not be retrieved"
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
            if (!canManagePayments(request)) {
                writeError(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "You do not have permission " +
                                "to record payments"
                );

                return;
            }

            String path = request.getPathInfo();

            if (path != null && !"/".equals(path)) {
                writeError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        "Payment API route was not found"
                );

                return;
            }

            HttpSession session =
                    request.getSession(false);

            long receivedBy =
                    ((Number) session.getAttribute(
                            "userId"
                    )).longValue();

            PaymentRequest paymentRequest =
                    new PaymentRequest(
                            parsePositiveLong(
                                    request.getParameter(
                                            "billId"
                                    ),
                                    "Bill"
                            ),
                            parseMoney(
                                    request.getParameter(
                                            "amount"
                                    )
                            ),
                            request.getParameter(
                                    "paymentMethod"
                            ),
                            request.getParameter(
                                    "referenceNumber"
                            )
                    );

            Payment payment = service.record(
                    paymentRequest,
                    receivedBy
            );

            writePaymentResponse(
                    response,
                    HttpServletResponse.SC_CREATED,
                    "Payment recorded successfully",
                    payment
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
            log("Payment database error", exception);

            writeError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Payment could not be recorded"
            );
        }
    }

    private void writePaymentList(
            HttpServletResponse response,
            long billId,
            List<Payment> payments
    ) throws IOException, SQLException {

        StringBuilder json = new StringBuilder(
                "{\"success\":true,\"billId\":"
        )
                .append(billId)
                .append(",\"count\":")
                .append(payments.size())
                .append(",\"totalPaid\":")
                .append(
                        service.totalPaid(billId)
                                .toPlainString()
                )
                .append(",\"remainingBalance\":")
                .append(
                        service.remainingBalance(billId)
                                .toPlainString()
                )
                .append(",\"payments\":[");

        for (int index = 0; index < payments.size(); index++) {
            if (index > 0) {
                json.append(',');
            }

            json.append(
                    paymentToJson(payments.get(index))
            );
        }

        json.append("]}");

        writeJson(
                response,
                HttpServletResponse.SC_OK,
                json.toString()
        );
    }

    private String paymentToJson(Payment payment) {
        return """
                {
                    "paymentId":%d,
                    "receiptNumber":%s,
                    "billId":%d,
                    "invoiceNumber":%s,
                    "patientName":%s,
                    "amount":%s,
                    "paymentMethod":%s,
                    "referenceNumber":%s,
                    "paidAt":%s,
                    "receivedBy":%d
                }
                """.formatted(
                payment.paymentId(),
                JsonUtil.quote(payment.receiptNumber()),
                payment.billId(),
                JsonUtil.quote(payment.invoiceNumber()),
                JsonUtil.quote(payment.patientName()),
                payment.amount().toPlainString(),
                JsonUtil.quote(
                        payment.paymentMethod().name()
                ),
                JsonUtil.quote(payment.referenceNumber()),
                JsonUtil.quote(
                        payment.paidAt() == null
                                ? null
                                : payment.paidAt().toString()
                ),
                payment.receivedBy()
        );
    }

    private void writePaymentResponse(
            HttpServletResponse response,
            int status,
            String message,
            Payment payment
    ) throws IOException {

        writeJson(
                response,
                status,
                "{\"success\":true,\"message\":" +
                        JsonUtil.quote(message) +
                        ",\"payment\":" +
                        paymentToJson(payment) +
                        "}"
        );
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    "Payment amount is required"
            );
        }

        try {
            return new BigDecimal(value.trim());

        } catch (NumberFormatException exception) {
            throw new ValidationException(
                    "Payment amount must be valid"
            );
        }
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

    private boolean canManagePayments(
            HttpServletRequest request
    ) {
        HttpSession session =
                request.getSession(false);

        if (session == null) {
            return false;
        }

        Object role = session.getAttribute("role");

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
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }
}
