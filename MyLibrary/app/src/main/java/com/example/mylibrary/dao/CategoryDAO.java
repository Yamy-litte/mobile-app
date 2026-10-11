
package com.example.mylibrary.dao;

import com.example.mylibrary.model.Category;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {

    private final FirebaseFirestore db;

    public CategoryDAO() {
        db = FirebaseFirestore.getInstance();
    }

    public interface CategoryCallback {
        void onSuccess(List<Category> categories);

        void onFailure(String errorMessage);
    }


    public void getActiveCategories(CategoryCallback callback) {
        db.collection("categories")
                .whereEqualTo("status", "Active")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Category> categories = new ArrayList<>();

                    for (QueryDocumentSnapshot document
                            : querySnapshot) {

                        Category category =
                                document.toObject(Category.class);

                        category.setId(document.getId());
                        categories.add(category);
                    }

                    // Sắp xếp theo tên ngay trong ứng dụng,
                    // không cần composite index trên Firestore.
                    java.util.Collections.sort(
                            categories,
                            (a, b) -> {
                                String nameA = a.getName() == null
                                        ? "" : a.getName();
                                String nameB = b.getName() == null
                                        ? "" : b.getName();

                                return nameA.compareToIgnoreCase(nameB);
                            }
                    );

                    callback.onSuccess(categories);
                })
                .addOnFailureListener(exception ->
                        callback.onFailure(
                                exception.getLocalizedMessage() != null
                                        ? exception.getLocalizedMessage()
                                        : "Không thể tải danh mục sách."
                        )
                );
    }


    public void getCategoryById(
            String categoryId,
            CategoryCallback callback
    ) {
        db.collection("categories")
                .document(categoryId)
                .get()
                .addOnSuccessListener(document -> {
                    List<Category> categories = new ArrayList<>();

                    if (document.exists()) {
                        Category category =
                                document.toObject(Category.class);

                        if (category != null) {
                            category.setId(document.getId());
                            categories.add(category);
                        }
                    }

                    callback.onSuccess(categories);
                })
                .addOnFailureListener(exception ->
                        callback.onFailure(
                                exception.getLocalizedMessage() != null
                                        ? exception.getLocalizedMessage()
                                        : "Không thể tải danh mục."
                        )
                );
    }
}
