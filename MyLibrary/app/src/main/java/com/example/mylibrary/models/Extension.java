package com.example.mylibrary.models;

public class Extension {
    private String id;
    private String borrowRecordId;
    private long requestDate;
    private int extendedDays;
    private String status;
    private String notes;

    public Extension() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBorrowRecordId() { return borrowRecordId; }
    public void setBorrowRecordId(String borrowRecordId) { this.borrowRecordId = borrowRecordId; }
    public long getRequestDate() { return requestDate; }
    public void setRequestDate(long requestDate) { this.requestDate = requestDate; }
    public int getExtendedDays() { return extendedDays; }
    public void setExtendedDays(int extendedDays) { this.extendedDays = extendedDays; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
