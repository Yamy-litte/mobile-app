package com.example.mylibrary.dao;

import com.example.mylibrary.models.Reservation;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class FirestoreReservationDao implements ReservationDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "reservations";

    @Override
    public void addReservation(Reservation reservation, DataCallback<Reservation> callback) {
        String id = reservation.getId() != null ? reservation.getId() : db.collection(COLLECTION).document().getId();
        reservation.setId(id);
        db.collection(COLLECTION).document(id).set(reservation)
                .addOnSuccessListener(unused -> callback.onSuccess(reservation))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getReservationById(String id, DataCallback<Reservation> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Reservation reservation = doc.toObject(Reservation.class);
                        if (reservation != null) {
                            reservation.setId(doc.getId());
                            callback.onSuccess(reservation);
                        } else {
                            callback.onError(new Exception("Reservation is null"));
                        }
                    } else {
                        callback.onError(new Exception("Reservation not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getReservationsByCustomerId(String customerId, DataCallback<List<Reservation>> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("customerId", customerId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Reservation> reservations = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            Reservation reservation = doc.toObject(Reservation.class);
                            if (reservation != null) {
                                reservation.setId(doc.getId());
                                reservations.add(reservation);
                            }
                        }
                    }
                    callback.onSuccess(reservations);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getReservationsByBookId(String bookId, DataCallback<List<Reservation>> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("bookId", bookId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Reservation> reservations = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            Reservation reservation = doc.toObject(Reservation.class);
                            if (reservation != null) {
                                reservation.setId(doc.getId());
                                reservations.add(reservation);
                            }
                        }
                    }
                    callback.onSuccess(reservations);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getActiveReservations(DataCallback<List<Reservation>> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("status", "PENDING")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Reservation> reservations = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            Reservation reservation = doc.toObject(Reservation.class);
                            if (reservation != null) {
                                reservation.setId(doc.getId());
                                reservations.add(reservation);
                            }
                        }
                    }
                    callback.onSuccess(reservations);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void updateReservation(Reservation reservation, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(reservation.getId()).set(reservation)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void cancelReservation(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id)
                .update("status", "CANCELLED")
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
