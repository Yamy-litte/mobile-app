package com.example.libraryonline.data.model;

/** Local reader/admin profile. Firebase Authentication owns credentials; never store passwords here. */
public class User {
    public String userId, fullName, dateOfBirth, email, phone, barcode, role, cardStatus, cardExpiryDate, createdAt, updatedAt;
    public boolean isDeleted;

    public User() {}

    public User(String userId, String fullName, String email, String createdAt, String updatedAt) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.role = "Reader";
        this.cardStatus = "ChoDuyet";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}