package com.rental.vehicle;

public class Car extends Vehicle {
    public Car(String id, String brand, String model, int year, double dailyRate, boolean available) {
        super(id, brand, model, year, dailyRate, available);
    }

    @Override
    public double calculateCost(int days) {
        return dailyRate * days;
    }

    @Override
    public String getType() {
        return "CAR";
    }
}
