
package com.example.mylibrary.model;

public class Customer {

    private String id;
    private String email;
    private String full_name;
    private String phone_number;
    private String avatar_url;
    private String status;
    private long created_at;
    private long updated_at;

    // Constructor rỗng cho Firebase Firestore
    public Customer() {
    }

    // Constructor đầy đủ
    public Customer(String id, String email, String full_name,
                    String phone_number, String avatar_url,
                    String status, long created_at, long updated_at) {
        this.id = id;
        this.email = email;
        this.full_name = full_name;
        this.phone_number = phone_number;
        this.avatar_url = avatar_url;
        this.status = status;
        this.created_at = created_at;
        this.updated_at = updated_at;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFull_name() {
        return full_name;
    }

    public void setFull_name(String full_name) {
        this.full_name = full_name;
    }

    public String getPhone_number() {
        return phone_number;
    }

    public void setPhone_number(String phone_number) {
        this.phone_number = phone_number;
    }

    public String getAvatar_url() {
        return avatar_url;
    }

    public void setAvatar_url(String avatar_url) {
        this.avatar_url = avatar_url;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreated_at() {
        return created_at;
    }

    public void setCreated_at(long created_at) {
        this.created_at = created_at;
    }

    public long getUpdated_at() {
        return updated_at;
    }

    public void setUpdated_at(long updated_at) {
        this.updated_at = updated_at;
    }
}
