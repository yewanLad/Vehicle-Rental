package com.rental.user;

public class Customer extends User {
    private String name;
    private String phone;
    private String licenseNumber;

    public Customer(String id, String username, String password, String name, String phone, String licenseNumber) {
        super(id, username, password);
        this.name = name;
        this.phone = phone;
        this.licenseNumber = licenseNumber;
    }

    @Override
    public String getRole() {
        return "CUSTOMER";
    }

    public String getName() { 
        return name; 
    }
    public String getPhone() { 
        return phone; 
    }
    public String getLicenseNumber() { 
        return licenseNumber; 
    }

    public void setName(String name) { 
        this.name = name; 
    }
    public void setPhone(String phone) { 
        this.phone = phone; 
    }
    public void setLicenseNumber(String licenseNumber) { 
        this.licenseNumber = licenseNumber; 
    }

    @Override
    public String toFileString() {
        return super.toFileString() + "," + clean(name) + "," + clean(phone) + "," + clean(licenseNumber);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (CUSTOMER) | Phone: %s | License: %s", id, name, phone, licenseNumber);
    }
}
