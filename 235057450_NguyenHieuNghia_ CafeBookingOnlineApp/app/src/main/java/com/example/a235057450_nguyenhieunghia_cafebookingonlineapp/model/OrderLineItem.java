package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class OrderLineItem {
    private final String menuName;
    private final int quantity;
    private final double unitPrice;

    public OrderLineItem(String menuName, int quantity, double unitPrice) {
        this.menuName = menuName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String getMenuName() { return menuName; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getSubtotal() { return quantity * unitPrice; }
}