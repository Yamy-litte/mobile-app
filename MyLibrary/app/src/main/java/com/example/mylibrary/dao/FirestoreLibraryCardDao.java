package com.example.mylibrary.dao;

import com.example.mylibrary.models.LibraryCard;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class FirestoreLibraryCardDao implements LibraryCardDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String COLLECTION = "library_cards";

    @Override
    public void addLibraryCard(LibraryCard card, DataCallback<LibraryCard> callback) {
        db.collection(COLLECTION).add(card)
                .addOnSuccessListener(doc -> {
                    card.setId(doc.getId());
                    callback.onSuccess(card);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getLibraryCardByCustomerId(String customerId, DataCallback<LibraryCard> callback) {
        db.collection(COLLECTION).whereEqualTo("customerId", customerId).get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        DocumentSnapshot doc = query.getDocuments().get(0);
                        LibraryCard card = doc.toObject(LibraryCard.class);
                        if (card != null) card.setId(doc.getId());
                        callback.onSuccess(card);
                    } else {
                        callback.onFailure("Library card not found");
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getLibraryCardByCardNumber(String cardNumber, DataCallback<LibraryCard> callback) {
        db.collection(COLLECTION).whereEqualTo("cardNumber", cardNumber).get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        DocumentSnapshot doc = query.getDocuments().get(0);
                        LibraryCard card = doc.toObject(LibraryCard.class);
                        if (card != null) card.setId(doc.getId());
                        callback.onSuccess(card);
                    } else {
                        callback.onFailure("Library card not found");
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void updateLibraryCard(LibraryCard card, DataCallback<Void> callback) {
        if (card.getId() == null) {
            callback.onFailure("Card ID is null");
            return;
        }
        db.collection(COLLECTION).document(card.getId()).set(card)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}
