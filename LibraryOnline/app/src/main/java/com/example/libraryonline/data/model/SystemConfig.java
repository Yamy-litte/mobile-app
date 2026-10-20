package com.example.libraryonline.data.model;

public class SystemConfig {
    public String configId, updatedBy, updatedAt;
    public int maxBooksPerUser = 5, maxLoanDays = 14, onlineHoldHours = 24, reservationHoldHours = 48, lateFeePerDay = 5000, lostBookMultiplier = 2;
    public SystemConfig() {}
}