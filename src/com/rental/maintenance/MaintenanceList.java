package com.rental.maintenance;

import java.io.*;
import java.util.*;
import com.rental.common.IdGenerator;
import com.rental.vehicle.Vehicle;
import com.rental.vehicle.VehicleList;


public class MaintenanceList {
    private List<MaintenanceRecord> records = new ArrayList<>();
    private static final String FILE_PATH = "data/maintenance.txt";

    // ================= CREATE =================
    public MaintenanceRecord addRecord(String vehicleId, String issue, double cost, VehicleList vehicleList) {
        MaintenanceRecord record = new MaintenanceRecord(generateNextId(), vehicleId, issue, cost, "PENDING");
        records.add(record);

        Vehicle v = vehicleList.findById(vehicleId);
        if (v != null) {
            v.setAvailable(false);
        }
        return record;
    }

    // ================= READ =================
    public MaintenanceRecord findById(String id) {
        if (id == null) return null;
        for (MaintenanceRecord r : records) {
            if (r.getId().equalsIgnoreCase(id)) return r;
        }
        return null;
    }

    public List<MaintenanceRecord> getAll() {
        return records;
    }

    // ================= UPDATE =================
    public MaintenanceRecord updateRecord(String id, String issue, double cost) {
        MaintenanceRecord record = findById(id);
        if (record == null) return null;
        record.setIssueDescription(issue);
        record.setCost(cost);
        return record;
    }
    public boolean completeRecord(String id, VehicleList vehicleList) {
        MaintenanceRecord record = findById(id);
        if (record == null || record.getStatus().equals("COMPLETED")) {
            return false;
        }
        record.setStatus("COMPLETED");
        Vehicle v = vehicleList.findById(record.getVehicleId());
        if (v != null) {
            v.setAvailable(true);
        }
        return true;
    }

    // ================= DELETE =================

    public boolean deleteRecord(String id, VehicleList vehicleList) {
        MaintenanceRecord record = findById(id);
        if (record == null) return false;
        records.remove(record);

        if (record.getStatus().equals("PENDING")) {
            boolean stillInWorkshop = false;
            for (MaintenanceRecord other : records) {
                if (other.getVehicleId().equalsIgnoreCase(record.getVehicleId())
                        && other.getStatus().equals("PENDING")) {
                    stillInWorkshop = true;
                    break;
                }
            }
            Vehicle v = vehicleList.findById(record.getVehicleId());
            if (v != null && !stillInWorkshop) v.setAvailable(true);
        }
        return true;
    }

    public String generateNextId() {
        List<String> ids = new ArrayList<>();
        for (MaintenanceRecord r : records) ids.add(r.getId());
        return IdGenerator.next("M", ids);
    }
}