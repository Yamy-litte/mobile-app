package com.example.libraryonline.data.model;

public class LoanDetail {
    public String loanDetailId, loanReceiptId, copyId, borrowDate, dueDate, returnDate, detailStatus, originalDueDate, createdAt;
    public int extensionCount;
    public boolean extended;
    public LoanDetail() {}
}