package com.rental.user;

public class Admin extends User {
    private String name;

    public Admin(String id, String username, String password, String name) {
        super(id, username, password);
        this.name = name;
    }

    // tells the role
    @Override
    public String getRole() {
        return "ADMIN";
    }

    //getter and setter for name
    public String getName() { 
        return name; 
    }
    public void setName(String name) { 
        this.name = name; 
    }

    @Override
    public String toFileString() {
        return super.toFileString() + "," + clean(name);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (ADMIN)", id, name);
    }
}
