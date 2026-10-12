package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class BillDetail {
    private final long id;
    private final long billId;
    private final long menuId;
    private final String menuName;
    private final int quantity;
    private final double unitPrice;

    public BillDetail(long id, long billId, long menuId, String menuName,
                      int quantity, double unitPrice) {
        this.id = id;
        this.billId = billId;
        this.menuId = menuId;
        this.menuName = menuName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public long getId() { return id; }
    public long getBillId() { return billId; }
    public long getMenuId() { return menuId; }
    public String getMenuName() { return menuName; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getLineTotal() { return quantity * unitPrice; }
}
