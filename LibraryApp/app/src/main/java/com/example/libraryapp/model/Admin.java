package com.example.libraryapp.model;

/**
 * Data model for the Admin entity described in the LibraryApp design.
 * Date/time values use ISO-8601 strings to align with SQLite TEXT columns.
 */
public class Admin {
    private int id;
    private String email;
    private String passwordHash;
    private String fullName;
    private String status;
    private String createdAt;

    public Admin() {
    }

    public Admin(int id, String email, String passwordHash, String fullName, String status, String createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

}
