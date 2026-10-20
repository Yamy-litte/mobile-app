package com.example.libraryonline.data.model;

public class BookTitle {
    public String bookTitleId, title, author, isbn, category, publisher, description, coverUrl, createdAt, updatedAt;
    public Integer publishYear;
    public int totalCopies, availableCopies;
    public boolean isHidden;
    public BookTitle() {}
}