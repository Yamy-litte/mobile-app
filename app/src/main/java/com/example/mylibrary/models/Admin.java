package com.example.mylibrary.models;

public class Admin {
    private String id;
    private String email;
    private String passwordHash;
    private String fullName;
    private String status;
    private long createdAt;

    public Admin() {
    }

    public Admin(String id, String email, String fullName, String status, long createdAt) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
