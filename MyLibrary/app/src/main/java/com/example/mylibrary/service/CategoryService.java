
package com.example.mylibrary.service;

import com.example.mylibrary.dao.CategoryDAO;
import com.example.mylibrary.model.Category;

import java.util.List;

public class CategoryService {

    private final CategoryDAO categoryDAO;

    public CategoryService() {
        categoryDAO = new CategoryDAO();
    }

    public interface CategoryServiceCallback {
        void onSuccess(List<Category> categories);

        void onFailure(String errorMessage);
    }

    public void getActiveCategories(
            CategoryServiceCallback callback
    ) {
        categoryDAO.getActiveCategories(
                new CategoryDAO.CategoryCallback() {

                    @Override
                    public void onSuccess(
                            List<Category> categories
                    ) {
                        callback.onSuccess(categories);
                    }

                    @Override
                    public void onFailure(
                            String errorMessage
                    ) {
                        callback.onFailure(errorMessage);
                    }
                }
        );
    }

    public void getCategoryById(
            String categoryId,
            CategoryServiceCallback callback
    ) {
        categoryDAO.getCategoryById(
                categoryId,
                new CategoryDAO.CategoryCallback() {

                    @Override
                    public void onSuccess(
                            List<Category> categories
                    ) {
                        callback.onSuccess(categories);
                    }

                    @Override
                    public void onFailure(
                            String errorMessage
                    ) {
                        callback.onFailure(errorMessage);
                    }
                }
        );
    }
}
