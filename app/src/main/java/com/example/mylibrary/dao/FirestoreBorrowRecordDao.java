package com.example.mylibrary.dao;

import com.example.mylibrary.models.BorrowRecord;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class FirestoreBorrowRecordDao implements BorrowRecordDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "borrow_records";

    @Override
    public void addBorrowRecord(BorrowRecord record, DataCallback<BorrowRecord> callback) {
        String id = record.getId() != null ? record.getId() : db.collection(COLLECTION).document().getId();
        record.setId(id);
        db.collection(COLLECTION).document(id).set(record)
                .addOnSuccessListener(unused -> callback.onSuccess(record))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getBorrowRecordById(String id, DataCallback<BorrowRecord> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        BorrowRecord record = doc.toObject(BorrowRecord.class);
                        if (record != null) {
                            record.setId(doc.getId());
                            callback.onSuccess(record);
                        } else {
                            callback.onError(new Exception("BorrowRecord is null"));
                        }
                    } else {
                        callback.onError(new Exception("BorrowRecord not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getBorrowRecordsByCustomerId(String customerId, DataCallback<List<BorrowRecord>> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("customerId", customerId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<BorrowRecord> records = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            BorrowRecord record = doc.toObject(BorrowRecord.class);
                            if (record != null) {
                                record.setId(doc.getId());
                                records.add(record);
                            }
                        }
                    }
                    callback.onSuccess(records);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getBorrowRecordsByBookCopyId(String bookCopyId, DataCallback<List<BorrowRecord>> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("bookCopyId", bookCopyId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<BorrowRecord> records = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            BorrowRecord record = doc.toObject(BorrowRecord.class);
                            if (record != null) {
                                record.setId(doc.getId());
                                records.add(record);
                            }
                        }
                    }
                    callback.onSuccess(records);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getActiveBorrowRecords(DataCallback<List<BorrowRecord>> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("status", "BORROWED")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<BorrowRecord> records = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            BorrowRecord record = doc.toObject(BorrowRecord.class);
                            if (record != null) {
                                record.setId(doc.getId());
                                records.add(record);
                            }
                        }
                    }
                    callback.onSuccess(records);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getOverdueBorrowRecords(DataCallback<List<BorrowRecord>> callback) {
        long now = System.currentTimeMillis();
        db.collection(COLLECTION)
                .whereEqualTo("status", "BORROWED")
                .whereLessThan("dueDate", now)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<BorrowRecord> records = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            BorrowRecord record = doc.toObject(BorrowRecord.class);
                            if (record != null) {
                                record.setId(doc.getId());
                                records.add(record);
                            }
                        }
                    }
                    callback.onSuccess(records);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void updateBorrowRecord(BorrowRecord record, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(record.getId()).set(record)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void returnBorrowRecord(String id, long returnDate, String condition, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id)
                .update(
                        "returnDate", returnDate,
                        "physicalConditionOnReturn", condition,
                        "status", "RETURNED"
                )
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
