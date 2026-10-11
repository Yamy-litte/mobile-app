package com.example.mylibrary.models;

/**
 * Thông tin khách hàng được lưu trong collection Firestore "customers".
 * Tên thuộc tính dùng camelCase, thống nhất với quy ước Java.
 */
public class Customer {

    private String id;
    private String email;
    private String fullName;
    private String phone;
    private String address;
    private String status;
    private long createdAt;
    private long updatedAt;

    // Firestore cần constructor rỗng để ánh xạ document thành object.
    public Customer() {
    }

    public Customer(String id, String email, String fullName,
                    String phone, String address, String status,
                    long createdAt, long updatedAt) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.address = address;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
