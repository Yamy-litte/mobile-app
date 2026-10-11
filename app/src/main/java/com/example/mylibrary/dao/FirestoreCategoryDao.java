package com.example.mylibrary.dao;

import com.example.mylibrary.models.Category;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class FirestoreCategoryDao implements CategoryDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "categories";

    @Override
    public void addCategory(Category category, DataCallback<Category> callback) {
        String id = category.getId() != null ? category.getId() : db.collection(COLLECTION).document().getId();
        category.setId(id);
        db.collection(COLLECTION).document(id).set(category)
                .addOnSuccessListener(unused -> callback.onSuccess(category))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getCategories(DataCallback<List<Category>> callback) {
        db.collection(COLLECTION).get()
                .addOnSuccessListener(snapshot -> {
                    List<Category> categories = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            Category category = doc.toObject(Category.class);
                            if (category != null) {
                                category.setId(doc.getId());
                                categories.add(category);
                            }
                        }
                    }
                    callback.onSuccess(categories);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getCategoryById(String id, DataCallback<Category> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Category category = doc.toObject(Category.class);
                        if (category != null) {
                            category.setId(doc.getId());
                            callback.onSuccess(category);
                        } else {
                            callback.onError(new Exception("Category is null"));
                        }
                    } else {
                        callback.onError(new Exception("Category not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void updateCategory(Category category, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(category.getId()).set(category)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void deleteCategory(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id).delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
