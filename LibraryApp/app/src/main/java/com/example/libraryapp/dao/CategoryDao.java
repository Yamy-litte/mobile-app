package com.example.libraryapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Category;

import java.util.ArrayList;
import java.util.List;

/** SQLite data-access methods for book categories. */
public class CategoryDao {
    private final LibraryDbHelper dbHelper;

    public CategoryDao(Context context) {
        dbHelper = new LibraryDbHelper(context.getApplicationContext());
    }

    public long insert(Category category) {
        return dbHelper.getWritableDatabase().insert(
                LibraryDbHelper.TABLE_CATEGORIES, null, toValues(category));
    }

    public int update(Category category) {
        if (category.getId() <= 0) {
            return 0;
        }
        return dbHelper.getWritableDatabase().update(
                LibraryDbHelper.TABLE_CATEGORIES,
                toValues(category),
                "id = ?",
                new String[]{String.valueOf(category.getId())});
    }

    public int delete(int categoryId) {
        return dbHelper.getWritableDatabase().delete(
                LibraryDbHelper.TABLE_CATEGORIES,
                "id = ?",
                new String[]{String.valueOf(categoryId)});
    }

    public Category getById(int categoryId) {
        try (Cursor cursor = dbHelper.getReadableDatabase().query(
                LibraryDbHelper.TABLE_CATEGORIES, null, "id = ?",
                new String[]{String.valueOf(categoryId)}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public List<Category> getAll() {
        List<Category> categories = new ArrayList<>();
        try (Cursor cursor = dbHelper.getReadableDatabase().query(
                LibraryDbHelper.TABLE_CATEGORIES, null, null, null,
                null, null, "category_name COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) {
                categories.add(fromCursor(cursor));
            }
        }
        return categories;
    }

    private ContentValues toValues(Category category) {
        ContentValues values = new ContentValues();
        values.put("category_name", category.getCategoryName());
        values.put("description", category.getDescription());
        return values;
    }

    private Category fromCursor(Cursor cursor) {
        return new Category(
                cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("category_name")),
                cursor.getString(cursor.getColumnIndexOrThrow("description")));
    }

    public void close() {
        dbHelper.close();
    }
}
