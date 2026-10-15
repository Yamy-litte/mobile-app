package com.example.libraryapp.model;

/**
 * Data model for the Reservation entity described in the LibraryApp design.
 * Date/time values use ISO-8601 strings to align with SQLite TEXT columns.
 */
public class Reservation {
    private int id;
    private int customerId;
    private int bookId;
    private int bookCopyId;
    private String reservationDate;
    private String status;
    private String expirationDate;

    public Reservation() {
    }

    public Reservation(int id, int customerId, int bookId, int bookCopyId, String reservationDate, String status, String expirationDate) {
        this.id = id;
        this.customerId = customerId;
        this.bookId = bookId;
        this.bookCopyId = bookCopyId;
        this.reservationDate = reservationDate;
        this.status = status;
        this.expirationDate = expirationDate;
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

    public int getBookId() {
        return bookId;
    }

    public void setBookId(int bookId) {
        this.bookId = bookId;
    }

    public int getBookCopyId() {
        return bookCopyId;
    }

    public void setBookCopyId(int bookCopyId) {
        this.bookCopyId = bookCopyId;
    }

    public String getReservationDate() {
        return reservationDate;
    }

    public void setReservationDate(String reservationDate) {
        this.reservationDate = reservationDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

}
