package com.example.mylibrary.dao;

import com.example.mylibrary.models.Category;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirestoreCategoryDao implements CategoryDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String COLLECTION = "categories";

    @Override
    public void addCategory(Category category, DataCallback<Category> callback) {
        db.collection(COLLECTION).add(category)
                .addOnSuccessListener(doc -> {
                    category.setId(doc.getId());
                    callback.onSuccess(category);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getCategories(DataCallback<List<Category>> callback) {
        db.collection(COLLECTION).get()
                .addOnSuccessListener(query -> {
                    List<Category> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Category cat = doc.toObject(Category.class);
                        cat.setId(doc.getId());
                        list.add(cat);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getCategoryById(String id, DataCallback<Category> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Category cat = doc.toObject(Category.class);
                        if (cat != null) cat.setId(doc.getId());
                        callback.onSuccess(cat);
                    } else {
                        callback.onFailure("Category not found");
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void updateCategory(Category category, DataCallback<Void> callback) {
        if (category.getId() == null) {
            callback.onFailure("Category ID is null");
            return;
        }
        db.collection(COLLECTION).document(category.getId()).set(category)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}
