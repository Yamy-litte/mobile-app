package com.example.mylibrary.dao;

import com.example.mylibrary.models.Category;
import java.util.List;

public interface CategoryDao {
    void addCategory(Category category, DataCallback<Category> callback);
    void getCategories(DataCallback<List<Category>> callback);
    void getCategoryById(String id, DataCallback<Category> callback);
    void updateCategory(Category category, DataCallback<Void> callback);
}
