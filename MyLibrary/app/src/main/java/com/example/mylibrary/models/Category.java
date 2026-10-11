package com.example.mylibrary.models;

public class Category {
    private String id;
    private String categoryName;
    private String description;

    public Category() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
