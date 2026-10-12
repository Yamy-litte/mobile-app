package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class BillDetail {
    private int billDetailId;
    private int menuId;
    private int billId;
    private int quantity;
    private double price;

    public BillDetail() {
    }

    public BillDetail(int billDetailId, int menuId, int billId,
                      int quantity, double price) {
        this.billDetailId = billDetailId;
        this.menuId = menuId;
        this.billId = billId;
        this.quantity = quantity;
        this.price = price;
    }

    public int getBillDetailId() { return billDetailId; }
    public void setBillDetailId(int billDetailId) { this.billDetailId = billDetailId; }

    public int getMenuId() { return menuId; }
    public void setMenuId(int menuId) { this.menuId = menuId; }

    public int getBillId() { return billId; }
    public void setBillId(int billId) { this.billId = billId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}
