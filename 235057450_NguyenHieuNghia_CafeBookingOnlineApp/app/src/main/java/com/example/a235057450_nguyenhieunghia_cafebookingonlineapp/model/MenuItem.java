package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class MenuItem {
    private final long id;
    private final long categoryId;
    private final String name;
    private final String description;
    private final String imageUrl;
    private final double price;
    private final boolean available;

    public MenuItem(long id, long categoryId, String name, String description,
                    String imageUrl, double price, boolean available) {
        this.id = id;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
        this.available = available;
    }

    public long getId() { return id; }
    public long getCategoryId() { return categoryId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public double getPrice() { return price; }
    public boolean isAvailable() { return available; }
}
