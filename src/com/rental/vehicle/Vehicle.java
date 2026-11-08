package com.rental.vehicle;

public abstract class Vehicle {
    protected String id;
    protected String brand;
    protected String model;
    protected int year;
    protected double dailyRate;
    protected boolean available;
    protected String image = "";

    public Vehicle(String id, String brand, String model, int year, double dailyRate, boolean available) {
        this.id = id;
        this.brand = brand;
        this.model = model;
        this.year = year;
        this.dailyRate = dailyRate;
        this.available = available;
    }

    public abstract double calculateCost(int days);
    public abstract String getType();

    public String getId() { return id; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public int getYear() { return year; }
    public double getDailyRate() { return dailyRate; }
    public boolean isAvailable() { return available; }
    public String getImage() { return image; }

    public void setAvailable(boolean available) { this.available = available; }
    public void setBrand(String brand) { this.brand = brand; }
    public void setModel(String model) { this.model = model; }
    public void setYear(int year) { this.year = year; }
    public void setDailyRate(double dailyRate) { this.dailyRate = dailyRate; }
    public void setImage(String image) { this.image = image == null ? "" : image; }

    private static String clean(String s) {
        return s == null ? "" : s.replace(",", " ").trim();
    }

    public String toFileString() {
        return id + "," + clean(brand) + "," + clean(model) + "," + year + "," + dailyRate + "," + available + "," + getType() + "," + clean(image);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s %s %s (%d) - Rs.%.2f/day - %s",
                getType(), id, brand, model, year, dailyRate,
                available ? "Available" : "Not Available");
    }
}
