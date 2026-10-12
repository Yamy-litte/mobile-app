package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.data.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.data.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.MenuItem;

import java.util.ArrayList;
import java.util.List;

/** Reads menu items from SQLite and maps rows to MenuItem models. */
public class MenuDAO {
    private final DatabaseHelper databaseHelper;

    public MenuDAO(DatabaseHelper databaseHelper) {
        if (databaseHelper == null) {
            throw new IllegalArgumentException("DatabaseHelper must not be null");
        }
        this.databaseHelper = databaseHelper;
    }

    public List<MenuItem> getAllMenuItems() {
        List<MenuItem> result = new ArrayList<>();
        SQLiteDatabase db = databaseHelper.getReadableDatabase();

        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_MENU,
                new String[]{"MenuId", "CategoryId", "MenuName", "Description",
                        "ImageUrl", "Price", "IsAvailable"},
                null, null, null, null, "MenuName ASC")) {
            while (cursor.moveToNext()) {
                result.add(mapMenuItem(cursor));
            }
        }
        return result;
    }

    public List<MenuItem> getMenuByCategory(long categoryId) {
        List<MenuItem> result = new ArrayList<>();
        SQLiteDatabase db = databaseHelper.getReadableDatabase();

        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_MENU,
                new String[]{"MenuId", "CategoryId", "MenuName", "Description",
                        "ImageUrl", "Price", "IsAvailable"},
                "CategoryId = ?",
                new String[]{String.valueOf(categoryId)},
                null, null, "MenuName ASC")) {
            while (cursor.moveToNext()) {
                result.add(mapMenuItem(cursor));
            }
        }
        return result;
    }

    private MenuItem mapMenuItem(Cursor cursor) {
        return new MenuItem(
                cursor.getLong(0),
                cursor.getLong(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getString(4),
                cursor.getDouble(5),
                cursor.getInt(6) == 1
        );
    }
}
