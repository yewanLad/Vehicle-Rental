package com.rental.report;

import java.util.*;
import com.rental.vehicle.VehicleList;
import com.rental.vehicle.Vehicle;
import com.rental.booking.RentalList;
import com.rental.booking.Rental;
import com.rental.payment.PaymentList;
import com.rental.maintenance.MaintenanceList;
import com.rental.fine.FineList;


public class ReportGenerator {

    public void printRevenueReport(PaymentList paymentList) {
        System.out.println("\n=== Revenue Report ===");
        System.out.printf("Total Revenue: Rs.%.2f%n", paymentList.getTotalRevenue());
        System.out.println("Total Payments Processed: " + paymentList.getAll().size());
        System.out.printf("Refunded: Rs.%.2f (%d payment(s))%n",
                paymentList.getTotalRefunded(), paymentList.countByStatus("REFUNDED"));
    }

    public void printMostRentedVehicle(RentalList rentalList, VehicleList vehicleList) {
        Map<String, Integer> counts = new HashMap<>();
        for (Rental r : rentalList.getAll()) {
            if (r.getStatus().equals("CANCELLED")) continue; // cancelled bookings don't count as rentals
            counts.merge(r.getVehicleId(), 1, Integer::sum);
        }

        System.out.println("\n=== Most Rented Vehicle ===");
        if (counts.isEmpty()) {
            System.out.println("No rentals recorded yet.");
            return;
        }

        String topId = Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
        Vehicle v = vehicleList.findById(topId);
        System.out.println((v != null ? v.toString() : topId) + " - rented " + counts.get(topId) + " time(s)");
    }

    public void printAvailabilitySummary(VehicleList vehicleList) {
        int available = vehicleList.getAvailable().size();
        int total = vehicleList.getAll().size();
        System.out.println("\n=== Vehicle Availability ===");
        System.out.println("Available: " + available + " / " + total);
    }

    public void printMaintenanceSummary(MaintenanceList maintenanceList) {
        long pending = maintenanceList.getAll().stream()
                .filter(m -> m.getStatus().equals("PENDING"))
                .count();
        System.out.println("\n=== Maintenance Summary ===");
        System.out.println("Pending repairs: " + pending + " / " + maintenanceList.getAll().size());
    }

    public void printFineSummary(FineList fineList) {
        System.out.println("\n=== Fine Summary ===");
        System.out.println("Total fines issued: " + fineList.getAll().size());
        System.out.printf("Pending:   %d (Rs.%.2f)%n", fineList.countByStatus("PENDING"), fineList.totalByStatus("PENDING"));
        System.out.printf("Collected: %d (Rs.%.2f)%n", fineList.countByStatus("PAID"), fineList.totalByStatus("PAID"));
        System.out.printf("Waived:    %d (Rs.%.2f)%n", fineList.countByStatus("WAIVED"), fineList.totalByStatus("WAIVED"));
    }
}
