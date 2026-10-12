package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class Bill {
    public static final String STATUS_AWAITING_CONFIRMATION = "Awaiting Confirmation";
    public static final String STATUS_IN_TRANSIT = "In Transit";
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_CANCELLED = "Cancelled";

    private long billId;
    private long userId;
    private String dateOfBill;
    private String status;
    private double totalAmount;

    public Bill() {
        this.status = STATUS_AWAITING_CONFIRMATION;
    }

    public Bill(long billId, long userId, String dateOfBill, String status, double totalAmount) {
        this.billId = billId;
        this.userId = userId;
        this.dateOfBill = dateOfBill;
        this.status = status == null || status.trim().isEmpty()
                ? STATUS_AWAITING_CONFIRMATION : status;
        this.totalAmount = totalAmount;
    }

    public long getBillId() { return billId; }
    public void setBillId(long billId) { this.billId = billId; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    /** ISO-8601 date/time string, for example 2026-10-12T14:30:00. */
    public String getDateOfBill() { return dateOfBill; }
    public void setDateOfBill(String dateOfBill) { this.dateOfBill = dateOfBill; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
}
