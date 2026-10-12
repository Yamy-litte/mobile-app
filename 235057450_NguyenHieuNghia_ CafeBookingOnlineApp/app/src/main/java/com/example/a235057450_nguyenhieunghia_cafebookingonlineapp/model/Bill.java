package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class Bill {
    private int billId;
    private int userId;
    private String dateOfBill;
    private String status;
    private double totalAmount;

    public Bill() {
    }

    public Bill(int billId, int userId, String dateOfBill,
                String status, double totalAmount) {
        this.billId = billId;
        this.userId = userId;
        this.dateOfBill = dateOfBill;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public int getBillId() { return billId; }
    public void setBillId(int billId) { this.billId = billId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getDateOfBill() { return dateOfBill; }
    public void setDateOfBill(String dateOfBill) { this.dateOfBill = dateOfBill; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
}
