package com.example.libraryapp.model;

/**
 * Data model for the Extension entity described in the LibraryApp design.
 * Date/time values use ISO-8601 strings to align with SQLite TEXT columns.
 */
public class Extension {
    private int id;
    private int borrowRecordId;
    private String requestDate;
    private int extendedDays;
    private String status;
    private String notes;

    public Extension() {
    }

    public Extension(int id, int borrowRecordId, String requestDate, int extendedDays, String status, String notes) {
        this.id = id;
        this.borrowRecordId = borrowRecordId;
        this.requestDate = requestDate;
        this.extendedDays = extendedDays;
        this.status = status;
        this.notes = notes;
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

    public String getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(String requestDate) {
        this.requestDate = requestDate;
    }

    public int getExtendedDays() {
        return extendedDays;
    }

    public void setExtendedDays(int extendedDays) {
        this.extendedDays = extendedDays;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

}
