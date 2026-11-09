package com.rental.vehicle;

import java.io.*;
import java.util.*;
import com.rental.common.IdGenerator;


public class VehicleList {
    private List<Vehicle> vehicles = new ArrayList<>();
    private static final String FILE_PATH = "data/vehicles.txt";

    // ---- Create ----
    public void addVehicle(Vehicle v) {
        vehicles.add(v);
    }

    // ---- Read ----
    public Vehicle findById(String id) {
        for (Vehicle v : vehicles) {
            if (v.getId().equalsIgnoreCase(id)) return v;
        }
        return null;
    }

    public List<Vehicle> getAll() {
        return vehicles;
    }

    public List<Vehicle> getAvailable() {
        List<Vehicle> result = new ArrayList<>();
        for (Vehicle v : vehicles) {
            if (v.isAvailable()) result.add(v);
        }
        return result;
    }

    // ---- Update ----
    public Vehicle update(String id, String brand, String model, int year, double dailyRate) {
        Vehicle v = findById(id);
        if (v == null) return null;
        v.setBrand(brand);
        v.setModel(model);
        v.setYear(year);
        v.setDailyRate(dailyRate);
        return v;
    }

    // ---- Delete ----
    public boolean removeVehicle(String id) {
        return vehicles.removeIf(v -> v.getId().equalsIgnoreCase(id));
    }

    public String generateNextId() {
        List<String> ids = new ArrayList<>();
        for (Vehicle v : vehicles) ids.add(v.getId());
        return IdGenerator.next("V", ids);
    }

    public void saveToFile() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH))) {
            for (Vehicle v : vehicles) {
                bw.write(v.toFileString());
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error saving vehicles: " + e.getMessage());
        }
    }

    public void loadFromFile() {
        File file = new File(FILE_PATH);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);

                String id = parts[0];
                String brand = parts[1];
                String model = parts[2];
                int year = Integer.parseInt(parts[3]);
                double dailyRate = Double.parseDouble(parts[4]);
                boolean available = Boolean.parseBoolean(parts[5]);
                String type = parts[6];

                Vehicle v;
                switch (type) {
                    case "VAN":
                        v = new Van(id, brand, model, year, dailyRate, available);
                        break;
                    case "MOTORCYCLE":
                        v = new Motorcycle(id, brand, model, year, dailyRate, available);
                        break;
                    default:
                        v = new Car(id, brand, model, year, dailyRate, available);
                }
                vehicles.add(v);
            }
        } catch (IOException e) {
            System.out.println("Error loading vehicles: " + e.getMessage());
        }
    }
}
