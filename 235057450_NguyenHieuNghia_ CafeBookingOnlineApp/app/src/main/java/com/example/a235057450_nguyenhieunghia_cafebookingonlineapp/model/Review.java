package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class Review {
    private final int reviewId;
    private final int billId;
    private final String customerEmail;
    private final int rating;
    private final String comment;
    private final String createdAt;
    private final double orderTotal;
    private final String menuSummary;

    public Review(int reviewId, int billId, String customerEmail, int rating,
                  String comment, String createdAt, double orderTotal, String menuSummary) {
        this.reviewId = reviewId;
        this.billId = billId;
        this.customerEmail = customerEmail;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
        this.orderTotal = orderTotal;
        this.menuSummary = menuSummary;
    }

    public int getReviewId() { return reviewId; }
    public int getBillId() { return billId; }
    public String getCustomerEmail() { return customerEmail; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public String getCreatedAt() { return createdAt; }
    public double getOrderTotal() { return orderTotal; }
    public String getMenuSummary() { return menuSummary; }
}
