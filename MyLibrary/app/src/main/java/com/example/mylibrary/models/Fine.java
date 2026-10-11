package com.example.mylibrary.models;

public class Fine {
    private String id;
    private String borrowRecordId;
    private double fineAmount;
    private String fineReason;
    private String status;
    private long createdAt;
    private long paidAt;

    public Fine() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBorrowRecordId() { return borrowRecordId; }
    public void setBorrowRecordId(String borrowRecordId) { this.borrowRecordId = borrowRecordId; }
    public double getFineAmount() { return fineAmount; }
    public void setFineAmount(double fineAmount) { this.fineAmount = fineAmount; }
    public String getFineReason() { return fineReason; }
    public void setFineReason(String fineReason) { this.fineReason = fineReason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getPaidAt() { return paidAt; }
    public void setPaidAt(long paidAt) { this.paidAt = paidAt; }
}
