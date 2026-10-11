package com.example.mylibrary.dao;

import com.example.mylibrary.models.Fine;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class FirestoreFineDao implements FineDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "fines";

    @Override
    public void addFine(Fine fine, DataCallback<Fine> callback) {
        String id = fine.getId() != null ? fine.getId() : db.collection(COLLECTION).document().getId();
        fine.setId(id);
        fine.setCreatedAt(System.currentTimeMillis());
        db.collection(COLLECTION).document(id).set(fine)
                .addOnSuccessListener(unused -> callback.onSuccess(fine))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getFineById(String id, DataCallback<Fine> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Fine fine = doc.toObject(Fine.class);
                        if (fine != null) {
                            fine.setId(doc.getId());
                            callback.onSuccess(fine);
                        } else {
                            callback.onError(new Exception("Fine is null"));
                        }
                    } else {
                        callback.onError(new Exception("Fine not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getFinesByBorrowRecordId(String borrowRecordId, DataCallback<List<Fine>> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("borrowRecordId", borrowRecordId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Fine> fines = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            Fine fine = doc.toObject(Fine.class);
                            if (fine != null) {
                                fine.setId(doc.getId());
                                fines.add(fine);
                            }
                        }
                    }
                    callback.onSuccess(fines);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getFinesByCustomerId(String customerId, DataCallback<List<Fine>> callback) {
        db.collection("borrow_records")
                .whereEqualTo("customerId", customerId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Fine> fines = new ArrayList<>();
                    if (snapshot != null && !snapshot.isEmpty()) {
                        List<String> borrowRecordIds = new ArrayList<>();
                        for (var doc : snapshot.getDocuments()) {
                            borrowRecordIds.add(doc.getId());
                        }
                        getFinesForBorrowRecords(borrowRecordIds, fines, callback);
                    } else {
                        callback.onSuccess(fines);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    private void getFinesForBorrowRecords(List<String> borrowRecordIds, List<Fine> fines, DataCallback<List<Fine>> callback) {
        if (borrowRecordIds.isEmpty()) {
            callback.onSuccess(fines);
            return;
        }
        db.collection(COLLECTION)
                .whereIn("borrowRecordId", borrowRecordIds)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            Fine fine = doc.toObject(Fine.class);
                            if (fine != null) {
                                fine.setId(doc.getId());
                                fines.add(fine);
                            }
                        }
                    }
                    callback.onSuccess(fines);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getUnpaidFines(String customerId, DataCallback<List<Fine>> callback) {
        db.collection("borrow_records")
                .whereEqualTo("customerId", customerId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Fine> fines = new ArrayList<>();
                    if (snapshot != null && !snapshot.isEmpty()) {
                        List<String> borrowRecordIds = new ArrayList<>();
                        for (var doc : snapshot.getDocuments()) {
                            borrowRecordIds.add(doc.getId());
                        }
                        getUnpaidFinesForBorrowRecords(borrowRecordIds, fines, callback);
                    } else {
                        callback.onSuccess(fines);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    private void getUnpaidFinesForBorrowRecords(List<String> borrowRecordIds, List<Fine> fines, DataCallback<List<Fine>> callback) {
        if (borrowRecordIds.isEmpty()) {
            callback.onSuccess(fines);
            return;
        }
        db.collection(COLLECTION)
                .whereIn("borrowRecordId", borrowRecordIds)
                .whereEqualTo("status", "UNPAID")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            Fine fine = doc.toObject(Fine.class);
                            if (fine != null) {
                                fine.setId(doc.getId());
                                fines.add(fine);
                            }
                        }
                    }
                    callback.onSuccess(fines);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void updateFine(Fine fine, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(fine.getId()).set(fine)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void payFine(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id)
                .update(
                        "status", "PAID",
                        "paidAt", System.currentTimeMillis()
                )
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
