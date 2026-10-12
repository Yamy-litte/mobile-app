package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Category;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {
    private final DatabaseHelper helper;

    public CategoryDAO(Context context) {
        helper = new DatabaseHelper(context.getApplicationContext());
    }

    public long insert(Category category) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("CategoryName", category.getCategoryName());
        values.put("Description", category.getDescription());
        return db.insert(DatabaseHelper.TABLE_CATEGORY, null, values);
    }

    public List<Category> getAll() {
        List<Category> result = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_CATEGORY, null, null, null,
                null, null, "CategoryName COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) {
                result.add(new Category(
                        cursor.getLong(cursor.getColumnIndexOrThrow("CategoryId")),
                        cursor.getString(cursor.getColumnIndexOrThrow("CategoryName")),
                        cursor.getString(cursor.getColumnIndexOrThrow("Description"))));
            }
        }
        return result;
    }

    public Category getById(long categoryId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_CATEGORY, null, "CategoryId = ?",
                new String[]{String.valueOf(categoryId)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return new Category(
                        cursor.getLong(cursor.getColumnIndexOrThrow("CategoryId")),
                        cursor.getString(cursor.getColumnIndexOrThrow("CategoryName")),
                        cursor.getString(cursor.getColumnIndexOrThrow("Description")));
            }
        }
        return null;
    }

    public int update(Category category) {
        ContentValues values = new ContentValues();
        values.put("CategoryName", category.getCategoryName());
        values.put("Description", category.getDescription());
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_CATEGORY, values,
                "CategoryId = ?", new String[]{String.valueOf(category.getCategoryId())});
    }

    public int delete(long categoryId) {
        return helper.getWritableDatabase().delete(DatabaseHelper.TABLE_CATEGORY,
                "CategoryId = ?", new String[]{String.valueOf(categoryId)});
    }

    public void close() {
        helper.close();
    }
}
