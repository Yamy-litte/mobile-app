package com.example.libraryapp.model;

/**
 * Data model for the BookCopy entity described in the LibraryApp design.
 * Date/time values use ISO-8601 strings to align with SQLite TEXT columns.
 */
public class BookCopy {
    private int id;
    private int bookId;
    private String barcode;
    private String status;

    public BookCopy() {
    }

    public BookCopy(int id, int bookId, String barcode, String status) {
        this.id = id;
        this.bookId = bookId;
        this.barcode = barcode;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBookId() {
        return bookId;
    }

    public void setBookId(int bookId) {
        this.bookId = bookId;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
