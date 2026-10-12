package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class User {
    private int userId;
    private String email;
    private String role;

    public User() {
    }

    public User(int userId, String email, String role) {
        this.userId = userId;
        this.email = email;
        this.role = role;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
