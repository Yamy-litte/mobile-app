package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.database.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * Data access methods for menu categories.
 */
public class CategoryDao {
    private final DatabaseHelper databaseHelper;

    public CategoryDao(Context context) {
        databaseHelper = new DatabaseHelper(context.getApplicationContext());
    }

    public List<Category> getAllCategories() {
        List<Category> categories = new ArrayList<>();
        SQLiteDatabase db = databaseHelper.getReadableDatabase();

        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_CATEGORIES,
                new String[]{"CategoryId", "CategoryName"},
                null,
                null,
                null,
                null,
                "CategoryName COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) {
                categories.add(new Category(
                        cursor.getInt(cursor.getColumnIndexOrThrow("CategoryId")),
                        cursor.getString(cursor.getColumnIndexOrThrow("CategoryName"))
                ));
            }
        }
        return categories;
    }

    public Category getCategoryById(int categoryId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();

        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_CATEGORIES,
                new String[]{"CategoryId", "CategoryName"},
                "CategoryId = ?",
                new String[]{String.valueOf(categoryId)},
                null,
                null,
                null,
                "1")) {
            if (cursor.moveToFirst()) {
                return new Category(
                        cursor.getInt(cursor.getColumnIndexOrThrow("CategoryId")),
                        cursor.getString(cursor.getColumnIndexOrThrow("CategoryName"))
                );
            }
        }
        return null;
    }

    public void close() {
        databaseHelper.close();
    }
}
