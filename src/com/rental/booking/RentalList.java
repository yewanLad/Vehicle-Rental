package com.rental.booking;

import java.io.*;
import java.util.*;
import com.rental.vehicle.Vehicle;
import com.rental.vehicle.VehicleList;
import com.rental.user.Customer;
import com.rental.user.UserList;
import com.rental.common.IdGenerator;
import com.rental.common.VehicleNotAvailableException;
import com.rental.common.InvalidBookingException;

/**
 * This is where the "integration" between modules happens: creating a
 * booking looks up a Customer (user module) and a Vehicle (vehicle
 * module) directly through their public list classes.
 *
 * CRUD: createRental (create), findById/getAll (read),
 *       pickup / updateRental / returnVehicle / cancelRental (update),
 *       removeById (delete).
 */
public class RentalList {
    private List<Rental> rentals = new ArrayList<>();
    private static final String FILE_PATH = "data/rentals.txt";

    // ================= CREATE =================
    public Rental createRental(String customerId, String vehicleId, int days,
                               UserList userList, VehicleList vehicleList)
            throws VehicleNotAvailableException, InvalidBookingException {

        if (days <= 0) {
            throw new InvalidBookingException("Rental duration must be at least 1 day.");
        }

        Customer customer = userList.findCustomerById(customerId);
        if (customer == null) {
            throw new InvalidBookingException("Customer " + customerId + " not found.");
        }

        Vehicle vehicle = vehicleList.findById(vehicleId);
        if (vehicle == null) {
            throw new InvalidBookingException("Vehicle " + vehicleId + " not found.");
        }

        if (!vehicle.isAvailable()) {
            throw new VehicleNotAvailableException("Vehicle " + vehicleId + " is not available right now.");
        }

        double cost = Math.round(vehicle.calculateCost(days) * 100.0) / 100.0;
        String id = generateNextId();
        Rental rental = new Rental(id, customerId, vehicleId, days, cost, "CONFIRMED");

        rentals.add(rental);
        vehicle.setAvailable(false); // reserved for this booking

        return rental;
    }

    // ================= READ =================
    public Rental findById(String id) {
        if (id == null) return null;
        for (Rental r : rentals) {
            if (r.getId().equalsIgnoreCase(id)) return r;
        }
        return null;
    }

    public List<Rental> getAll() {
        return rentals;
    }

    /** True if the vehicle is booked (CONFIRMED) or out on the road (ACTIVE). */
    public boolean hasOpenRentalForVehicle(String vehicleId) {
        for (Rental r : rentals) {
            if (r.getVehicleId().equalsIgnoreCase(vehicleId) && isOpen(r)) return true;
        }
        return false;
    }

    public boolean hasOpenRentalForCustomer(String customerId) {
        for (Rental r : rentals) {
            if (r.getCustomerId().equalsIgnoreCase(customerId) && isOpen(r)) return true;
        }
        return false;
    }

    private boolean isOpen(Rental r) {
        return r.getStatus().equals("CONFIRMED") || r.getStatus().equals("ACTIVE");
    }

    // ================= UPDATE =================

    /** Admin hands the vehicle to the customer: CONFIRMED -> ACTIVE. */
    public boolean pickup(String rentalId) {
        Rental rental = findById(rentalId);
        if (rental == null || !rental.getStatus().equals("CONFIRMED")) {
            return false;
        }
        rental.setStatus("ACTIVE");
        return true;
    }

    /** Vehicle comes back: ACTIVE -> RETURNED and the vehicle becomes available again. */
    public boolean returnVehicle(String rentalId, VehicleList vehicleList) {
        Rental rental = findById(rentalId);
        if (rental == null || !rental.getStatus().equals("ACTIVE")) {
            return false;
        }
        rental.setStatus("RETURNED");
        Vehicle vehicle = vehicleList.findById(rental.getVehicleId());
        if (vehicle != null) {
            vehicle.setAvailable(true);
        }
        return true;
    }

    /** Cancel a booking that has not been collected yet: CONFIRMED -> CANCELLED, vehicle freed. */
    public boolean cancelRental(String rentalId, VehicleList vehicleList) {
        Rental rental = findById(rentalId);
        if (rental == null || !rental.getStatus().equals("CONFIRMED")) {
            return false;
        }
        rental.setStatus("CANCELLED");
        Vehicle vehicle = vehicleList.findById(rental.getVehicleId());
        if (vehicle != null) {
            vehicle.setAvailable(true);
        }
        return true;
    }

    /** Change the length / start date of a CONFIRMED booking and recalculate its cost. */
    public Rental updateRental(String rentalId, int days, String startDate, VehicleList vehicleList)
            throws InvalidBookingException {
        Rental rental = findById(rentalId);
        if (rental == null) {
            throw new InvalidBookingException("Booking " + rentalId + " not found.");
        }
        if (!rental.getStatus().equals("CONFIRMED")) {
            throw new InvalidBookingException("Only bookings that have not been collected yet can be edited.");
        }
        if (days <= 0) {
            throw new InvalidBookingException("Rental duration must be at least 1 day.");
        }
        Vehicle vehicle = vehicleList.findById(rental.getVehicleId());
        if (vehicle == null) {
            throw new InvalidBookingException("Vehicle " + rental.getVehicleId() + " no longer exists.");
        }
        rental.setDays(days);
        rental.setTotalCost(Math.round(vehicle.calculateCost(days) * 100.0) / 100.0);
        if (startDate != null && !startDate.isBlank()) rental.setStartDate(startDate);
        return rental;
    }

    // ================= DELETE =================
    public boolean removeById(String id) {
        return rentals.removeIf(r -> r.getId().equalsIgnoreCase(id));
    }

    public String generateNextId() {
        List<String> ids = new ArrayList<>();
        for (Rental r : rentals) ids.add(r.getId());
        return IdGenerator.next("R", ids);
    }

    public void saveToFile() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH))) {
            for (Rental r : rentals) {
                bw.write(r.toFileString());
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error saving rentals: " + e.getMessage());
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
                Rental r = new Rental(p[0], p[1], p[2], Integer.parseInt(p[3]), Double.parseDouble(p[4]), p[5]);
                if (p.length > 6 && !p[6].isBlank()) r.setStartDate(p[6]);
                rentals.add(r);
            }
        } catch (IOException e) {
            System.out.println("Error loading rentals: " + e.getMessage());
        }
    }
}
