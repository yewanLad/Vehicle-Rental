package com.rental.maintenance;

public class MaintenanceRecord {
    private String id;
    private String vehicleId;
    private String issueDescription;
    private double cost;
    private String status; // PENDING or COMPLETED

    public MaintenanceRecord(String id, String vehicleId, String issueDescription, double cost, String status) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.issueDescription = issueDescription;
        this.cost = cost;
        this.status = status;
    }

    public String getId() { return id; }
    public String getVehicleId() { return vehicleId; }
    public String getIssueDescription() { return issueDescription; }
    public double getCost() { return cost; }
    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }
    public void setIssueDescription(String issueDescription) { this.issueDescription = issueDescription; }
    public void setCost(double cost) { this.cost = cost; }
}