package com.rental.booking;

public class Rental {
    private String id;
    private String customerId;
    private String vehicleId;
    private int days;
    private double totalCost;

    private String status;
    private String startDate; // yyyy-MM-dd (null for records created before dates were stored)

    public Rental(String id, String customerId, String vehicleId, int days, double totalCost, String status) {
        this.id = id;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.days = days;
        this.totalCost = totalCost;
        this.status = status;
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getVehicleId() { return vehicleId; }
    public int getDays() { return days; }
    public double getTotalCost() { return totalCost; }
    public String getStatus() { return status; }
    public String getStartDate() { return startDate; }

    public void setStatus(String status) { this.status = status; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public void setDays(int days) { this.days = days; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }

    public String toFileString() {
        return id + "," + customerId + "," + vehicleId + "," + days + "," + totalCost + "," + status
                + "," + (startDate == null ? "" : startDate);
    }

    @Override
    public String toString() {
        return String.format("[%s] Customer:%s Vehicle:%s Days:%d Cost:Rs.%.2f Status:%s",
                id, customerId, vehicleId, days, totalCost, status);
    }
}
