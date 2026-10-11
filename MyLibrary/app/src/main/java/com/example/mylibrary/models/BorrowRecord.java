package com.example.mylibrary.models;

public class BorrowRecord {
    private String id;
    private String customerId;
    private String customerName;
    private String bookCopyId;
    private String bookId;
    private String bookTitle;
    private String bookAuthor;
    private String adminBorrowId;
    private String adminReturnId;
    private long borrowDate;
    private long dueDate;
    private long returnDate;
    private String physicalConditionOnReturn;
    private String status; // BORROWED, RETURNED, OVERDUE

    public BorrowRecord() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getBookCopyId() { return bookCopyId; }
    public void setBookCopyId(String bookCopyId) { this.bookCopyId = bookCopyId; }

    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }

    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }

    public String getBookAuthor() { return bookAuthor; }
    public void setBookAuthor(String bookAuthor) { this.bookAuthor = bookAuthor; }

    public String getAdminBorrowId() { return adminBorrowId; }
    public void setAdminBorrowId(String adminBorrowId) { this.adminBorrowId = adminBorrowId; }

    public String getAdminReturnId() { return adminReturnId; }
    public void setAdminReturnId(String adminReturnId) { this.adminReturnId = adminReturnId; }

    public long getBorrowDate() { return borrowDate; }
    public void setBorrowDate(long borrowDate) { this.borrowDate = borrowDate; }

    public long getDueDate() { return dueDate; }
    public void setDueDate(long dueDate) { this.dueDate = dueDate; }

    public long getReturnDate() { return returnDate; }
    public void setReturnDate(long returnDate) { this.returnDate = returnDate; }

    public String getPhysicalConditionOnReturn() { return physicalConditionOnReturn; }
    public void setPhysicalConditionOnReturn(String physicalConditionOnReturn) { this.physicalConditionOnReturn = physicalConditionOnReturn; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
