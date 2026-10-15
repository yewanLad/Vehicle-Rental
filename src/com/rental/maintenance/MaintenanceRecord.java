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

    public String toFileString() {
        // commas inside the description would break the simple CSV format, so replace them
        return id + "," + vehicleId + "," + issueDescription.replace(",", ";") + "," + cost + "," + status;
    }

    @Override
    public String toString() {
        return String.format("[%s] Vehicle:%s Issue:%s Cost:Rs.%.2f Status:%s",
                id, vehicleId, issueDescription, cost, status);
    }
}