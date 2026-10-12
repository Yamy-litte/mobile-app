package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

/**
 * Represents an application user.
 * Firebase Authentication manages credentials; this model stores profile and role data only.
 */
public class User {
    public static final String ROLE_CUSTOMER = "Customer";
    public static final String ROLE_ADMIN = "Administrator";

    private long userId;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private String role;

    public User() {
        this.role = ROLE_CUSTOMER;
    }

    public User(long userId, String fullName, String email, String phone,
                String address, String role) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.role = role == null || role.trim().isEmpty() ? ROLE_CUSTOMER : role;
    }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getRole() { return role; }
    public void setRole(String role) {
        this.role = role == null || role.trim().isEmpty() ? ROLE_CUSTOMER : role;
    }
}
