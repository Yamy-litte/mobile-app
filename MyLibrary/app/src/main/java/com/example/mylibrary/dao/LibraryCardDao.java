package com.example.mylibrary.dao;

import com.example.mylibrary.models.LibraryCard;

public interface LibraryCardDao {
    void addLibraryCard(LibraryCard card, DataCallback<LibraryCard> callback);
    void getLibraryCardByCustomerId(String customerId, DataCallback<LibraryCard> callback);
    void getLibraryCardByCardNumber(String cardNumber, DataCallback<LibraryCard> callback);
    void updateLibraryCard(LibraryCard card, DataCallback<Void> callback);
}
