package com.rental.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.time.Year;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.nio.file.Files;



import com.rental.vehicle.*;

import static com.rental.server.ApiUtil.*;
import static com.rental.server.AppData.*;

/**
 * VEHICLE MODULE endpoints.
 *   GET  /api/vehicles                (read, everyone)
 *   GET  /api/vehicles/available      (read, everyone)
 *   POST /api/vehicles                (create, admin)
 *   POST /api/vehicles/update         (update, admin)
 *   POST /api/vehicles/delete         (delete, admin)
 */
final class VehicleHandler {

    private VehicleHandler() { }

    static void handle(HttpExchange ex) throws IOException {
        switch (path(ex)) {
            case "/api/vehicles":
                if (ex.getRequestMethod().equals("GET")) list(ex, false);
                else if (ex.getRequestMethod().equals("POST")) add(ex);
                else sendJson(ex, 405, errorJson("Method not allowed."));
                break;
            case "/api/vehicles/available":
                if (method(ex, "GET")) list(ex, true);
                break;
            case "/api/vehicles/photo": uploadPhoto(ex); break;
            case "/api/vehicles/photo/remove": removePhoto(ex); break;
            case "/api/vehicles/update": update(ex); break;
            case "/api/vehicles/delete": delete(ex); break;
            default: notFound(ex);
        }
    }

    private static void list(HttpExchange ex, boolean onlyAvailable) throws IOException {
        List<Vehicle> src = onlyAvailable ? vehicleList.getAvailable() : vehicleList.getAll();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < src.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(vehicleJson(src.get(i)));
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    private static void add(HttpExchange ex) throws IOException {
        if (requireAdmin(ex) == null) return;
        Map<String, String> form = parseForm(readBody(ex));
        String err = validate(form);
        if (err != null) { sendJson(ex, 400, errorJson(err)); return; }

        String type = form.getOrDefault("type", "car").toLowerCase();
        String brand = trim(form.get("brand"));
        String model = trim(form.get("model"));
        int year = Integer.parseInt(form.get("year").trim());
        double rate = parseDouble(form.get("dailyRate"));
        String id = vehicleList.generateNextId();

        Vehicle v;
        switch (type) {
            case "van": v = new Van(id, brand, model, year, rate, true); break;
            case "motorcycle": v = new Motorcycle(id, brand, model, year, rate, true); break;
            default: v = new Car(id, brand, model, year, rate, true);
        }
        vehicleList.addVehicle(v);
        vehicleList.saveToFile();
        sendJson(ex, 201, "{\"success\":true,\"vehicle\":" + vehicleJson(v) + "}");
    }

    private static void update(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        if (requireAdmin(ex) == null) return;
        Map<String, String> form = parseForm(readBody(ex));
        if (vehicleList.findById(form.get("id")) == null) {
            sendJson(ex, 404, errorJson("Vehicle not found.")); return;
        }
        String err = validate(form);
        if (err != null) { sendJson(ex, 400, errorJson(err)); return; }

        Vehicle v = vehicleList.update(form.get("id"), trim(form.get("brand")), trim(form.get("model")),
                Integer.parseInt(form.get("year").trim()), parseDouble(form.get("dailyRate")));
        vehicleList.saveToFile();
        sendJson(ex, 200, "{\"success\":true,\"vehicle\":" + vehicleJson(v) + "}");
    }

    private static void delete(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        if (requireAdmin(ex) == null) return;
        Map<String, String> form = parseForm(readBody(ex));
        String id = form.get("id");
        if (vehicleList.findById(id) == null) {
            sendJson(ex, 404, errorJson("Vehicle not found.")); return;
        }
        if (rentalList.hasOpenRentalForVehicle(id)) {
            sendJson(ex, 400, errorJson("This vehicle has an active or confirmed booking and cannot be removed.")); return;
        }
        Vehicle removed = vehicleList.findById(id);
        removeImageFile(removed);
        vehicleList.removeVehicle(id);
        vehicleList.saveToFile();
        sendJson(ex, 200, "{\"success\":true}");
    }

    private static void uploadPhoto(HttpExchange ex) throws IOException {
        if (!method(ex, "POST") || requireAdmin(ex) == null) return;
        Map<String, String> query = parseForm(ex.getRequestURI().getRawQuery());
        String id = query.get("vehicleId");
        Vehicle v = vehicleList.findById(id);
        if (v == null) { sendJson(ex, 404, errorJson("Vehicle not found.")); return; }
        String type = ex.getRequestHeaders().getFirst("Content-Type");
        String ext = type != null && type.toLowerCase(Locale.ROOT).contains("png") ? ".png"
                : type != null && type.toLowerCase(Locale.ROOT).contains("webp") ? ".webp"
                : type != null && (type.toLowerCase(Locale.ROOT).contains("jpeg") || type.toLowerCase(Locale.ROOT).contains("jpg")) ? ".jpg" : "";
        if (ext.isEmpty()) { sendJson(ex, 400, errorJson("Choose a JPG, PNG, or WebP photo.")); return; }
        byte[] bytes;
        try (var in = ex.getRequestBody()) {
            bytes = in.readNBytes(2 * 1024 * 1024 + 1);
        }
        if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024) { sendJson(ex, 400, errorJson("Photo must be between 1 byte and 2 MB.")); return; }
        File folder = new File("frontend/images/vehicles");
        folder.mkdirs();
        String filename = id + ext;
        Files.write(new File(folder, filename).toPath(), bytes);
        // Remove any previous extension after the new image has been safely saved.
        String oldImage = v.getImage();
        v.setImage("images/vehicles/" + filename);
        vehicleList.saveToFile();
        if (!oldImage.isBlank() && !oldImage.equals(v.getImage())) new File("frontend/" + oldImage).delete();
        sendJson(ex, 200, "{\"success\":true,\"vehicle\":" + vehicleJson(v) + "}");
    }

    private static void removePhoto(HttpExchange ex) throws IOException {
        if (!method(ex, "POST") || requireAdmin(ex) == null) return;
        String id = parseForm(ex.getRequestURI().getRawQuery()).get("vehicleId");
        Vehicle v = vehicleList.findById(id);
        if (v == null) { sendJson(ex, 404, errorJson("Vehicle not found.")); return; }
        removeImageFile(v);
        v.setImage("");
        vehicleList.saveToFile();
        sendJson(ex, 200, "{\"success\":true,\"vehicle\":" + vehicleJson(v) + "}");
    }

    private static void removeImageFile(Vehicle v) {
        if (v != null && !v.getImage().isBlank()) new File("frontend/" + v.getImage()).delete();
    }

    /** Shared validation for add + update. Returns an error message or null. */
    private static String validate(Map<String, String> form) {
        if (trim(form.get("brand")).isEmpty()) return "Brand is required.";
        if (trim(form.get("model")).isEmpty()) return "Model is required.";
        int year;
        try {
            year = Integer.parseInt(trim(form.get("year")));
        } catch (NumberFormatException e) {
            return "Year must be a whole number.";
        }
        if (year < 1950 || year > Year.now().getValue() + 1) return "Year looks invalid.";
        Double rate = parseDouble(form.get("dailyRate"));
        if (rate == null) return "Daily rate must be a number.";
        if (rate <= 0) return "Daily rate must be greater than zero.";
        return null;
    }

    static String vehicleJson(Vehicle v) {
        return String.format(Locale.ROOT,
                "{\"id\":\"%s\",\"type\":\"%s\",\"brand\":\"%s\",\"model\":\"%s\",\"year\":%d,\"dailyRate\":%.2f,\"available\":%b,\"image\":\"%s\"}",
                esc(v.getId()), esc(v.getType()), esc(v.getBrand()), esc(v.getModel()), v.getYear(), v.getDailyRate(), v.isAvailable(), esc(v.getImage()));
    }

    static String vehicleName(String vehicleId) {
        Vehicle v = vehicleList.findById(vehicleId);
        return v != null ? v.getBrand() + " " + v.getModel() : vehicleId;
    }
}
