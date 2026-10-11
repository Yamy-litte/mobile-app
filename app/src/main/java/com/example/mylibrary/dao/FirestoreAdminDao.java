package com.example.mylibrary.dao;

import com.example.mylibrary.models.Admin;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class FirestoreAdminDao implements AdminDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "admin";

    @Override
    public void addAdmin(Admin admin, DataCallback<Admin> callback) {
        String id = admin.getId() != null ? admin.getId() : db.collection(COLLECTION).document().getId();
        admin.setId(id);
        admin.setCreatedAt(System.currentTimeMillis());
        db.collection(COLLECTION).document(id).set(admin)
                .addOnSuccessListener(unused -> callback.onSuccess(admin))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getAdminById(String id, DataCallback<Admin> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Admin admin = doc.toObject(Admin.class);
                        if (admin != null) {
                            admin.setId(doc.getId());
                            callback.onSuccess(admin);
                        } else {
                            callback.onError(new Exception("Admin is null"));
                        }
                    } else {
                        callback.onError(new Exception("Admin not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getAllAdmins(DataCallback<List<Admin>> callback) {
        db.collection(COLLECTION).get()
                .addOnSuccessListener(snapshot -> {
                    List<Admin> admins = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            Admin admin = doc.toObject(Admin.class);
                            if (admin != null) {
                                admin.setId(doc.getId());
                                admins.add(admin);
                            }
                        }
                    }
                    callback.onSuccess(admins);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void updateAdmin(Admin admin, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(admin.getId()).set(admin)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void deleteAdmin(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id).delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
