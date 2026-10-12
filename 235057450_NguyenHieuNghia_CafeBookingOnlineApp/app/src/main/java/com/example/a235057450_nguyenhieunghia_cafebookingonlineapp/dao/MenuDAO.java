package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.MenuItem;

import java.util.ArrayList;
import java.util.List;

public class MenuDAO {
    private final DatabaseHelper helper;

    public MenuDAO(Context context) {
        helper = new DatabaseHelper(context.getApplicationContext());
    }

    public long insert(MenuItem item) {
        ContentValues values = toValues(item);
        return helper.getWritableDatabase().insert(DatabaseHelper.TABLE_MENU, null, values);
    }

    public List<MenuItem> getAll() {
        return queryMenu(null, null, "MenuName COLLATE NOCASE ASC");
    }

    public List<MenuItem> getAvailable() {
        return queryMenu("IsAvailable = ?", new String[]{"1"}, "MenuName COLLATE NOCASE ASC");
    }

    public List<MenuItem> getByCategory(long categoryId) {
        return queryMenu("CategoryId = ?", new String[]{String.valueOf(categoryId)},
                "MenuName COLLATE NOCASE ASC");
    }

    public List<MenuItem> search(String keyword) {
        String value = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return queryMenu("(MenuName LIKE ? OR Description LIKE ?)",
                new String[]{value, value}, "MenuName COLLATE NOCASE ASC");
    }

    public MenuItem getById(long menuId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_MENU, null, "MenuId = ?",
                new String[]{String.valueOf(menuId)}, null, null, null)) {
            if (cursor.moveToFirst()) return fromCursor(cursor);
        }
        return null;
    }

    public int update(MenuItem item) {
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_MENU, toValues(item),
                "MenuId = ?", new String[]{String.valueOf(item.getMenuId())});
    }

    public int setAvailability(long menuId, boolean available) {
        ContentValues values = new ContentValues();
        values.put("IsAvailable", available ? 1 : 0);
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_MENU, values,
                "MenuId = ?", new String[]{String.valueOf(menuId)});
    }

    public int delete(long menuId) {
        return helper.getWritableDatabase().delete(DatabaseHelper.TABLE_MENU,
                "MenuId = ?", new String[]{String.valueOf(menuId)});
    }

    private List<MenuItem> queryMenu(String selection, String[] args, String orderBy) {
        List<MenuItem> result = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_MENU, null, selection, args,
                null, null, orderBy)) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    private MenuItem fromCursor(Cursor cursor) {
        return new MenuItem(
                cursor.getLong(cursor.getColumnIndexOrThrow("MenuId")),
                cursor.getLong(cursor.getColumnIndexOrThrow("CategoryId")),
                cursor.getString(cursor.getColumnIndexOrThrow("MenuName")),
                cursor.getString(cursor.getColumnIndexOrThrow("Description")),
                cursor.getString(cursor.getColumnIndexOrThrow("ImageUrl")),
                cursor.getDouble(cursor.getColumnIndexOrThrow("Price")),
                cursor.getInt(cursor.getColumnIndexOrThrow("IsAvailable")) == 1);
    }

    private ContentValues toValues(MenuItem item) {
        ContentValues values = new ContentValues();
        values.put("CategoryId", item.getCategoryId());
        values.put("MenuName", item.getMenuName());
        values.put("Description", item.getDescription());
        values.put("ImageUrl", item.getImageUrl());
        values.put("Price", item.getPrice());
        values.put("IsAvailable", item.isAvailable() ? 1 : 0);
        return values;
    }

    public void close() {
        helper.close();
    }
}
