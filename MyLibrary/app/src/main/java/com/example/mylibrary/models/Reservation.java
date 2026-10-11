package com.example.mylibrary.models;

public class Reservation {
    private String id;
    private String customerId;
    private String customerName;
    private String bookId;
    private String bookTitle;
    private String bookCopyId;
    private long reservationDate;
    private long expectedPickupDate;
    private long expirationDate;
    private String pickupLocation;
    private String reservationCode;
    private String status; // PENDING, APPROVED, CANCELLED, EXPIRED, FULFILLED

    public Reservation() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }

    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }

    public String getBookCopyId() { return bookCopyId; }
    public void setBookCopyId(String bookCopyId) { this.bookCopyId = bookCopyId; }

    public long getReservationDate() { return reservationDate; }
    public void setReservationDate(long reservationDate) { this.reservationDate = reservationDate; }

    public long getExpectedPickupDate() { return expectedPickupDate; }
    public void setExpectedPickupDate(long expectedPickupDate) { this.expectedPickupDate = expectedPickupDate; }

    public long getExpirationDate() { return expirationDate; }
    public void setExpirationDate(long expirationDate) { this.expirationDate = expirationDate; }

    public String getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(String pickupLocation) { this.pickupLocation = pickupLocation; }

    public String getReservationCode() { return reservationCode; }
    public void setReservationCode(String reservationCode) { this.reservationCode = reservationCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
