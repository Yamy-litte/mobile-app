package com.example.mylibrary.dao;

import com.example.mylibrary.models.BorrowRecord;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreBorrowRecordDao implements BorrowRecordDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String COLLECTION = "borrow_records";

    @Override
    public void addBorrowRecord(BorrowRecord record, DataCallback<BorrowRecord> callback) {
        db.collection(COLLECTION).add(record)
                .addOnSuccessListener(doc -> {
                    record.setId(doc.getId());
                    callback.onSuccess(record);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getBorrowRecordById(String id, DataCallback<BorrowRecord> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        BorrowRecord record = doc.toObject(BorrowRecord.class);
                        if (record != null) record.setId(doc.getId());
                        callback.onSuccess(record);
                    } else {
                        callback.onFailure("Borrow record not found");
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getBorrowRecordsByCustomerId(String customerId, DataCallback<List<BorrowRecord>> callback) {
        db.collection(COLLECTION).whereEqualTo("customerId", customerId).get()
                .addOnSuccessListener(query -> {
                    List<BorrowRecord> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        BorrowRecord record = doc.toObject(BorrowRecord.class);
                        record.setId(doc.getId());
                        list.add(record);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getActiveBorrowRecords(DataCallback<List<BorrowRecord>> callback) {
        db.collection(COLLECTION).whereEqualTo("status", "BORROWED").get()
                .addOnSuccessListener(query -> {
                    List<BorrowRecord> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        BorrowRecord record = doc.toObject(BorrowRecord.class);
                        record.setId(doc.getId());
                        list.add(record);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getOverdueBorrowRecords(DataCallback<List<BorrowRecord>> callback) {
        db.collection(COLLECTION).whereEqualTo("status", "OVERDUE").get()
                .addOnSuccessListener(query -> {
                    List<BorrowRecord> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        BorrowRecord record = doc.toObject(BorrowRecord.class);
                        record.setId(doc.getId());
                        list.add(record);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void updateBorrowRecord(BorrowRecord record, DataCallback<Void> callback) {
        if (record.getId() == null) {
            callback.onFailure("Record ID is null");
            return;
        }
        db.collection(COLLECTION).document(record.getId()).set(record)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void returnBorrowRecord(String id, long returnDate, String condition, DataCallback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("returnDate", returnDate);
        updates.put("physicalConditionOnReturn", condition);
        updates.put("status", "RETURNED");

        db.collection(COLLECTION).document(id).update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}
