package com.rental.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.rental.maintenance.MaintenanceRecord;

import static com.rental.server.ApiUtil.*;
import static com.rental.server.AppData.*;

/**
 * MAINTENANCE MODULE endpoints (all admin only).
 *   GET  /api/maintenance            (read)
 *   POST /api/maintenance            (create: takes the vehicle offline)
 *   POST /api/maintenance/update     (edit issue / cost)
 *   POST /api/maintenance/complete   (mark done: vehicle goes back to the fleet)
 *   POST /api/maintenance/delete     (delete a record)
 */
final class MaintenanceHandler {

    private MaintenanceHandler() { }

    static void handle(HttpExchange ex) throws IOException {
        if (requireAdmin(ex) == null) return;   // the whole module is admin only
        switch (path(ex)) {
            case "/api/maintenance":
                if (ex.getRequestMethod().equals("GET")) list(ex);
                else if (ex.getRequestMethod().equals("POST")) create(ex);
                else sendJson(ex, 405, errorJson("Method not allowed."));
                break;
            case "/api/maintenance/update": update(ex); break;
            case "/api/maintenance/complete": complete(ex); break;
            case "/api/maintenance/delete": delete(ex); break;
            default: notFound(ex);
        }
    }

    private static void list(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        List<MaintenanceRecord> all = maintenanceList.getAll();
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(maintenanceJson(all.get(i)));
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    private static void create(HttpExchange ex) throws IOException {
        Map<String, String> form = parseForm(readBody(ex));
        String vehicleId = form.get("vehicleId");
        if (vehicleList.findById(vehicleId) == null) {
            sendJson(ex, 404, errorJson("Vehicle not found.")); return;
        }
        if (rentalList.hasOpenRentalForVehicle(vehicleId)) {
            sendJson(ex, 400, errorJson("This vehicle is currently booked or rented out.")); return;
        }
        String issue = trim(form.get("issue"));
        if (issue.isEmpty()) { sendJson(ex, 400, errorJson("Issue description is required.")); return; }
        Double cost = parseDouble(form.getOrDefault("cost", "0"));
        if (cost == null || cost < 0) { sendJson(ex, 400, errorJson("Cost must be a number (0 or more).")); return; }

        MaintenanceRecord m = maintenanceList.addRecord(vehicleId, issue, cost, vehicleList);
        maintenanceList.saveToFile();
        vehicleList.saveToFile();
        sendJson(ex, 201, "{\"success\":true,\"record\":" + maintenanceJson(m) + "}");
    }

    private static void update(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        Map<String, String> form = parseForm(readBody(ex));
        if (maintenanceList.findById(form.get("id")) == null) {
            sendJson(ex, 404, errorJson("Record not found.")); return;
        }
        String issue = trim(form.get("issue"));
        if (issue.isEmpty()) { sendJson(ex, 400, errorJson("Issue description is required.")); return; }
        Double cost = parseDouble(form.get("cost"));
        if (cost == null || cost < 0) { sendJson(ex, 400, errorJson("Cost must be a number (0 or more).")); return; }

        MaintenanceRecord m = maintenanceList.updateRecord(form.get("id"), issue, cost);
        maintenanceList.saveToFile();
        sendJson(ex, 200, "{\"success\":true,\"record\":" + maintenanceJson(m) + "}");
    }

    private static void complete(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        Map<String, String> form = parseForm(readBody(ex));
        boolean ok = maintenanceList.completeRecord(form.get("id"), vehicleList);
        if (ok) { maintenanceList.saveToFile(); vehicleList.saveToFile(); }
        sendJson(ex, ok ? 200 : 404, "{\"success\":" + ok + "}");
    }

    private static void delete(HttpExchange ex) throws IOException {
        if (!method(ex, "POST")) return;
        Map<String, String> form = parseForm(readBody(ex));
        boolean ok = maintenanceList.deleteRecord(form.get("id"), vehicleList);
        if (ok) { maintenanceList.saveToFile(); vehicleList.saveToFile(); }
        sendJson(ex, ok ? 200 : 404, "{\"success\":" + ok + "}");
    }

    static String maintenanceJson(MaintenanceRecord m) {
        return String.format(Locale.ROOT,
                "{\"id\":\"%s\",\"vehicleId\":\"%s\",\"vehicleName\":\"%s\",\"issue\":\"%s\",\"cost\":%.2f,\"status\":\"%s\"}",
                esc(m.getId()), esc(m.getVehicleId()), esc(VehicleHandler.vehicleName(m.getVehicleId())),
                esc(m.getIssueDescription()), m.getCost(), esc(m.getStatus()));
    }
}
