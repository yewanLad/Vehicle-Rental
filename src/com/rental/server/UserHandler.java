package com.rental.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.rental.booking.Rental;
import com.rental.fine.Fine;
import com.rental.user.*;



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


}
