package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class Category {
    private final long id;
    private final String name;
    private final String description;

    public Category(long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
