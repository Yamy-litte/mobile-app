package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class MenuItem {
    private long menuId;
    private long categoryId;
    private String menuName;
    private String description;
    private String imageUrl;
    private double price;
    private boolean available;

    public MenuItem() {
        this.available = true;
    }

    public MenuItem(long menuId, long categoryId, String menuName, String description,
                    String imageUrl, double price, boolean available) {
        this.menuId = menuId;
        this.categoryId = categoryId;
        this.menuName = menuName;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
        this.available = available;
    }

    public long getMenuId() { return menuId; }
    public void setMenuId(long menuId) { this.menuId = menuId; }

    public long getCategoryId() { return categoryId; }
    public void setCategoryId(long categoryId) { this.categoryId = categoryId; }

    public String getMenuName() { return menuName; }
    public void setMenuName(String menuName) { this.menuName = menuName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
