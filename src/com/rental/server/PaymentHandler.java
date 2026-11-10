package com.rental.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import com.rental.booking.Rental;
import com.rental.common.InvalidBookingException;
import com.rental.fine.Fine;
import com.rental.payment.Payment;
import com.rental.user.Customer;
import com.rental.user.User;
import com.rental.vehicle.Vehicle;

import static com.rental.server.ApiUtil.*;
import static com.rental.server.AppData.*;

/**
 * PAYMENT MODULE endpoints.
 *   GET  /api/payments                    (read: admin all, customer own)
 *   GET  /api/payments/{id}/receipt       (read receipt text)
 *   POST /api/payments                    (create a payment for a booking, admin)
 *   POST /api/payments/update             (edit method/amount, mark REFUNDED / VOID / PAID, admin)
 *   POST /api/payments/delete             (delete, admin)
 */
final class PaymentHandler {

    private PaymentHandler() { }

    static void handle(HttpExchange ex) throws IOException {
        String path = path(ex);
        switch (path) {
            case "/api/payments":
                if (ex.getRequestMethod().equals("GET")) list(ex);
                else if (ex.getRequestMethod().equals("POST")) create(ex);
                else sendJson(ex, 405, errorJson("Method not allowed."));
                break;
            case "/api/payments/update": update(ex); break;
            case "/api/payments/delete": delete(ex); break;
            default:
                if (path.endsWith("/receipt") && ex.getRequestMethod().equals("GET")) receipt(ex, path);
                else notFound(ex);
        }
    }

    // ---------- read ----------
    private static void list(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) return;
        StringBuilder sb = new StringBuilder("[");
        for (Payment p : paymentList.getAll()) {
            if (!isAdmin(user) && !user.getId().equals(customerIdOf(p))) continue;
            if (sb.length() > 1) sb.append(",");
            sb.append(paymentJson(p));
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    private static void receipt(HttpExchange ex, String path) throws IOException {
        User user = authenticate(ex);
        if (user == null) return;
        String[] parts = path.split("/"); // "", "api", "payments", "{id}", "receipt"
        String paymentId = parts.length == 5 ? parts[3] : null;

        Payment payment = paymentList.findById(paymentId);
        Rental rental = payment == null ? null : rentalList.findById(payment.getRentalId());
        if (payment == null || rental == null) {
            sendJson(ex, 404, errorJson("Payment not found."));
            return;
        }
        if (!isAdmin(user) && !user.getId().equals(rental.getCustomerId())) {
            sendJson(ex, 403, errorJson("You can only view your own receipts."));
            return;
        }
        Customer customer = userList.findCustomerById(rental.getCustomerId());
        Vehicle vehicle = vehicleList.findById(rental.getVehicleId());
        String text = buildReceipt(payment, rental, customer, vehicle);
        sendJson(ex, 200, "{\"success\":true,\"receipt\":\"" + escMultiline(text) + "\"}");
    }

    // ---------- create ----------
    private static void create(HttpExchange ex) throws IOException {
        if (requireAdmin(ex) == null) return;
        Map<String, String> form = parseForm(readBody(ex));
        String rentalId = form.getOrDefault("rentalId", form.get("bookingId"));
        try {
            Rental rental = rentalList.findById(rentalId);
            if (rental == null) throw new InvalidBookingException("Rental " + rentalId + " not found.");
            for (Payment existing : paymentList.getByRental(rental.getId())) {
                if (existing.getType().equals("RENTAL") && existing.getStatus().equals("PAID")) {
                    sendJson(ex, 400, errorJson("This booking already has a paid rental payment.")); return;
                }
            }
            Payment p = paymentList.makePayment(rental.getId(), form.getOrDefault("method", "Cash"), rentalList);
            paymentList.saveToFile();
            sendJson(ex, 201, "{\"success\":true,\"payment\":" + paymentJson(p) + "}");
        } catch (InvalidBookingException e) {
            sendJson(ex, 400, errorJson(e.getMessage()));
        }
    }

    // ---------- update ----------
    private static void update(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        if (requireAdmin(ex) == null) return;
        Map<String, String> form = parseForm(readBody(ex));

        Payment p = paymentList.findById(form.get("id"));
        if (p == null) { sendJson(ex, 404, errorJson("Payment not found.")); return; }

        Double amount = null;
        if (!trim(form.get("amount")).isEmpty()) {
            amount = parseDouble(form.get("amount"));
            if (amount == null) { sendJson(ex, 400, errorJson("Amount must be a number.")); return; }
        }
        try {
            paymentList.update(p.getId(), form.get("method"), form.get("status"), amount);
        } catch (IllegalArgumentException e) {
            sendJson(ex, 400, errorJson(e.getMessage())); return;
        }
        syncFineWithPayment(p);
        paymentList.saveToFile();
        fineList.saveToFile();
        sendJson(ex, 200, "{\"success\":true,\"payment\":" + paymentJson(p) + "}");
    }

    // ---------- delete ----------
    private static void delete(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        if (requireAdmin(ex) == null) return;
        Map<String, String> form = parseForm(readBody(ex));
        Payment p = paymentList.findById(form.get("id"));
        if (p == null) { sendJson(ex, 404, errorJson("Payment not found.")); return; }

        paymentList.remove(p.getId());
        p.setStatus("VOID");   // a deleted payment no longer settles its fine
        syncFineWithPayment(p);
        paymentList.saveToFile();
        fineList.saveToFile();
        sendJson(ex, 200, "{\"success\":true}");
    }

    /** A fine is PAID only while its payment is PAID; refund/void/delete puts it back to PENDING. */
    private static void syncFineWithPayment(Payment p) {
        if (!p.getType().equals("FINE")) return;
        Fine fine = fineList.findById(p.getFineId());
        if (fine == null || fine.getStatus().equals("WAIVED")) return;
        fine.setStatus(p.getStatus().equals("PAID") ? "PAID" : "PENDING");
    }

    // ---------- helpers ----------
    static String customerIdOf(Payment p) {
        Rental r = rentalList.findById(p.getRentalId());
        return r == null ? null : r.getCustomerId();
    }

    static String paymentJson(Payment p) {
        return String.format(Locale.ROOT,
                "{\"id\":\"%s\",\"rentalId\":\"%s\",\"customerId\":\"%s\",\"customerName\":\"%s\",\"amount\":%.2f,\"method\":\"%s\",\"status\":\"%s\",\"type\":\"%s\",\"fineId\":\"%s\"}",
                esc(p.getId()), esc(p.getRentalId()), esc(customerIdOf(p)), esc(UserHandler.customerName(customerIdOf(p))),
                p.getAmount(), esc(p.getMethod()), esc(p.getStatus()), esc(p.getType()), esc(p.getFineId()));
    }

    static String buildReceipt(Payment p, Rental r, Customer c, Vehicle v) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String startText = "N/A";
        String endText = "N/A";
        if (r.getStartDate() != null) {
            try {
                LocalDate s = LocalDate.parse(r.getStartDate());
                startText = s.format(fmt);
                endText = s.plusDays(r.getDays()).format(fmt);
            } catch (Exception ignored) { }
        }
        String bar = "=".repeat(40);
        boolean isFine = p.getType().equals("FINE");

        StringBuilder sb = new StringBuilder();
        sb.append(bar).append("\n");
        sb.append(center(isFine ? "FINE PAYMENT RECEIPT" : "VEHICLE RENTAL RECEIPT", 40)).append("\n");
        sb.append(bar).append("\n\n");

        sb.append("Receipt ID: R").append(p.getId()).append("\n");
        sb.append("Booking ID: ").append(r.getId()).append("\n\n");

        sb.append("Customer:\n").append(c != null ? c.getName() : r.getCustomerId()).append("\n\n");

        sb.append("Vehicle:\n");
        sb.append(v != null ? v.getBrand() + " " + v.getModel() : r.getVehicleId()).append("\n\n");

        if (isFine) {
            Fine fine = fineList.findById(p.getFineId());
            sb.append("Fine ID:      ").append(p.getFineId()).append("\n");
            sb.append("Fine Type:    ").append(fine != null ? fine.getReason() : "N/A").append("\n");
            if (fine != null && !fine.getDescription().isBlank()) {
                sb.append("Details:      ").append(fine.getDescription()).append("\n");
            }
            sb.append("\n");
        } else {
            sb.append("Rental Start: ").append(startText).append("\n");
            sb.append("Rental End:   ").append(endText).append("\n\n");

            double dailyRate = v != null ? v.getDailyRate() : r.getTotalCost() / Math.max(1, r.getDays());
            sb.append(String.format("Daily Rate:   Rs. %,.2f%n", dailyRate));
            sb.append("Days:         ").append(r.getDays()).append("\n\n");

            sb.append(String.format("Rental Cost:  Rs. %,.2f%n", r.getTotalCost()));
            double fines = 0;
            for (Fine f : fineList.getByRental(r.getId())) {
                if (!f.getStatus().equals("WAIVED")) fines += f.getAmount();
            }
            sb.append(String.format("Fines (billed separately): Rs. %,.2f%n%n", fines));
        }

        sb.append(String.format("TOTAL PAID:   Rs. %,.2f%n%n", p.getAmount()));

        sb.append("Payment Method: ").append(p.getMethod()).append("\n");
        sb.append("Payment Status: ").append(p.getStatus()).append("\n\n");

        sb.append(bar).append("\n");
        sb.append(center("Thank you for renting!", 40)).append("\n");
        sb.append(bar);
        return sb.toString();
    }

    private static String center(String text, int width) {
        int pad = Math.max(0, (width - text.length()) / 2);
        return " ".repeat(pad) + text;
    }
}
