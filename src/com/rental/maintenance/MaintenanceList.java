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
}