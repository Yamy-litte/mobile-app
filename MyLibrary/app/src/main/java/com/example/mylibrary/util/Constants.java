package com.example.mylibrary.util;

public class Constants {
    public static final String USER_TYPE_CUSTOMER = "CUSTOMER";
    public static final String USER_TYPE_ADMIN = "ADMIN";
    public static final String BORROW_STATUS_BORROWED = "BORROWED";
    public static final String BORROW_STATUS_RETURNED = "RETURNED";
    public static final String RESERVATION_STATUS_PENDING = "PENDING";
    public static final String RESERVATION_STATUS_APPROVED = "APPROVED";
    public static final String RESERVATION_STATUS_CANCELLED = "CANCELLED";
    public static final String BOOK_COPY_STATUS_AVAILABLE = "AVAILABLE";
    public static final String BOOK_COPY_STATUS_BORROWED = "BORROWED";
    public static final String BOOK_COPY_STATUS_DAMAGED = "DAMAGED";
    public static final String EXTENSION_STATUS_PENDING = "PENDING";
    public static final String EXTENSION_STATUS_APPROVED = "APPROVED";
    public static final String FINE_STATUS_UNPAID = "UNPAID";
    public static final String FINE_STATUS_PAID = "PAID";
    public static final double FINE_PER_DAY_OVERDUE = 10000;
    public static final double FINE_DAMAGED = 50000;
    public static final double FINE_LOST = 200000;
    public static final long BORROW_DURATION_DAYS = 14;
    public static final long EXTENSION_DURATION_DAYS = 7;
}
