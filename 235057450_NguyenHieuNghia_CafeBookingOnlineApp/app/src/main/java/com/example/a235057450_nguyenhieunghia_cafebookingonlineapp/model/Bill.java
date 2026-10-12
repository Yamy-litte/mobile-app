package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class Bill {
    public static final String STATUS_AWAITING_CONFIRMATION = "awaiting confirmation";
    public static final String STATUS_IN_TRANSIT = "in transit";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";

    private final long id;
    private final long userId;
    private final String createdAt;
    private final String status;
    private final double totalAmount;

    public Bill(long id, long userId, String createdAt, String status, double totalAmount) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public long getId() { return id; }
    public long getUserId() { return userId; }
    public String getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public double getTotalAmount() { return totalAmount; }

    public boolean canBeCancelled() {
        return STATUS_AWAITING_CONFIRMATION.equalsIgnoreCase(status);
    }
}
