package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class Menu {
    private int menuId;
    private String menuName;
    private String image;
    private int categoryId;
    private double price;
    private int stockQuantity;

    public Menu() {
    }

    public Menu(int menuId, String menuName, String image,
                int categoryId, double price, int stockQuantity) {
        this.menuId = menuId;
        this.menuName = menuName;
        this.image = image;
        this.categoryId = categoryId;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public int getMenuId() { return menuId; }
    public void setMenuId(int menuId) { this.menuId = menuId; }

    public String getMenuName() { return menuName; }
    public void setMenuName(String menuName) { this.menuName = menuName; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
}
