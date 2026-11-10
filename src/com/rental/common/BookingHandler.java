package com.rental.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.rental.booking.Rental;
import com.rental.common.InvalidBookingException;
import com.rental.common.VehicleNotAvailableException;
import com.rental.fine.Fine;
import com.rental.payment.Payment;
import com.rental.user.Customer;
import com.rental.user.User;
import com.rental.vehicle.Vehicle;

import static com.rental.server.ApiUtil.*;
import static com.rental.server.AppData.*;

/**
 * BOOKING MODULE endpoints.
 *   GET  /api/bookings           (read: admin sees all, customer sees own)
 *   POST /api/bookings           (create + pay + receipt)
 *   POST /api/bookings/update    (update days / start date of a CONFIRMED booking; owner or admin)
 *   POST /api/bookings/cancel    (cancel a CONFIRMED booking and refund; owner or admin)
 *   POST /api/bookings/pickup    (admin: CONFIRMED -> ACTIVE)
 *   POST /api/bookings/return    (admin: ACTIVE -> RETURNED, adds late/damage fines)
 *   POST /api/bookings/delete    (delete a CANCELLED/RETURNED booking, admin)
 *
 * Status flow:  CONFIRMED -> ACTIVE -> RETURNED      CONFIRMED -> CANCELLED
 */
final class BookingHandler {

    private BookingHandler() { }

    static void handle(HttpExchange ex) throws IOException {
        switch (path(ex)) {
            case "/api/bookings":
                if (ex.getRequestMethod().equals("GET")) list(ex);
                else if (ex.getRequestMethod().equals("POST")) create(ex);
                else sendJson(ex, 405, errorJson("Method not allowed."));
                break;
            case "/api/bookings/update": update(ex); break;
            case "/api/bookings/cancel": cancel(ex); break;
            case "/api/bookings/pickup": pickup(ex); break;
            case "/api/bookings/return": returnVehicle(ex); break;
            case "/api/bookings/delete": delete(ex); break;
            default: notFound(ex);
        }
    }

    // ---------- read ----------
    private static void list(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) return;
        StringBuilder sb = new StringBuilder("[");
        for (Rental r : rentalList.getAll()) {
            if (!isAdmin(user) && !r.getCustomerId().equals(user.getId())) continue;
            if (sb.length() > 1) sb.append(",");
            sb.append(rentalJson(r));
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    // ---------- create ----------
    private static void create(HttpExchange ex) throws IOException {
        User user = authenticate(ex);
        if (user == null) return;
        Map<String, String> form = parseForm(readBody(ex));
        try {
            // customers always book for themselves; an admin may book on behalf of a customer
            String customerId = isAdmin(user) ? form.get("customerId") : user.getId();
            if (customerId == null || customerId.isBlank()) {
                sendJson(ex, 400, errorJson("Customer ID is missing."));
                return;
            }
            int days = Integer.parseInt(form.get("days").trim());
            String method = form.getOrDefault("method", "Cash");
            String cardNo = form.getOrDefault("cardNumber", "").replaceAll("\\s", "");
            if (method.equalsIgnoreCase("Card") && cardNo.length() < 12) {
                sendJson(ex, 400, errorJson("Please enter a valid card number."));
                return;
            }

            LocalDate start;
            try {
                start = LocalDate.parse(form.get("startDate"));
            } catch (Exception e) {
                start = LocalDate.now();
            }
            if (start.isBefore(LocalDate.now())) {
                sendJson(ex, 400, errorJson("Start date cannot be in the past."));
                return;
            }

            // 1. Create the booking
            Rental rental = rentalList.createRental(customerId, form.get("vehicleId"), days, userList, vehicleList);
            rental.setStartDate(start.toString());
            rentalList.saveToFile();
            vehicleList.saveToFile();

            // 2. Take the payment
            Payment payment = paymentList.makePayment(rental.getId(), method, rentalList);
            paymentList.saveToFile();

            // 3. Build the receipt
            Customer customer = userList.findCustomerById(customerId);
            Vehicle vehicle = vehicleList.findById(rental.getVehicleId());
            String receipt = PaymentHandler.buildReceipt(payment, rental, customer, vehicle);

            sendJson(ex, 201, "{\"success\":true,\"rental\":" + rentalJson(rental)
                    + ",\"payment\":" + PaymentHandler.paymentJson(payment)
                    + ",\"receipt\":\"" + escMultiline(receipt) + "\"}");
        } catch (NumberFormatException e) {
            sendJson(ex, 400, errorJson("Days must be a number."));
        } catch (VehicleNotAvailableException | InvalidBookingException e) {
            sendJson(ex, 400, errorJson(e.getMessage()));
        }
    }

    // ---------- update ----------
    private static void update(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        User user = authenticate(ex);
        if (user == null) return;
        Map<String, String> form = parseForm(readBody(ex));

        Rental rental = rentalList.findById(form.getOrDefault("bookingId", form.get("id")));
        if (rental == null) { sendJson(ex, 404, errorJson("Booking not found.")); return; }
        if (!canAccess(user, rental)) { sendJson(ex, 403, errorJson("You can only edit your own bookings.")); return; }

        try {
            int days = Integer.parseInt(trim(form.get("days")));
            String startDate = trim(form.get("startDate"));
            if (!startDate.isEmpty()) {
                LocalDate d;
                try { d = LocalDate.parse(startDate); }
                catch (Exception e) { sendJson(ex, 400, errorJson("Start date is not valid.")); return; }
                if (d.isBefore(LocalDate.now())) {
                    sendJson(ex, 400, errorJson("Start date cannot be in the past.")); return;
                }
            }
            rentalList.updateRental(rental.getId(), days, startDate, vehicleList);

            // keep the rental payment in step with the new price
            for (Payment p : paymentList.getByRental(rental.getId())) {
                if (p.getType().equals("RENTAL") && p.getStatus().equals("PAID")) {
                    p.setAmount(rental.getTotalCost());
                }
            }
            rentalList.saveToFile();
            paymentList.saveToFile();
            sendJson(ex, 200, "{\"success\":true,\"rental\":" + rentalJson(rental) + "}");
        } catch (NumberFormatException e) {
            sendJson(ex, 400, errorJson("Days must be a number."));
        } catch (InvalidBookingException e) {
            sendJson(ex, 400, errorJson(e.getMessage()));
        }
    }

    // ---------- cancel ----------
    private static void cancel(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        User user = authenticate(ex);
        if (user == null) return;
        Map<String, String> form = parseForm(readBody(ex));

        Rental rental = rentalList.findById(form.getOrDefault("bookingId", form.get("id")));
        if (rental == null) { sendJson(ex, 404, errorJson("Booking not found.")); return; }
        if (!canAccess(user, rental)) { sendJson(ex, 403, errorJson("You can only cancel your own bookings.")); return; }
        if (!rental.getStatus().equals("CONFIRMED")) {
            sendJson(ex, 400, errorJson("Only bookings that have not been collected yet can be cancelled.")); return;
        }

        rentalList.cancelRental(rental.getId(), vehicleList);
        int refunded = 0;
        for (Payment p : paymentList.getByRental(rental.getId())) {
            if (p.getType().equals("RENTAL") && p.getStatus().equals("PAID")) {
                p.setStatus("REFUNDED");
                refunded++;
            }
        }
        rentalList.saveToFile();
        vehicleList.saveToFile();
        paymentList.saveToFile();
        sendJson(ex, 200, "{\"success\":true,\"refunded\":" + refunded + "}");
    }

    // ---------- pickup ----------
    private static void pickup(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        if (requireAdmin(ex) == null) return;
        Map<String, String> form = parseForm(readBody(ex));
        String id = form.getOrDefault("bookingId", form.get("id"));
        Rental rental = rentalList.findById(id);
        if (rental == null) { sendJson(ex, 404, errorJson("Booking not found.")); return; }
        if (!rentalList.pickup(rental.getId())) {
            sendJson(ex, 400, errorJson("Only CONFIRMED bookings can be handed over.")); return;
        }
        rentalList.saveToFile();
        sendJson(ex, 200, "{\"success\":true,\"rental\":" + rentalJson(rental) + "}");
    }

    // ---------- return ----------
    private static void returnVehicle(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        if (requireAdmin(ex) == null) return;
        Map<String, String> form = parseForm(readBody(ex));

        Rental rental = rentalList.findById(form.getOrDefault("bookingId", form.get("rentalId")));
        if (rental == null) { sendJson(ex, 404, errorJson("Booking not found.")); return; }
        if (!rental.getStatus().equals("ACTIVE")) {
            sendJson(ex, 400, errorJson("Only ACTIVE bookings can be returned.")); return;
        }

        double damage = 0;
        if (!trim(form.get("damageAmount")).isEmpty()) {
            Double d = parseDouble(form.get("damageAmount"));
            if (d == null || d < 0) { sendJson(ex, 400, errorJson("Damage amount must be a positive number.")); return; }
            damage = d;
        }

        rentalList.returnVehicle(rental.getId(), vehicleList);
        Vehicle vehicle = vehicleList.findById(rental.getVehicleId());

        // automatic fines: late return + (optional) damage reported by the admin
        List<Fine> created = new ArrayList<>();
        Fine late = fineList.createLateFine(rental, vehicle, LocalDate.now());
        if (late != null) created.add(late);
        if (damage > 0) {
            String note = trim(form.get("damageNote"));
            created.add(fineList.addFine(rental.getId(), "DAMAGE", damage,
                    note.isEmpty() ? "Damage reported at return" : note, rentalList));
        }

        rentalList.saveToFile();
        vehicleList.saveToFile();
        fineList.saveToFile();

        StringBuilder fines = new StringBuilder("[");
        double total = 0;
        for (int i = 0; i < created.size(); i++) {
            if (i > 0) fines.append(",");
            fines.append(FineHandler.fineJson(created.get(i)));
            total += created.get(i).getAmount();
        }
        fines.append("]");
        sendJson(ex, 200, "{\"success\":true,\"fineCount\":" + created.size()
                + String.format(Locale.ROOT, ",\"fineTotal\":%.2f", total)
                + ",\"fines\":" + fines + "}");
    }

    // ---------- delete ----------
    private static void delete(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        if (requireAdmin(ex) == null) return;
        Map<String, String> form = parseForm(readBody(ex));
        Rental rental = rentalList.findById(form.getOrDefault("bookingId", form.get("id")));
        if (rental == null) { sendJson(ex, 404, errorJson("Booking not found.")); return; }
        if (!rental.getStatus().equals("CANCELLED") && !rental.getStatus().equals("RETURNED")) {
            sendJson(ex, 400, errorJson("Only CANCELLED or RETURNED bookings can be deleted.")); return;
        }

        // remove everything that hangs off this booking so nothing is left orphaned
        paymentList.removeByRental(rental.getId());
        fineList.removeByRental(rental.getId());
        rentalList.removeById(rental.getId());
        rentalList.saveToFile();
        paymentList.saveToFile();
        fineList.saveToFile();
        sendJson(ex, 200, "{\"success\":true}");
    }

    // ---------- helpers ----------
    static boolean canAccess(User user, Rental rental) {
        return isAdmin(user) || rental.getCustomerId().equals(user.getId());
    }

    static String rentalJson(Rental r) {
        return String.format(Locale.ROOT,
                "{\"id\":\"%s\",\"customerId\":\"%s\",\"customerName\":\"%s\",\"vehicleId\":\"%s\",\"vehicleName\":\"%s\",\"startDate\":\"%s\",\"days\":%d,\"totalCost\":%.2f,\"status\":\"%s\"}",
                esc(r.getId()), esc(r.getCustomerId()), esc(UserHandler.customerName(r.getCustomerId())),
                esc(r.getVehicleId()), esc(VehicleHandler.vehicleName(r.getVehicleId())), esc(r.getStartDate()),
                r.getDays(), r.getTotalCost(), esc(r.getStatus()));
    }
}
