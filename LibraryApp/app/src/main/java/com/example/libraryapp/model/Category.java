package com.example.libraryapp.model;

/**
 * Data model for the Category entity described in the LibraryApp design.
 * Date/time values use ISO-8601 strings to align with SQLite TEXT columns.
 */
public class Category {
    private int id;
    private String categoryName;
    private String description;

    public Category() {
    }

    public Category(int id, String categoryName, String description) {
        this.id = id;
        this.categoryName = categoryName;
        this.description = description;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
