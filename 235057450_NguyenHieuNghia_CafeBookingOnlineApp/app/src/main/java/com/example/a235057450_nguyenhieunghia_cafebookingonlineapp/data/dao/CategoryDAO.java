package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.data.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.data.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Category;

import java.util.ArrayList;
import java.util.List;

/** Reads menu categories from SQLite without putting SQL in an Activity. */
public class CategoryDAO {
    private final DatabaseHelper databaseHelper;

    public CategoryDAO(DatabaseHelper databaseHelper) {
        if (databaseHelper == null) {
            throw new IllegalArgumentException("DatabaseHelper must not be null");
        }
        this.databaseHelper = databaseHelper;
    }

    public List<Category> getAllCategories() {
        List<Category> result = new ArrayList<>();
        SQLiteDatabase db = databaseHelper.getReadableDatabase();

        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_CATEGORY,
                new String[]{"CategoryId", "CategoryName", "Description"},
                null, null, null, null, "CategoryName ASC")) {
            while (cursor.moveToNext()) {
                result.add(new Category(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2)
                ));
            }
        }
        return result;
    }
}
