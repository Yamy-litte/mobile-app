package com.example.mylibrary.dao;

import com.example.mylibrary.models.Extension;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class FirestoreExtensionDao implements ExtensionDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "extensions";

    @Override
    public void addExtension(Extension extension, DataCallback<Extension> callback) {
        String id = extension.getId() != null ? extension.getId() : db.collection(COLLECTION).document().getId();
        extension.setId(id);
        db.collection(COLLECTION).document(id).set(extension)
                .addOnSuccessListener(unused -> callback.onSuccess(extension))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getExtensionById(String id, DataCallback<Extension> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Extension extension = doc.toObject(Extension.class);
                        if (extension != null) {
                            extension.setId(doc.getId());
                            callback.onSuccess(extension);
                        } else {
                            callback.onError(new Exception("Extension is null"));
                        }
                    } else {
                        callback.onError(new Exception("Extension not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getExtensionsByBorrowRecordId(String borrowRecordId, DataCallback<List<Extension>> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("borrowRecordId", borrowRecordId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Extension> extensions = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            Extension extension = doc.toObject(Extension.class);
                            if (extension != null) {
                                extension.setId(doc.getId());
                                extensions.add(extension);
                            }
                        }
                    }
                    callback.onSuccess(extensions);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getPendingExtensions(DataCallback<List<Extension>> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("status", "PENDING")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Extension> extensions = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            Extension extension = doc.toObject(Extension.class);
                            if (extension != null) {
                                extension.setId(doc.getId());
                                extensions.add(extension);
                            }
                        }
                    }
                    callback.onSuccess(extensions);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void updateExtension(Extension extension, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(extension.getId()).set(extension)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void approveExtension(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id)
                .update("status", "APPROVED")
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void rejectExtension(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id)
                .update("status", "REJECTED")
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
