package com.rental.payment;

import java.io.*;
import java.util.*;
import com.rental.booking.Rental;
import com.rental.booking.RentalList;
import com.rental.common.IdGenerator;
import com.rental.common.InvalidBookingException;
import com.rental.fine.Fine;

/**
 * CRUD: makePayment / makeFinePayment (create), getAll / findById (read),
 *       update (edit details, refund, void), remove (delete).
 */
public class PaymentList {
    public static final List<String> STATUSES = List.of("PAID", "REFUNDED", "VOID");

    private List<Payment> payments = new ArrayList<>();
    private static final String FILE_PATH = "data/payments.txt";

    // ================= CREATE =================
    public Payment makePayment(String rentalId, String method, RentalList rentalList) throws InvalidBookingException {
        Rental rental = rentalList.findById(rentalId);
        if (rental == null) {
            throw new InvalidBookingException("Rental " + rentalId + " not found.");
        }

        Payment payment = new Payment(generateNextId(), rental.getId(), rental.getTotalCost(), method, "PAID");
        payments.add(payment);
        return payment;
    }

    /** Pays a fine (used by the Fine module). */
    public Payment makeFinePayment(Fine fine, String method) {
        Payment payment = new Payment(generateNextId(), fine.getRentalId(), fine.getAmount(),
                method, "PAID", "FINE", fine.getId());
        payments.add(payment);
        return payment;
    }

    // ================= READ =================
    public List<Payment> getAll() {
        return payments;
    }

    public Payment findById(String id) {
        if (id == null) return null;
        for (Payment p : payments) {
            if (p.getId().equalsIgnoreCase(id)) return p;
        }
        return null;
    }

    public List<Payment> getByRental(String rentalId) {
        List<Payment> result = new ArrayList<>();
        for (Payment p : payments) {
            if (p.getRentalId().equalsIgnoreCase(rentalId)) result.add(p);
        }
        return result;
    }

    /** Money actually kept: refunded and void payments are not counted. */
    public double getTotalRevenue() {
        double total = 0;
        for (Payment p : payments) {
            if (p.getStatus().equals("PAID")) total += p.getAmount();
        }
        return total;
    }

    public double getRevenueByType(String type) {
        double total = 0;
        for (Payment p : payments) {
            if (p.getStatus().equals("PAID") && p.getType().equals(type)) total += p.getAmount();
        }
        return total;
    }

    public int countByStatus(String status) {
        int n = 0;
        for (Payment p : payments) {
            if (p.getStatus().equals(status)) n++;
        }
        return n;
    }

    public double getTotalRefunded() {
        double total = 0;
        for (Payment p : payments) {
            if (p.getStatus().equals("REFUNDED")) total += p.getAmount();
        }
        return total;
    }

    // ================= UPDATE =================
    /** Any argument can be null to leave that field unchanged. */
    public Payment update(String id, String method, String status, Double amount) {
        Payment p = findById(id);
        if (p == null) throw new IllegalArgumentException("Payment " + id + " not found.");
        if (method != null && !method.isBlank()) p.setMethod(method.trim());
        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase();
            if (!STATUSES.contains(s)) {
                throw new IllegalArgumentException("Status must be PAID, REFUNDED or VOID.");
            }
            p.setStatus(s);
        }
        if (amount != null) {
            if (amount.isNaN() || amount.isInfinite() || amount <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero.");
            }
            p.setAmount(amount);
        }
        return p;
    }

    // ================= DELETE =================
    public boolean remove(String id) {
        return payments.removeIf(p -> p.getId().equalsIgnoreCase(id));
    }

    public void removeByRental(String rentalId) {
        payments.removeIf(p -> p.getRentalId().equalsIgnoreCase(rentalId));
    }

    public String generateNextId() {
        List<String> ids = new ArrayList<>();
        for (Payment p : payments) ids.add(p.getId());
        return IdGenerator.next("P", ids);
    }

    public void saveToFile() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH))) {
            for (Payment p : payments) {
                bw.write(p.toFileString());
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error saving payments: " + e.getMessage());
        }
    }

    public void loadFromFile() {
        File file = new File(FILE_PATH);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",", -1);
                String type = p.length > 5 ? p[5] : "RENTAL";
                String fineId = p.length > 6 ? p[6] : "";
                payments.add(new Payment(p[0], p[1], Double.parseDouble(p[2]), p[3], p[4], type, fineId));
            }
        } catch (IOException e) {
            System.out.println("Error loading payments: " + e.getMessage());
        }
    }
}
