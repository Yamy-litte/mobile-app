package com.example.libraryonline.data.model;

public class Penalty {
    public String penaltyId, userId, loanDetailId, copyId, penaltyType, penaltyStatus, createdAt, paidAt;
    public int daysLate, amountDue, amountPaid;
    public Penalty() {}
}