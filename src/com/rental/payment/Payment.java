package com.rental.payment;

public class Payment {
    private String id;
    private String rentalId;
    private double amount;
    private String method;  // CASH, CARD, BANK TRANSFER ...
    private String status;  // PAID, REFUNDED or VOID
    private String type;    // RENTAL or FINE
    private String fineId;  // only set when type == FINE

    public Payment(String id, String rentalId, double amount, String method, String status) {
        this(id, rentalId, amount, method, status, "RENTAL", "");
    }

    public Payment(String id, String rentalId, double amount, String method, String status,
                   String type, String fineId) {
        this.id = id;
        this.rentalId = rentalId;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.type = (type == null || type.isBlank()) ? "RENTAL" : type;
        this.fineId = fineId == null ? "" : fineId;
    }

    public String getId() { return id; }
    public String getRentalId() { return rentalId; }
    public double getAmount() { return amount; }
    public String getMethod() { return method; }
    public String getStatus() { return status; }
    public String getType() { return type; }
    public String getFineId() { return fineId; }

    public void setAmount(double amount) { this.amount = amount; }
    public void setMethod(String method) { this.method = method; }
    public void setStatus(String status) { this.status = status; }

    public String toFileString() {
        return id + "," + rentalId + "," + amount + "," + method.replace(",", " ") + "," + status
                + "," + type + "," + fineId;
    }

    @Override
    public String toString() {
        return String.format("[%s] Rental:%s Type:%s Amount:Rs.%.2f Method:%s Status:%s",
                id, rentalId, type, amount, method, status);
    }
}
