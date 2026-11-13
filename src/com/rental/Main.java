package com.rental;

import java.util.Scanner;
import com.rental.vehicle.*;
import com.rental.user.*;
import com.rental.booking.*;
import com.rental.payment.*;
import com.rental.maintenance.*;
import com.rental.report.*;
import com.rental.common.*;


public class Main {
    static Scanner sc = new Scanner(System.in);

    static VehicleList vehicleList = new VehicleList();
    static UserList userList = new UserList();
    static RentalList rentalList = new RentalList();
    static PaymentList paymentList = new PaymentList();
    static MaintenanceList maintenanceList = new MaintenanceList();
    static ReportGenerator reportGenerator = new ReportGenerator();

    public static void main(String[] args) {
        new java.io.File("data").mkdirs();
        loadAll();

        boolean running = true;
        while (running) {
            System.out.println("\n===== VEHICLE RENTAL MANAGEMENT SYSTEM =====");
            System.out.println("1. Vehicle Management");
            System.out.println("2. User Management");
            System.out.println("3. Booking / Rental");
            System.out.println("4. Payment");
            System.out.println("5. Maintenance");
            System.out.println("6. Reports");
            System.out.println("0. Save & Exit");
            System.out.print("Choose an option: ");

            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1": vehicleMenu(); break;
                case "2": userMenu(); break;
                case "3": bookingMenu(); break;
                case "4": paymentMenu(); break;
                case "5": maintenanceMenu(); break;
                case "6": reportMenu(); break;
                case "0":
                    saveAll();
                    running = false;
                    System.out.println("Data saved. Goodbye!");
                    break;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    // ---------------- Vehicle ----------------
    static void vehicleMenu() {
        System.out.println("\n-- Vehicle Management --");
        System.out.println("1. Add Vehicle  2. View All  3. View Available  4. Remove Vehicle  0. Back");
        System.out.print("Choose: ");
        switch (sc.nextLine().trim()) {
            case "1": addVehicle(); break;
            case "2":
                if (vehicleList.getAll().isEmpty()) System.out.println("No vehicles yet.");
                vehicleList.getAll().forEach(System.out::println);
                break;
            case "3":
                if (vehicleList.getAvailable().isEmpty()) System.out.println("No available vehicles.");
                vehicleList.getAvailable().forEach(System.out::println);
                break;
            case "4":
                System.out.print("Vehicle ID to remove: ");
                String id = sc.nextLine().trim();
                System.out.println(vehicleList.removeVehicle(id) ? "Removed." : "Not found.");
                break;
        }
    }

    static void addVehicle() {
        System.out.print("Type (car/van/motorcycle): ");
        String type = sc.nextLine().trim().toLowerCase();
        System.out.print("Brand: ");
        String brand = sc.nextLine().trim();
        System.out.print("Model: ");
        String model = sc.nextLine().trim();
        System.out.print("Year: ");
        int year = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Daily Rate: ");
        double rate = Double.parseDouble(sc.nextLine().trim());

        String id = vehicleList.generateNextId();
        Vehicle v;
        switch (type) {
            case "van": v = new Van(id, brand, model, year, rate, true); break;
            case "motorcycle": v = new Motorcycle(id, brand, model, year, rate, true); break;
            default: v = new Car(id, brand, model, year, rate, true);
        }
        vehicleList.addVehicle(v);
        System.out.println("Added: " + v);
    }

    // ---------------- User ----------------
    static void userMenu() {
        System.out.println("\n-- User Management --");
        System.out.println("1. Register Customer  2. Register Admin  3. Login  4. View All Users  0. Back");
        System.out.print("Choose: ");
        switch (sc.nextLine().trim()) {
            case "1": registerCustomer(); break;
            case "2": registerAdmin(); break;
            case "3": login(); break;
            case "4":
                if (userList.getAll().isEmpty()) System.out.println("No users yet.");
                userList.getAll().forEach(System.out::println);
                break;
        }
    }

    static void registerCustomer() {
        System.out.print("Username: ");
        String username = sc.nextLine().trim();
        if (userList.isUsernameTaken(username)) {
            System.out.println("That username is already taken.");
            return;
        }
        System.out.print("Password: ");
        String password = sc.nextLine().trim();
        System.out.print("Full Name: ");
        String name = sc.nextLine().trim();
        System.out.print("Phone: ");
        String phone = sc.nextLine().trim();
        System.out.print("License Number: ");
        String license = sc.nextLine().trim();

        Customer c = new Customer(userList.generateNextId(), username, password, name, phone, license);
        userList.register(c);
        System.out.println("Registered: " + c);
    }

    static void registerAdmin() {
        System.out.print("Username: ");
        String username = sc.nextLine().trim();
        if (userList.isUsernameTaken(username)) {
            System.out.println("That username is already taken.");
            return;
        }
        System.out.print("Password: ");
        String password = sc.nextLine().trim();
        System.out.print("Full Name: ");
        String name = sc.nextLine().trim();

        Admin a = new Admin(userList.generateNextId(), username, password, name);
        userList.register(a);
        System.out.println("Registered: " + a);
    }

    static void login() {
        System.out.print("Username: ");
        String username = sc.nextLine().trim();
        System.out.print("Password: ");
        String password = sc.nextLine().trim();

        User u = userList.login(username, password);
        if (u == null) {
            System.out.println("Invalid username or password.");
        } else {
            System.out.println("Welcome, " + u.getUsername() + "! Logged in as " + u.getRole() + ". Your ID is " + u.getId() + ".");
        }
    }

    // ---------------- Booking ----------------
    static void bookingMenu() {
        System.out.println("\n-- Booking / Rental --");
        System.out.println("1. Create Booking  2. Return Vehicle  3. View All Rentals  0. Back");
        System.out.print("Choose: ");
        switch (sc.nextLine().trim()) {
            case "1": createBooking(); break;
            case "2":
                System.out.print("Rental ID to return: ");
                String id = sc.nextLine().trim();
                System.out.println(rentalList.returnVehicle(id, vehicleList)
                        ? "Vehicle returned."
                        : "Rental not found or already returned.");
                break;
            case "3":
                if (rentalList.getAll().isEmpty()) System.out.println("No rentals yet.");
                rentalList.getAll().forEach(System.out::println);
                break;
        }
    }

    static void createBooking() {
        System.out.print("Customer ID: ");
        String custId = sc.nextLine().trim();
        System.out.print("Vehicle ID: ");
        String vehId = sc.nextLine().trim();
        System.out.print("Number of days: ");
        int days = Integer.parseInt(sc.nextLine().trim());

        try {
            Rental rental = rentalList.createRental(custId, vehId, days, userList, vehicleList);
            System.out.println("Booking created: " + rental);
        } catch (VehicleNotAvailableException | InvalidBookingException e) {
            System.out.println("Booking failed: " + e.getMessage());
        }
    }

    // ---------------- Payment ----------------
    static void paymentMenu() {
        System.out.println("\n-- Payment --");
        System.out.println("1. Make Payment  2. View All Payments  0. Back");
        System.out.print("Choose: ");
        switch (sc.nextLine().trim()) {
            case "1":
                System.out.print("Rental ID: ");
                String rentalId = sc.nextLine().trim();
                System.out.print("Method (cash/card): ");
                String method = sc.nextLine().trim().toUpperCase();
                try {
                    Payment p = paymentList.makePayment(rentalId, method, rentalList);
                    System.out.println("Payment recorded: " + p);
                } catch (InvalidBookingException e) {
                    System.out.println("Payment failed: " + e.getMessage());
                }
                break;
            case "2":
                if (paymentList.getAll().isEmpty()) System.out.println("No payments yet.");
                paymentList.getAll().forEach(System.out::println);
                break;
        }
    }

    // ---------------- Maintenance ----------------
    static void maintenanceMenu() {
        System.out.println("\n-- Maintenance --");
        System.out.println("1. Report Issue  2. Mark Completed  3. View All  0. Back");
        System.out.print("Choose: ");
        switch (sc.nextLine().trim()) {
            case "1":
                System.out.print("Vehicle ID: ");
                String vehId = sc.nextLine().trim();
                System.out.print("Issue description: ");
                String issue = sc.nextLine().trim();
                System.out.print("Estimated cost: ");
                double cost = Double.parseDouble(sc.nextLine().trim());
                MaintenanceRecord m = maintenanceList.addRecord(vehId, issue, cost, vehicleList);
                System.out.println("Logged: " + m);
                break;
            case "2":
                System.out.print("Maintenance ID: ");
                String id = sc.nextLine().trim();
                System.out.println(maintenanceList.completeRecord(id, vehicleList)
                        ? "Marked completed."
                        : "Not found or already completed.");
                break;
            case "3":
                if (maintenanceList.getAll().isEmpty()) System.out.println("No maintenance records yet.");
                maintenanceList.getAll().forEach(System.out::println);
                break;
        }
    }

    // ---------------- Reports ----------------
    static void reportMenu() {
        reportGenerator.printRevenueReport(paymentList);
        reportGenerator.printMostRentedVehicle(rentalList, vehicleList);
        reportGenerator.printAvailabilitySummary(vehicleList);
        reportGenerator.printMaintenanceSummary(maintenanceList);
    }

    // ---------------- Persistence ----------------
    static void loadAll() {
        vehicleList.loadFromFile();
        userList.loadFromFile();
        rentalList.loadFromFile();
        paymentList.loadFromFile();
        maintenanceList.loadFromFile();
    }

    static void saveAll() {
        vehicleList.saveToFile();
        userList.saveToFile();
        rentalList.saveToFile();
        paymentList.saveToFile();
        maintenanceList.saveToFile();
    }
}
