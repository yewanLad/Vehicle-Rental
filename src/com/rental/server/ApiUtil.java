package com.rental.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.rental.user.User;

final class ApiUtil {

    private ApiUtil() { }

    // ---------- request helpers ----------
    static String path(HttpExchange ex) {
        String p = ex.getRequestURI().getPath();
        return (p.length() > 1 && p.endsWith("/")) ? p.substring(0, p.length() - 1) : p;
    }

    static boolean method(HttpExchange ex, String expected) throws IOException {
        if (!ex.getRequestMethod().equals(expected)) {
            sendJson(ex, 405, errorJson("Method not allowed."));
            return false;
        }
        return true;
    }

    static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isBlank()) return map;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(key, value);
        }
        return map;
    }

    /** Parses a number from a form field; returns null when missing or not a valid number. */
    static Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            double d = Double.parseDouble(s.trim());
            return (Double.isNaN(d) || Double.isInfinite(d)) ? null : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    // ---------- response helpers ----------
    static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    static void notFound(HttpExchange ex) throws IOException {
        sendJson(ex, 404, errorJson("Not found."));
    }

    static String errorJson(String message) {
        return "{\"success\":false,\"message\":\"" + esc(message) + "\"}";
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ").replace("\t", " ");
    }

    /** Like esc(), but keeps line breaks (as \n) so multi-line text survives JSON. */
    static String escMultiline(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n");
    }

    // ---------- access control ----------
    /** Identifies the caller from ?userId=... Sends 401 and returns null if unknown. */
    static User authenticate(HttpExchange ex) throws IOException {
        String id = parseForm(ex.getRequestURI().getRawQuery()).get("userId");
        User u = AppData.userList.findById(id);
        if (u == null) {
            sendJson(ex, 401, errorJson("Please log in."));
        }
        return u;
    }

    static boolean isAdmin(User u) {
        return u != null && "ADMIN".equalsIgnoreCase(u.getRole());
    }

    /** Authenticates and requires an admin. Sends 401/403 and returns null otherwise. */
    static User requireAdmin(HttpExchange ex) throws IOException {
        User u = authenticate(ex);
        if (u == null) return null;
        if (!isAdmin(u)) {
            sendJson(ex, 403, errorJson("Admin access required."));
            return null;
        }
        return u;
    }
}
