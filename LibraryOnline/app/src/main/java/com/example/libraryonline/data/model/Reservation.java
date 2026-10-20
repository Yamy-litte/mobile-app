package com.example.libraryonline.data.model;

public class Reservation {
    public String reservationId, userId, bookTitleId, copyId, reservationType, reservationStatus, createdAt, holdUntil, priorityUntil, notifiedAt, cancelledAt;
    public Integer queuePosition;
    public int requestedLoanDays = 14;
    public Reservation() {}
}