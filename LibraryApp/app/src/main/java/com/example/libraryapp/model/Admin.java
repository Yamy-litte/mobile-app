package com.example.libraryapp.model;

/** Local administrator profile linked to Firebase Authentication. */
public class Admin {
    private int id;
    private String firebaseUid;
    private String email;
    private String fullName;
    private String status;
    private String createdAt;

    public Admin() {}

    public Admin(int id, String firebaseUid, String email, String fullName,
                 String status, String createdAt) {
        this.id = id;
        this.firebaseUid = firebaseUid;
        this.email = email;
        this.fullName = fullName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getFirebaseUid() { return firebaseUid; }
    public void setFirebaseUid(String firebaseUid) { this.firebaseUid = firebaseUid; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
