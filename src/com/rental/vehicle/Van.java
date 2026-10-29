package com.rental.vehicle;

public class Van extends Vehicle {
    private static final double SURCHARGE = 1.15;

    public Van(String id, String brand, String model, int year, double dailyRate, boolean available) {
        super(id, brand, model, year, dailyRate, available);
    }

    @Override
    public double calculateCost(int days) {
        return dailyRate * days * SURCHARGE;
    }

    @Override
    public String getType() {
        return "VAN";
    }
}
