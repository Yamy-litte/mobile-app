package com.example.mylibrary.repository;

import com.example.mylibrary.dao.LibraryCardDao;
import com.example.mylibrary.dao.DataCallback;
import com.example.mylibrary.dao.FirestoreLibraryCardDao;
import com.example.mylibrary.models.LibraryCard;

public class LibraryCardRepository {
    private final LibraryCardDao libraryCardDao = new FirestoreLibraryCardDao();

    public void addLibraryCard(LibraryCard card, DataCallback<LibraryCard> callback) {
        libraryCardDao.addLibraryCard(card, callback);
    }

    public void getLibraryCardByCustomerId(String customerId, DataCallback<LibraryCard> callback) {
        libraryCardDao.getLibraryCardByCustomerId(customerId, callback);
    }

    public void getLibraryCardByCardNumber(String cardNumber, DataCallback<LibraryCard> callback) {
        libraryCardDao.getLibraryCardByCardNumber(cardNumber, callback);
    }

    public void updateLibraryCard(LibraryCard card, DataCallback<Void> callback) {
        libraryCardDao.updateLibraryCard(card, callback);
    }
}
