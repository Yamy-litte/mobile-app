
package com.example.mylibrary.model;

import com.google.firebase.Timestamp;

public class Book {

    private String id;
    private String title;
    private String author;
    private String categoryId;
    private String description;
    private String coverUrl;
    private int popularity;
    private double averageRating;
    private int reviewCount;
    private String status;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Book() {
        // Constructor rỗng để Firestore ánh xạ dữ liệu
    }

    public Book(
            String id,
            String title,
            String author,
            String categoryId,
            String description,
            String coverUrl,
            int popularity,
            String status,
            Timestamp createdAt,
            Timestamp updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.categoryId = categoryId;
        this.description = description;
        this.coverUrl = coverUrl;
        this.popularity = popularity;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public int getPopularity() {
        return popularity;
    }

    public void setPopularity(int popularity) {
        this.popularity = popularity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
    public double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }
}
