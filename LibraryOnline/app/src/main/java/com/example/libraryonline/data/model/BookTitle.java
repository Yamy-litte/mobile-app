package com.example.libraryonline.data.model;

public class BookTitle {
    public String bookTitleId, title, author, isbn, categoryId, category, publisher, description, coverUrl, createdAt, updatedAt;
    public Integer publishYear;
    public int totalCopies, availableCopies;
    /** Approved Online title slots awaiting physical book assignment. */
    public int reservedOnlineCopies;
    public boolean isHidden;
    public BookTitle() {}
}