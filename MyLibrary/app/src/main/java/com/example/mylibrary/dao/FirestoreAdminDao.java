package com.example.mylibrary.dao;

import com.example.mylibrary.models.Admin;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirestoreAdminDao implements AdminDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String COLLECTION = "admins";

    @Override
    public void addAdmin(Admin admin, DataCallback<Admin> callback) {
        if (admin.getId() != null && !admin.getId().isEmpty()) {
            db.collection(COLLECTION).document(admin.getId()).set(admin)
                    .addOnSuccessListener(unused -> callback.onSuccess(admin))
                    .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
        } else {
            db.collection(COLLECTION).add(admin)
                    .addOnSuccessListener(doc -> {
                        admin.setId(doc.getId());
                        callback.onSuccess(admin);
                    })
                    .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
        }
    }

    @Override
    public void getAdminById(String id, DataCallback<Admin> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Admin admin = doc.toObject(Admin.class);
                        if (admin != null) admin.setId(doc.getId());
                        callback.onSuccess(admin);
                    } else {
                        callback.onFailure("Admin not found");
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getAllAdmins(DataCallback<List<Admin>> callback) {
        db.collection(COLLECTION).get()
                .addOnSuccessListener(query -> {
                    List<Admin> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Admin admin = doc.toObject(Admin.class);
                        admin.setId(doc.getId());
                        list.add(admin);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void updateAdmin(Admin admin, DataCallback<Void> callback) {
        if (admin.getId() == null) {
            callback.onFailure("Admin ID is null");
            return;
        }
        db.collection(COLLECTION).document(admin.getId()).set(admin)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}
