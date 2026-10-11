package com.example.mylibrary.repository;

import com.example.mylibrary.dao.CategoryDao;
import com.example.mylibrary.dao.DataCallback;
import com.example.mylibrary.dao.FirestoreCategoryDao;
import com.example.mylibrary.models.Category;
import java.util.List;

public class CategoryRepository {
    private final CategoryDao categoryDao = new FirestoreCategoryDao();

    public void addCategory(Category category, DataCallback<Category> callback) {
        categoryDao.addCategory(category, callback);
    }

    public void getCategories(DataCallback<List<Category>> callback) {
        categoryDao.getCategories(callback);
    }

    public void getCategoryById(String id, DataCallback<Category> callback) {
        categoryDao.getCategoryById(id, callback);
    }

    public void updateCategory(Category category, DataCallback<Void> callback) {
        categoryDao.updateCategory(category, callback);
    }
}
