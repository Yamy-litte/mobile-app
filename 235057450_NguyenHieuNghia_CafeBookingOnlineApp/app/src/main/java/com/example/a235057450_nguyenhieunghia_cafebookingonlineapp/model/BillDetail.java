package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class BillDetail {
    private long billDetailId;
    private long billId;
    private long menuId;
    private int quantity;
    /** Price copied from the menu when the order is placed. */
    private double unitPrice;

    public BillDetail() {
    }

    public BillDetail(long billDetailId, long billId, long menuId,
                      int quantity, double unitPrice) {
        this.billDetailId = billDetailId;
        this.billId = billId;
        this.menuId = menuId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public long getBillDetailId() { return billDetailId; }
    public void setBillDetailId(long billDetailId) { this.billDetailId = billDetailId; }

    public long getBillId() { return billId; }
    public void setBillId(long billId) { this.billId = billId; }

    public long getMenuId() { return menuId; }
    public void setMenuId(long menuId) { this.menuId = menuId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    public double getLineTotal() { return quantity * unitPrice; }
}
