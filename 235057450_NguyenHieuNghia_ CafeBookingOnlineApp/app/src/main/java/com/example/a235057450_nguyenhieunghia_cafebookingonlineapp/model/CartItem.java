package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

/** One menu item and its selected quantity in the customer's cart. */
public class CartItem {
    private final Menu menu;
    private int quantity;

    public CartItem(Menu menu, int quantity) {
        if (menu == null) {
            throw new IllegalArgumentException("Menu item cannot be null.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        this.menu = menu;
        this.quantity = quantity;
    }

    public Menu getMenu() {
        return menu;
    }

    public int getMenuId() {
        return menu.getMenuId();
    }

    public String getMenuName() {
        return menu.getMenuName();
    }

    public String getImage() {
        return menu.getImage();
    }

    public double getUnitPrice() {
        return menu.getPrice();
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        this.quantity = quantity;
    }

    public double getSubtotal() {
        return menu.getPrice() * quantity;
    }
}
