package com.example.mylibrary.service;

import com.example.mylibrary.dao.CategoryDao;
import com.example.mylibrary.dao.DataCallback;
import com.example.mylibrary.dao.FirestoreCategoryDao;
import com.example.mylibrary.models.Category;

import java.util.List;

public class CategoryService {

    private final CategoryDao categoryDao;

    public CategoryService() {
        categoryDao = new FirestoreCategoryDao();
    }

    public interface CategoryServiceCallback {
        void onSuccess(List<Category> categories);
        void onFailure(String errorMessage);
    }

    public void getActiveCategories(CategoryServiceCallback callback) {
        categoryDao.getCategories(new DataCallback<List<Category>>() {
            @Override
            public void onSuccess(List<Category> result) {
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    public void getCategoryById(String categoryId, CategoryServiceCallback callback) {
        categoryDao.getCategoryById(categoryId, new DataCallback<Category>() {
            @Override
            public void onSuccess(Category result) {
                callback.onSuccess(java.util.Collections.singletonList(result));
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }
}
