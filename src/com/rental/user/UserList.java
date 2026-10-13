package com.rental.user;

import java.util.ArrayList;
import java.util.List;
import java.io.*;

public class UserList {
    private List<User> users = new ArrayList<>();
    private static final String FILE_PATH = "data/users.txt";

    //create
    public void register(User u){
        users.add(u);
    }

    //read
    public User login(String username, String password){
        if (password == null || username == null) 
            return null;
        for(User u : users){
            if (u.getUsername().equalsIgnoreCase(username) && u.checkPassword(password))
                return u;
        }
        return null;
    }

 public boolean isUsernameTaken(String username) {
        return findByUsername(username) != null;
    }

    public User findByUsername(String username) {
        if (username == null) return null;
        for (User u : users) {
            if (u.getUsername().equalsIgnoreCase(username)) return u;
        }
        return null;
    }

    public User findById(String id) {
        if (id == null) return null;
        for (User u : users) {
            if (u.getId().equalsIgnoreCase(id)) return u;
        }
        return null;
    }

    public Customer findCustomerById(String id) {
        User u = findById(id);
        return (u instanceof Customer) ? (Customer) u : null;
    }

    public List<User> getAll() {
        return users;
    }

    // ---- Delete ----
    public boolean removeById(String id) {
        return users.removeIf(u -> u.getId().equalsIgnoreCase(id));
    }

    public String generateNextId() {
        List<String> ids = new ArrayList<>();
        String prefix = "U";
        for (User u : users) {
            ids.add(u.getId());
        }
        if (!users.isEmpty()) {
            String first = users.get(0).getId();
            String letters = first.replaceAll("[0-9]", "");
            if (!letters.isEmpty()) prefix = letters;
        }
        return IdGenerator.next(prefix, ids);
    }

    // File saving + loading

    public void saveToFile() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH))) {
            for (User u : users) {
                bw.write(u.toFileString());
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error saving users: " + e.getMessage());
        }
    }

    public void loadFromFile() {
        File file = new File(FILE_PATH);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",", -1);
                if (p.length < 4) continue;
                if (p[3].equals("ADMIN")) {
                    users.add(new Admin(p[0], p[1], p[2], at(p, 4)));
                } else {
                    users.add(new Customer(p[0], p[1], p[2], at(p, 4), at(p, 5), at(p, 6)));
                }
            }
        } catch (IOException e) {
            System.out.println("Error loading users: " + e.getMessage());
        }
    }

    private static String at(String[] p, int i) {
        return i < p.length ? p[i] : "";
    }
}
