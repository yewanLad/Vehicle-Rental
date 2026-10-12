package com.rental.user;

public abstract class User {
    protected String id;
    protected String username;
    protected String password;

    public User(String id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
    }

    // subclass reports what kind of user it is. 
    public abstract String getRole();

    public String getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }

    public boolean checkPassword(String attempt) {
        return password.equals(attempt);
    }

    protected static String clean(String s) {
        return s == null ? "" : s.replace(",", " ").trim();
    }

    public String toFileString() {
        return id + "," + clean(username) + "," + clean(password) + "," + getRole();
    }
}
