package com.rental.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import com.rental.booking.Rental;
import com.rental.fine.Fine;
import com.rental.payment.Payment;
import com.rental.user.User;


import static com.rental.server.ApiUtil.*;
import static com.rental.server.AppData.*;

/**
 * FINE MODULE endpoints.
 *   GET  /api/fines          (read: admin all, customer own)
 *   POST /api/fines          (create a manual fine, admin)
 *   POST /api/fines/update   (edit reason/amount/description of a PENDING fine, admin)
 *   POST /api/fines/pay      (pay a PENDING fine: owner or admin; creates a FINE payment)
 *   POST /api/fines/waive    (waive a PENDING fine, admin)
 *   POST /api/fines/delete   (delete a non-paid fine, admin)
 *
 * Late-return and damage fines are also created automatically by
 * POST /api/bookings/return (see BookingHandler).
 */
final class FineHandler {

    private FineHandler() { }

    static void handle(HttpExchange ex) throws IOException {
        switch (path(ex)) {
            case "/api/fines":
                if (ex.getRequestMethod().equals("GET")) list(ex);
                else if (ex.getRequestMethod().equals("POST")) create(ex);
                else sendJson(ex, 405, errorJson("Method not allowed."));
                break;
            case "/api/fines/update": update(ex); break;
            case "/api/fines/pay": pay(ex); break;
            case "/api/fines/waive": waive(ex); break;
            case "/api/fines/delete": delete(ex); break;
            default: notFound(ex);
        }
    }

    // ---------- read ----------
    private static void list(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) return;
        StringBuilder sb = new StringBuilder("[");
        for (Fine f : fineList.getAll()) {
            if (!isAdmin(user) && !user.getId().equals(customerIdOf(f))) continue;
            if (sb.length() > 1) sb.append(",");
            sb.append(fineJson(f));
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    // ---------- create ----------
    private static void create(HttpExchange ex) throws IOException {
        if (requireAdmin(ex) == null) return;
        Map<String, String> form = parseForm(readBody(ex));
        Double amount = parseDouble(form.get("amount"));
        if (amount == null) { sendJson(ex, 400, errorJson("Amount must be a number.")); return; }
        try {
            Fine f = fineList.addFine(trim(form.get("rentalId")), form.get("reason"), amount,
                    trim(form.get("description")), rentalList);
            fineList.saveToFile();
            sendJson(ex, 201, "{\"success\":true,\"fine\":" + fineJson(f) + "}");
        } catch (IllegalArgumentException e) {
            sendJson(ex, 400, errorJson(e.getMessage()));
        }
    }

    // ---------- update ----------
    private static void update(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        if (requireAdmin(ex) == null) return;
        Map<String, String> form = parseForm(readBody(ex));
        Double amount = parseDouble(form.get("amount"));
        if (amount == null) { sendJson(ex, 400, errorJson("Amount must be a number.")); return; }
        try {
            Fine f = fineList.update(form.get("id"), form.get("reason"), amount, trim(form.get("description")));
            fineList.saveToFile();
            sendJson(ex, 200, "{\"success\":true,\"fine\":" + fineJson(f) + "}");
        } catch (IllegalArgumentException e) {
            sendJson(ex, e.getMessage().contains("not found") ? 404 : 400, errorJson(e.getMessage()));
        }
    }

    // ---------- pay ----------
    private static void pay(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        User user = authenticate(ex);
        if (user == null) return;
        Map<String, String> form = parseForm(readBody(ex));

        Fine fine = fineList.findById(form.get("id"));
        if (fine == null) { sendJson(ex, 404, errorJson("Fine not found.")); return; }
        if (!isAdmin(user) && !user.getId().equals(customerIdOf(fine))) {
            sendJson(ex, 403, errorJson("You can only pay your own fines.")); return;
        }
        try {
            fineList.markPaid(fine.getId());   // throws if it is not PENDING
        } catch (IllegalArgumentException e) {
            sendJson(ex, 400, errorJson(e.getMessage())); return;
        }
        String method = trim(form.get("method"));
        Payment p = paymentList.makeFinePayment(fine, method.isEmpty() ? "Cash" : method);
        fineList.saveToFile();
        paymentList.saveToFile();
        sendJson(ex, 200, "{\"success\":true,\"fine\":" + fineJson(fine)
                + ",\"payment\":" + PaymentHandler.paymentJson(p) + "}");
    }

    // ---------- waive ----------
    private static void waive(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        if (requireAdmin(ex) == null) return;
        Map<String, String> form = parseForm(readBody(ex));
        try {
            Fine f = fineList.waive(form.get("id"));
            fineList.saveToFile();
            sendJson(ex, 200, "{\"success\":true,\"fine\":" + fineJson(f) + "}");
        } catch (IllegalArgumentException e) {
            sendJson(ex, e.getMessage().contains("not found") ? 404 : 400, errorJson(e.getMessage()));
        }
    }

    // ---------- delete ----------
    private static void delete(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        if (requireAdmin(ex) == null) return;
        Map<String, String> form = parseForm(readBody(ex));
        try {
            fineList.remove(form.get("id"));
            fineList.saveToFile();
            sendJson(ex, 200, "{\"success\":true}");
        } catch (IllegalArgumentException e) {
            sendJson(ex, e.getMessage().contains("not found") ? 404 : 400, errorJson(e.getMessage()));
        }
    }

    // ---------- helpers ----------
    static String customerIdOf(Fine f) {
        Rental r = rentalList.findById(f.getRentalId());
        return r == null ? null : r.getCustomerId();
    }

    static String fineJson(Fine f) {
        Rental r = rentalList.findById(f.getRentalId());
        String customerId = r == null ? "" : r.getCustomerId();
        String vehicleName = r == null ? "" : VehicleHandler.vehicleName(r.getVehicleId());
        return String.format(Locale.ROOT,
                "{\"id\":\"%s\",\"rentalId\":\"%s\",\"customerId\":\"%s\",\"customerName\":\"%s\",\"vehicleName\":\"%s\",\"reason\":\"%s\",\"description\":\"%s\",\"amount\":%.2f,\"status\":\"%s\"}",
                esc(f.getId()), esc(f.getRentalId()), esc(customerId), esc(UserHandler.customerName(customerId)),
                esc(vehicleName), esc(f.getReason()), esc(f.getDescription()), f.getAmount(), esc(f.getStatus()));
    }
}
