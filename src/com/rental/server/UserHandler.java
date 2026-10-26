package com.rental.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.rental.booking.Rental;
import com.rental.fine.Fine;
import com.rental.user.*;

import static com.rental.server.ApiUtil.*;
import static com.rental.server.AppData.*;


final class UserHandler {

    private UserHandler() { }

    static void handle(HttpExchange ex) throws IOException {
        switch (path(ex)) {
            case "/api/users": list(ex); break;
            case "/api/users/update": update(ex); break;
            case "/api/users/delete": delete(ex); break;
            default: notFound(ex);
        }
    }

    // ---------- auth + create ----------
    static void login(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        Map<String, String> form = parseForm(readBody(ex));
        User u = userList.login(form.get("username"), form.get("password"));
        if (u == null) {
            sendJson(ex, 401, "{\"success\":false,\"message\":\"Invalid username or password.\"}");
        } else {
            sendJson(ex, 200, "{\"success\":true,\"user\":" + userJson(u) + "}");
        }
    }

    static void registerCustomer(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        Map<String, String> form = parseForm(readBody(ex));
        String err = validateCredentials(form.get("username"), form.get("password"), null);
        if (err != null) { sendJson(ex, err.startsWith("That") ? 409 : 400, errorJson(err)); return; }
        if (trim(form.get("name")).isEmpty()) { sendJson(ex, 400, errorJson("Full name is required.")); return; }

        Customer c = new Customer(userList.generateNextId(), trim(form.get("username")), form.get("password"),
                trim(form.get("name")), trim(form.get("phone")), trim(form.get("license")));
        userList.register(c);
        userList.saveToFile();
        sendJson(ex, 201, "{\"success\":true,\"user\":" + userJson(c) + "}");
    }

    static void registerAdmin(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        Map<String, String> form = parseForm(readBody(ex));
        String err = validateCredentials(form.get("username"), form.get("password"), null);
        if (err != null) { sendJson(ex, err.startsWith("That") ? 409 : 400, errorJson(err)); return; }
        if (trim(form.get("name")).isEmpty()) { sendJson(ex, 400, errorJson("Full name is required.")); return; }

        Admin a = new Admin(userList.generateNextId(), trim(form.get("username")), form.get("password"),
                trim(form.get("name")));
        userList.register(a);
        userList.saveToFile();
        sendJson(ex, 201, "{\"success\":true,\"user\":" + userJson(a) + "}");
    }

    // ---------- read ----------
    private static void list(HttpExchange ex) throws IOException {
        if (!method(ex, "GET")) return;
        if (requireAdmin(ex) == null) return;
        StringBuilder sb = new StringBuilder("[");
        List<User> all = userList.getAll();
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(userJson(all.get(i)));
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    // ---------- update ----------
    private static void update(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        User caller = authenticate(ex);
        if (caller == null) return;
        Map<String, String> form = parseForm(readBody(ex));

        String id = form.getOrDefault("id", caller.getId());
        if (!isAdmin(caller) && !caller.getId().equals(id)) {
            sendJson(ex, 403, errorJson("You can only edit your own profile.")); return;
        }
        User target = userList.findById(id);
        if (target == null) { sendJson(ex, 404, errorJson("User not found.")); return; }

        // username (must stay unique) and optional new password
        String newUsername = form.containsKey("username") ? trim(form.get("username")) : target.getUsername();
        String newPassword = form.getOrDefault("password", "");
        String err = validateCredentials(newUsername, newPassword.isEmpty() ? "unchanged" : newPassword, target);
        if (err != null) { sendJson(ex, err.startsWith("That") ? 409 : 400, errorJson(err)); return; }

        String name = form.containsKey("name") ? trim(form.get("name")) : null;
        if (name != null && name.isEmpty()) { sendJson(ex, 400, errorJson("Full name is required.")); return; }

        target.setUsername(newUsername);
        if (!newPassword.isEmpty()) target.setPassword(newPassword);

        if (target instanceof Customer c) {
            if (name != null) c.setName(name);
            if (form.containsKey("phone")) c.setPhone(trim(form.get("phone")));
            if (form.containsKey("license")) c.setLicenseNumber(trim(form.get("license")));
        } else if (target instanceof Admin a) {
            if (name != null) a.setName(name);
        }
        userList.saveToFile();
        sendJson(ex, 200, "{\"success\":true,\"user\":" + userJson(target) + "}");
    }

    // ---------- delete ----------
    private static void delete(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        User caller = authenticate(ex);
        if (caller == null) return;
        Map<String, String> form = parseForm(readBody(ex));

        String id = form.get("id");
        User target = userList.findById(id);
        if (target == null) { sendJson(ex, 404, errorJson("User not found.")); return; }

        if (!isAdmin(caller) && !caller.getId().equals(target.getId())) {
            sendJson(ex, 403, errorJson("You can only delete your own account.")); return;
        }
        if (isAdmin(target) && caller.getId().equals(target.getId())) {
            sendJson(ex, 400, errorJson("Admins cannot delete their own account. Ask another admin.")); return;
        }
        if (target instanceof Customer) {
            if (rentalList.hasOpenRentalForCustomer(target.getId())) {
                sendJson(ex, 400, errorJson("This customer still has an active or confirmed booking.")); return;
            }
            for (Fine f : fineList.getAll()) {
                Rental r = rentalList.findById(f.getRentalId());
                if (r != null && r.getCustomerId().equals(target.getId()) && f.getStatus().equals("PENDING")) {
                    sendJson(ex, 400, errorJson("This customer still has unpaid fines.")); return;
                }
            }
        }
        userList.removeById(target.getId());
        userList.saveToFile();
        sendJson(ex, 200, "{\"success\":true}");
    }

    // ---------- helpers ----------
    /** Returns an error message, or null if the username/password are acceptable. "self" is the user being edited. */
    private static String validateCredentials(String username, String password, User self) {
        String u = trim(username);
        if (u.isEmpty()) return "Username is required.";
        if (password == null || password.isEmpty()) return "Password is required.";
        if (u.contains(",") || password.contains(",")) return "Username and password cannot contain commas.";
        User existing = userList.findByUsername(u);
        if (existing != null && existing != self) return "That username is already taken.";
        return null;
    }

    static String userJson(User u) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":\"").append(esc(u.getId()))
          .append("\",\"username\":\"").append(esc(u.getUsername()))
          .append("\",\"role\":\"").append(esc(u.getRole())).append("\"");
        if (u instanceof Customer c) {
            sb.append(",\"name\":\"").append(esc(c.getName()))
              .append("\",\"phone\":\"").append(esc(c.getPhone()))
              .append("\",\"license\":\"").append(esc(c.getLicenseNumber())).append("\"");
        } else if (u instanceof Admin a) {
            sb.append(",\"name\":\"").append(esc(a.getName())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    static String customerName(String customerId) {
        Customer c = customerId == null ? null : userList.findCustomerById(customerId);
        return c != null ? c.getName() : customerId;
    }
}
