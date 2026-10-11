package com.example.mylibrary.models;

public class LibraryCard {
    private String id;
    private String customerId;
    private String cardNumber;
    private String status;
    private long issuedAt;

    public LibraryCard() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getIssuedAt() { return issuedAt; }
    public void setIssuedAt(long issuedAt) { this.issuedAt = issuedAt; }
}
