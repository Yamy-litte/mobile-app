package com.example.mylibrary.dao;

import com.example.mylibrary.models.Fine;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreFineDao implements FineDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String COLLECTION = "fines";

    @Override
    public void addFine(Fine fine, DataCallback<Fine> callback) {
        db.collection(COLLECTION).add(fine)
                .addOnSuccessListener(doc -> {
                    fine.setId(doc.getId());
                    callback.onSuccess(fine);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getFinesByCustomerId(String customerId, DataCallback<List<Fine>> callback) {
        db.collection(COLLECTION).whereEqualTo("customerId", customerId).get()
                .addOnSuccessListener(query -> {
                    List<Fine> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Fine fine = doc.toObject(Fine.class);
                        fine.setId(doc.getId());
                        list.add(fine);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getUnpaidFines(String customerId, DataCallback<List<Fine>> callback) {
        db.collection(COLLECTION).whereEqualTo("customerId", customerId).whereEqualTo("status", "UNPAID").get()
                .addOnSuccessListener(query -> {
                    List<Fine> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Fine fine = doc.toObject(Fine.class);
                        fine.setId(doc.getId());
                        list.add(fine);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void updateFine(Fine fine, DataCallback<Void> callback) {
        if (fine.getId() == null) {
            callback.onFailure("Fine ID is null");
            return;
        }
        db.collection(COLLECTION).document(fine.getId()).set(fine)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void payFine(String id, DataCallback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "PAID");
        updates.put("paidAt", System.currentTimeMillis());

        db.collection(COLLECTION).document(id).update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}
