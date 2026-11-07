package com.rental.fine;

import java.io.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.rental.booking.Rental;
import com.rental.booking.RentalList;
import com.rental.common.IdGenerator;
import com.rental.vehicle.Vehicle;

/**
 * Owns all fines and saves/loads them to data/fines.txt.
 * CRUD: addFine / createLateFine (create), findById / getAll / getByRental (read),
 *       update / markPaid / waive (update), remove (delete).
 */
public class FineList {
    public static final List<String> REASONS = List.of("LATE", "DAMAGE", "OTHER");

    /** Late fee per overdue day, as a fraction of the vehicle's daily rate. */
    public static final double LATE_FEE_RATE = 0.5;

    private List<Fine> fines = new ArrayList<>();
    private static final String FILE_PATH = "data/fines.txt";

    // ================= CREATE =================
    public Fine addFine(String rentalId, String reason, double amount, String description, RentalList rentalList) {
        Rental rental = rentalList.findById(rentalId);
        if (rental == null) {
            throw new IllegalArgumentException("Booking " + rentalId + " not found.");
        }
        String cleanReason = validate(reason, amount);
        Fine fine = new Fine(generateNextId(), rental.getId(), cleanReason, amount, "PENDING", description);
        fines.add(fine);
        return fine;
    }

    /**
     * Calculates a late-return fine for a rental being returned on "today".
     * Returns the new Fine, or null if the return is on time (or the start date is unknown).
     */
    public Fine createLateFine(Rental rental, Vehicle vehicle, LocalDate today) {
        if (rental.getStartDate() == null || vehicle == null) return null;
        LocalDate dueDate;
        try {
            dueDate = LocalDate.parse(rental.getStartDate()).plusDays(rental.getDays());
        } catch (Exception e) {
            return null;
        }
        long lateDays = ChronoUnit.DAYS.between(dueDate, today);
        if (lateDays <= 0) return null;

        double amount = Math.round(lateDays * vehicle.getDailyRate() * LATE_FEE_RATE * 100.0) / 100.0;
        String text = "Returned " + lateDays + " day(s) late (due " + dueDate + ")";
        Fine fine = new Fine(generateNextId(), rental.getId(), "LATE", amount, "PENDING", text);
        fines.add(fine);
        return fine;
    }

    // ================= READ =================
    public Fine findById(String id) {
        if (id == null) return null;
        for (Fine f : fines) {
            if (f.getId().equalsIgnoreCase(id)) return f;
        }
        return null;
    }

    public List<Fine> getAll() {
        return fines;
    }

    public List<Fine> getByRental(String rentalId) {
        List<Fine> result = new ArrayList<>();
        for (Fine f : fines) {
            if (f.getRentalId().equalsIgnoreCase(rentalId)) result.add(f);
        }
        return result;
    }

    public double totalByStatus(String status) {
        double total = 0;
        for (Fine f : fines) {
            if (f.getStatus().equals(status)) total += f.getAmount();
        }
        return total;
    }

    public int countByStatus(String status) {
        int n = 0;
        for (Fine f : fines) {
            if (f.getStatus().equals(status)) n++;
        }
        return n;
    }

    // ================= UPDATE =================
    /** Edit reason / amount / description. Only PENDING fines can be edited. */
    public Fine update(String id, String reason, double amount, String description) {
        Fine fine = requireFine(id);
        if (!fine.getStatus().equals("PENDING")) {
            throw new IllegalArgumentException("Only pending fines can be edited.");
        }
        String cleanReason = validate(reason, amount);
        fine.setReason(cleanReason);
        fine.setAmount(amount);
        fine.setDescription(description);
        return fine;
    }

    public Fine markPaid(String id) {
        Fine fine = requireFine(id);
        if (!fine.getStatus().equals("PENDING")) {
            throw new IllegalArgumentException("Only pending fines can be paid.");
        }
        fine.setStatus("PAID");
        return fine;
    }

    public Fine waive(String id) {
        Fine fine = requireFine(id);
        if (!fine.getStatus().equals("PENDING")) {
            throw new IllegalArgumentException("Only pending fines can be waived.");
        }
        fine.setStatus("WAIVED");
        return fine;
    }

    // ================= DELETE =================
    public void remove(String id) {
        Fine fine = requireFine(id);
        if (fine.getStatus().equals("PAID")) {
            throw new IllegalArgumentException("A paid fine cannot be deleted. Refund its payment first.");
        }
        fines.remove(fine);
    }

    /** Used when a booking is deleted. */
    public void removeByRental(String rentalId) {
        fines.removeIf(f -> f.getRentalId().equalsIgnoreCase(rentalId));
    }

    // ================= helpers =================
    private Fine requireFine(String id) {
        Fine fine = findById(id);
        if (fine == null) throw new IllegalArgumentException("Fine " + id + " not found.");
        return fine;
    }

    private String validate(String reason, double amount) {
        String r = reason == null ? "" : reason.trim().toUpperCase();
        if (!REASONS.contains(r)) {
            throw new IllegalArgumentException("Reason must be LATE, DAMAGE or OTHER.");
        }
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Fine amount must be greater than zero.");
        }
        return r;
    }

    public String generateNextId() {
        List<String> ids = new ArrayList<>();
        for (Fine f : fines) ids.add(f.getId());
        return IdGenerator.next("F", ids);
    }

    public void saveToFile() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH))) {
            for (Fine f : fines) {
                bw.write(f.toFileString());
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error saving fines: " + e.getMessage());
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
                String description = p.length > 5 ? p[5] : "";
                fines.add(new Fine(p[0], p[1], p[2], Double.parseDouble(p[3]), p[4], description));
            }
        } catch (IOException e) {
            System.out.println("Error loading fines: " + e.getMessage());
        }
    }
}
