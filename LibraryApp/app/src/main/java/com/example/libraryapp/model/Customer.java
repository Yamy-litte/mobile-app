package com.example.libraryapp.model;

/**
 * Local Reader profile linked to Firebase Authentication.
 * Firebase UID identifies the account; passwords are never stored in this model.
 */
public class Customer {
    private int id;
    private String firebaseUid;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String avatarUrl;
    private String status;
    private String createdAt;
    private String updatedAt;

    public Customer() {
    }

    public Customer(int id, String firebaseUid, String email, String fullName,
                    String phoneNumber, String avatarUrl, String status,
                    String createdAt, String updatedAt) {
        this.id = id;
        this.firebaseUid = firebaseUid;
        this.email = email;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.avatarUrl = avatarUrl;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFirebaseUid() { return firebaseUid; }
    public void setFirebaseUid(String firebaseUid) { this.firebaseUid = firebaseUid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
