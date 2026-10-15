package com.example.libraryapp.model;

/**
 * Data model for the Fine entity described in the LibraryApp design.
 * Date/time values use ISO-8601 strings to align with SQLite TEXT columns.
 */
public class Fine {
    private int id;
    private int borrowRecordId;
    private double fineAmount;
    private String fineReason;
    private String status;
    private String createdAt;
    private String paidAt;

    public Fine() {
    }

    public Fine(int id, int borrowRecordId, double fineAmount, String fineReason, String status, String createdAt, String paidAt) {
        this.id = id;
        this.borrowRecordId = borrowRecordId;
        this.fineAmount = fineAmount;
        this.fineReason = fineReason;
        this.status = status;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBorrowRecordId() {
        return borrowRecordId;
    }

    public void setBorrowRecordId(int borrowRecordId) {
        this.borrowRecordId = borrowRecordId;
    }

    public double getFineAmount() {
        return fineAmount;
    }

    public void setFineAmount(double fineAmount) {
        this.fineAmount = fineAmount;
    }

    public String getFineReason() {
        return fineReason;
    }

    public void setFineReason(String fineReason) {
        this.fineReason = fineReason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(String paidAt) {
        this.paidAt = paidAt;
    }

}
