package com.example.mylibrary.models;

public class BookCopy {
    private String id;
    private String bookId;
    private String barcode;
    private String status; // AVAILABLE, BORROWED, RESERVED, DAMAGED, LOST

    public BookCopy() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
