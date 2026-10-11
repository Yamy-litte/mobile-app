package com.example.mylibrary.dao;

import com.example.mylibrary.models.Reservation;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirestoreReservationDao implements ReservationDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String COLLECTION = "reservations";

    @Override
    public void addReservation(Reservation reservation, DataCallback<Reservation> callback) {
        db.collection(COLLECTION).add(reservation)
                .addOnSuccessListener(doc -> {
                    reservation.setId(doc.getId());
                    callback.onSuccess(reservation);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getReservationsByCustomerId(String customerId, DataCallback<List<Reservation>> callback) {
        db.collection(COLLECTION).whereEqualTo("customerId", customerId).get()
                .addOnSuccessListener(query -> {
                    List<Reservation> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Reservation res = doc.toObject(Reservation.class);
                        res.setId(doc.getId());
                        list.add(res);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getReservationsByBookId(String bookId, DataCallback<List<Reservation>> callback) {
        db.collection(COLLECTION).whereEqualTo("bookId", bookId).get()
                .addOnSuccessListener(query -> {
                    List<Reservation> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Reservation res = doc.toObject(Reservation.class);
                        res.setId(doc.getId());
                        list.add(res);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getActiveReservations(DataCallback<List<Reservation>> callback) {
        db.collection(COLLECTION).whereEqualTo("status", "PENDING").get()
                .addOnSuccessListener(query -> {
                    List<Reservation> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Reservation res = doc.toObject(Reservation.class);
                        res.setId(doc.getId());
                        list.add(res);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void updateReservation(Reservation reservation, DataCallback<Void> callback) {
        if (reservation.getId() == null) {
            callback.onFailure("Reservation ID is null");
            return;
        }
        db.collection(COLLECTION).document(reservation.getId()).set(reservation)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void cancelReservation(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id).update("status", "CANCELLED")
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}
