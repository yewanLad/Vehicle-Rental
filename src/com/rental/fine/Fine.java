package com.rental.fine;


public class Fine {
    private String id;
    private String rentalId;
    private String reason;      // LATE, DAMAGE or OTHER
    private double amount;
    private String status;      // PENDING, PAID or WAIVED
    private String description;

    public Fine(String id, String rentalId, String reason, double amount, String status, String description) {
        this.id = id;
        this.rentalId = rentalId;
        this.reason = reason;
        this.amount = amount;
        this.status = status;
        this.description = description == null ? "" : description;
    }

    public String getId() { return id; }
    public String getRentalId() { return rentalId; }
    public String getReason() { return reason; }
    public double getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getDescription() { return description; }

    public void setReason(String reason) { this.reason = reason; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setStatus(String status) { this.status = status; }
    public void setDescription(String description) { this.description = description == null ? "" : description; }

    public String toFileString() {
        return id + "," + rentalId + "," + reason + "," + amount + "," + status + ","
                + description.replace(",", ";").replace("\n", " ");
    }

    @Override
    public String toString() {
        return String.format("[%s] Booking:%s Reason:%s Amount:Rs.%.2f Status:%s",
                id, rentalId, reason, amount, status);
    }
}
