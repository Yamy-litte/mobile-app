package com.example.mylibrary.dao;

import com.example.mylibrary.models.Extension;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirestoreExtensionDao implements ExtensionDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String COLLECTION = "extensions";

    @Override
    public void addExtension(Extension extension, DataCallback<Extension> callback) {
        db.collection(COLLECTION).add(extension)
                .addOnSuccessListener(doc -> {
                    extension.setId(doc.getId());
                    callback.onSuccess(extension);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getExtensionsByBorrowRecordId(String borrowRecordId, DataCallback<List<Extension>> callback) {
        db.collection(COLLECTION).whereEqualTo("borrowRecordId", borrowRecordId).get()
                .addOnSuccessListener(query -> {
                    List<Extension> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Extension ext = doc.toObject(Extension.class);
                        ext.setId(doc.getId());
                        list.add(ext);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getPendingExtensions(DataCallback<List<Extension>> callback) {
        db.collection(COLLECTION).whereEqualTo("status", "PENDING").get()
                .addOnSuccessListener(query -> {
                    List<Extension> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Extension ext = doc.toObject(Extension.class);
                        ext.setId(doc.getId());
                        list.add(ext);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void updateExtension(Extension extension, DataCallback<Void> callback) {
        if (extension.getId() == null) {
            callback.onFailure("Extension ID is null");
            return;
        }
        db.collection(COLLECTION).document(extension.getId()).set(extension)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void approveExtension(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id).update("status", "APPROVED")
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void rejectExtension(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id).update("status", "REJECTED")
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}
