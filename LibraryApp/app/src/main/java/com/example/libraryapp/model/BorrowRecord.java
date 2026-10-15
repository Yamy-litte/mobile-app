package com.example.libraryapp.model;

/**
 * Data model for the BorrowRecord entity described in the LibraryApp design.
 * Date/time values use ISO-8601 strings to align with SQLite TEXT columns.
 */
public class BorrowRecord {
    private int id;
    private int customerId;
    private int bookCopyId;
    private int adminBorrowId;
    private int adminReturnId;
    private String borrowDate;
    private String dueDate;
    private String returnDate;
    private String physicalConditionOnReturn;
    private String status;

    public BorrowRecord() {
    }

    public BorrowRecord(int id, int customerId, int bookCopyId, int adminBorrowId, int adminReturnId, String borrowDate, String dueDate, String returnDate, String physicalConditionOnReturn, String status) {
        this.id = id;
        this.customerId = customerId;
        this.bookCopyId = bookCopyId;
        this.adminBorrowId = adminBorrowId;
        this.adminReturnId = adminReturnId;
        this.borrowDate = borrowDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.physicalConditionOnReturn = physicalConditionOnReturn;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getBookCopyId() {
        return bookCopyId;
    }

    public void setBookCopyId(int bookCopyId) {
        this.bookCopyId = bookCopyId;
    }

    public int getAdminBorrowId() {
        return adminBorrowId;
    }

    public void setAdminBorrowId(int adminBorrowId) {
        this.adminBorrowId = adminBorrowId;
    }

    public int getAdminReturnId() {
        return adminReturnId;
    }

    public void setAdminReturnId(int adminReturnId) {
        this.adminReturnId = adminReturnId;
    }

    public String getBorrowDate() {
        return borrowDate;
    }

    public void setBorrowDate(String borrowDate) {
        this.borrowDate = borrowDate;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public String getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(String returnDate) {
        this.returnDate = returnDate;
    }

    public String getPhysicalConditionOnReturn() {
        return physicalConditionOnReturn;
    }

    public void setPhysicalConditionOnReturn(String physicalConditionOnReturn) {
        this.physicalConditionOnReturn = physicalConditionOnReturn;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
