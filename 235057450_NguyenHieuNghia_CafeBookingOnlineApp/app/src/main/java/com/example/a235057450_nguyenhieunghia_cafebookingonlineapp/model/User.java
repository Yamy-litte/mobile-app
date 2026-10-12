package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class User {
    private final long id;
    private final String firebaseUid;
    private final String email;
    private final String fullName;
    private final String role;

    public User(long id, String firebaseUid, String email, String fullName, String role) {
        this.id = id;
        this.firebaseUid = firebaseUid;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    public long getId() { return id; }
    public String getFirebaseUid() { return firebaseUid; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
}
