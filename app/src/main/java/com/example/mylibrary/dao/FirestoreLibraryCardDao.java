package com.example.mylibrary.dao;

import com.example.mylibrary.models.LibraryCard;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class FirestoreLibraryCardDao implements LibraryCardDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "library_cards";

    @Override
    public void addLibraryCard(LibraryCard card, DataCallback<LibraryCard> callback) {
        String id = card.getId() != null ? card.getId() : db.collection(COLLECTION).document().getId();
        card.setId(id);
        card.setIssuedAt(System.currentTimeMillis());
        db.collection(COLLECTION).document(id).set(card)
                .addOnSuccessListener(unused -> callback.onSuccess(card))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getLibraryCardById(String id, DataCallback<LibraryCard> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        LibraryCard card = doc.toObject(LibraryCard.class);
                        if (card != null) {
                            card.setId(doc.getId());
                            callback.onSuccess(card);
                        } else {
                            callback.onError(new Exception("LibraryCard is null"));
                        }
                    } else {
                        callback.onError(new Exception("LibraryCard not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getLibraryCardByCustomerId(String customerId, DataCallback<LibraryCard> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("customerId", customerId)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && !snapshot.isEmpty()) {
                        LibraryCard card = snapshot.getDocuments().get(0).toObject(LibraryCard.class);
                        if (card != null) {
                            card.setId(snapshot.getDocuments().get(0).getId());
                            callback.onSuccess(card);
                        } else {
                            callback.onError(new Exception("LibraryCard is null"));
                        }
                    } else {
                        callback.onError(new Exception("LibraryCard not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getLibraryCardByCardNumber(String cardNumber, DataCallback<LibraryCard> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("cardNumber", cardNumber)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && !snapshot.isEmpty()) {
                        LibraryCard card = snapshot.getDocuments().get(0).toObject(LibraryCard.class);
                        if (card != null) {
                            card.setId(snapshot.getDocuments().get(0).getId());
                            callback.onSuccess(card);
                        } else {
                            callback.onError(new Exception("LibraryCard is null"));
                        }
                    } else {
                        callback.onError(new Exception("LibraryCard not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void updateLibraryCard(LibraryCard card, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(card.getId()).set(card)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void deleteLibraryCard(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id).delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
