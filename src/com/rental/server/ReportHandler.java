package com.rental.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.rental.booking.Rental;
import com.rental.fine.Fine;
import com.rental.vehicle.Vehicle;

import static com.rental.server.ApiUtil.*;
import static com.rental.server.AppData.*;

/**
 * REPORT endpoints (admin only). Owns no data: it summarises every other module.
 *   GET /api/reports
 *   GET /api/reports/download
 */
final class ReportHandler {

    private ReportHandler() { }

    static void handle(HttpExchange ex) throws IOException {
        if (!method(ex, "GET")) return;
        if (requireAdmin(ex) == null) return;
        switch (path(ex)) {
            case "/api/reports": sendJson(ex, 200, buildReportJson()); break;
            case "/api/reports/download": download(ex); break;
            default: notFound(ex);
        }
    }

    private static void download(HttpExchange ex) throws IOException {
        byte[] bytes = buildReportText().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"rental-report.txt\"");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    /** Cancelled bookings never happened, so they are not counted towards "most rented". */
    private static Map<String, Integer> rentalCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (Rental r : rentalList.getAll()) {
            if (r.getStatus().equals("CANCELLED")) continue;
            counts.merge(r.getVehicleId(), 1, Integer::sum);
        }
        return counts;
    }

    static String buildReportJson() {
        Map<String, Integer> counts = rentalCounts();
        String topId = counts.isEmpty() ? null : Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
        Vehicle topVehicle = topId == null ? null : vehicleList.findById(topId);

        long pendingMaintenance = maintenanceList.getAll().stream().filter(m -> m.getStatus().equals("PENDING")).count();

        StringBuilder sb = new StringBuilder("{");
        sb.append(String.format(Locale.ROOT, "\"totalRevenue\":%.2f,", paymentList.getTotalRevenue()));
        sb.append(String.format(Locale.ROOT, "\"rentalRevenue\":%.2f,", paymentList.getRevenueByType("RENTAL")));
        sb.append(String.format(Locale.ROOT, "\"fineRevenue\":%.2f,", paymentList.getRevenueByType("FINE")));
        sb.append(String.format(Locale.ROOT, "\"totalRefunded\":%.2f,", paymentList.getTotalRefunded()));
        sb.append("\"totalPayments\":").append(paymentList.getAll().size()).append(",");
        sb.append("\"mostRentedVehicle\":").append(topVehicle == null ? "null" : VehicleHandler.vehicleJson(topVehicle)).append(",");
        sb.append("\"mostRentedCount\":").append(topId == null ? 0 : counts.get(topId)).append(",");
        sb.append("\"availableVehicles\":").append(vehicleList.getAvailable().size()).append(",");
        sb.append("\"totalVehicles\":").append(vehicleList.getAll().size()).append(",");
        sb.append("\"pendingMaintenance\":").append(pendingMaintenance).append(",");
        sb.append("\"totalMaintenanceRecords\":").append(maintenanceList.getAll().size()).append(",");
        sb.append("\"totalFines\":").append(fineList.getAll().size()).append(",");
        sb.append("\"pendingFines\":").append(fineList.countByStatus("PENDING")).append(",");
        sb.append(String.format(Locale.ROOT, "\"pendingFinesAmount\":%.2f,", fineList.totalByStatus("PENDING")));
        sb.append("\"paidFines\":").append(fineList.countByStatus("PAID")).append(",");
        sb.append("\"waivedFines\":").append(fineList.countByStatus("WAIVED"));
        sb.append("}");
        return sb.toString();
    }

    static String buildReportText() {
        StringBuilder sb = new StringBuilder();
        sb.append("VEHICLE RENTAL MANAGEMENT SYSTEM - REPORT\n");
        sb.append("Generated: ").append(new Date()).append("\n");
        sb.append("==========================================\n\n");

        sb.append("REVENUE\n");
        sb.append(String.format("Total Revenue: Rs.%.2f%n", paymentList.getTotalRevenue()));
        sb.append(String.format("  Rental payments: Rs.%.2f%n", paymentList.getRevenueByType("RENTAL")));
        sb.append(String.format("  Fine payments:   Rs.%.2f%n", paymentList.getRevenueByType("FINE")));
        sb.append(String.format("Refunded: Rs.%.2f%n", paymentList.getTotalRefunded()));
        sb.append("Total Payments Processed: ").append(paymentList.getAll().size()).append("\n\n");

        sb.append("MOST RENTED VEHICLE\n");
        Map<String, Integer> counts = rentalCounts();
        if (counts.isEmpty()) {
            sb.append("No rentals recorded yet.\n\n");
        } else {
            String topId = Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
            Vehicle v = vehicleList.findById(topId);
            sb.append(v != null ? v.toString() : topId).append(" - rented ").append(counts.get(topId)).append(" time(s)\n\n");
        }

        sb.append("VEHICLE AVAILABILITY\n");
        sb.append("Available: ").append(vehicleList.getAvailable().size()).append(" / ").append(vehicleList.getAll().size()).append("\n\n");

        sb.append("MAINTENANCE\n");
        long pending = maintenanceList.getAll().stream().filter(m -> m.getStatus().equals("PENDING")).count();
        sb.append("Pending repairs: ").append(pending).append(" / ").append(maintenanceList.getAll().size()).append("\n\n");

        sb.append("FINES\n");
        sb.append(String.format("Pending:   %d (Rs.%.2f)%n", fineList.countByStatus("PENDING"), fineList.totalByStatus("PENDING")));
        sb.append(String.format("Collected: %d (Rs.%.2f)%n", fineList.countByStatus("PAID"), fineList.totalByStatus("PAID")));
        sb.append(String.format("Waived:    %d (Rs.%.2f)%n%n", fineList.countByStatus("WAIVED"), fineList.totalByStatus("WAIVED")));

        sb.append("ALL VEHICLES\n");
        for (Vehicle v : vehicleList.getAll()) sb.append(v.toString()).append("\n");

        sb.append("\nALL RENTALS\n");
        for (Rental r : rentalList.getAll()) sb.append(r.toString()).append("\n");

        sb.append("\nALL FINES\n");
        for (Fine f : fineList.getAll()) sb.append(f.toString()).append("\n");

        return sb.toString();
    }
}
