package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

public class DishReview {
    private final int reviewId;
    private final int userId;
    private final int menuId;
    private final String customerEmail;
    private final int rating;
    private final String comment;
    private final String createdAt;

    public DishReview(int reviewId, int userId, int menuId, String customerEmail,
                      int rating, String comment, String createdAt) {
        this.reviewId = reviewId;
        this.userId = userId;
        this.menuId = menuId;
        this.customerEmail = customerEmail;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public int getReviewId() { return reviewId; }
    public int getUserId() { return userId; }
    public int getMenuId() { return menuId; }
    public String getCustomerEmail() { return customerEmail; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public String getCreatedAt() { return createdAt; }
}
