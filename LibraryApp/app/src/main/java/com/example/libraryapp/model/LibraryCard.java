package com.example.libraryapp.model;

/**
 * Data model for the LibraryCard entity described in the LibraryApp design.
 * Date/time values use ISO-8601 strings to align with SQLite TEXT columns.
 */
public class LibraryCard {
    private int id;
    private int customerId;
    private String cardNumber;
    private String status;
    private String issueDate;

    public LibraryCard() {
    }

    public LibraryCard(int id, int customerId, String cardNumber, String status, String issueDate) {
        this.id = id;
        this.customerId = customerId;
        this.cardNumber = cardNumber;
        this.status = status;
        this.issueDate = issueDate;
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

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(String issueDate) {
        this.issueDate = issueDate;
    }

}
