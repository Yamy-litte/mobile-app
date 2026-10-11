
package com.example.mylibrary.model;

import com.google.firebase.Timestamp;

public class Category {

    private String id;
    private String name;
    private String description;
    private String status;
    private Timestamp updatedAt;

    public Category() {
    }

    public Category(
            String id,
            String name,
            String description,
            String status,
            Timestamp updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
