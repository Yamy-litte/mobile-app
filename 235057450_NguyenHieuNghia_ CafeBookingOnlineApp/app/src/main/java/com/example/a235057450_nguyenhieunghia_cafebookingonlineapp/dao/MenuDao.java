package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.database.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Menu;

import java.util.ArrayList;
import java.util.List;

/**
 * Data access methods for menu items.
 */
public class MenuDao {
    private final DatabaseHelper databaseHelper;

    public MenuDao(Context context) {
        databaseHelper = new DatabaseHelper(context.getApplicationContext());
    }

    /** Returns only menu items that currently have stock. */
    public List<Menu> getAvailableMenus() {
        List<Menu> menus = new ArrayList<>();
        SQLiteDatabase db = databaseHelper.getReadableDatabase();

        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_MENU,
                new String[]{"MenuId", "MenuName", "Image", "CategoryId", "Price", "StockQuantity"},
                "StockQuantity > ?",
                new String[]{"0"},
                null,
                null,
                "MenuName COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) {
                menus.add(readMenu(cursor));
            }
        }
        return menus;
    }

    /** Searches available menu items by name. The keyword is safely parameterized. */
    public List<Menu> searchAvailableMenus(String keyword) {
        List<Menu> menus = new ArrayList<>();
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_MENU,
                new String[]{"MenuId", "MenuName", "Image", "CategoryId", "Price", "StockQuantity"},
                "StockQuantity > ? AND MenuName LIKE ?",
                new String[]{"0", "%" + normalizedKeyword + "%"},
                null,
                null,
                "MenuName COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) {
                menus.add(readMenu(cursor));
            }
        }
        return menus;
    }

    /** Finds one menu item by ID, including its stock quantity. */
    public Menu getMenuById(int menuId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();

        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_MENU,
                new String[]{"MenuId", "MenuName", "Image", "CategoryId", "Price", "StockQuantity"},
                "MenuId = ?",
                new String[]{String.valueOf(menuId)},
                null,
                null,
                null,
                "1")) {
            if (cursor.moveToFirst()) {
                return readMenu(cursor);
            }
        }
        return null;
    }

    /** Returns true only when the requested positive quantity is in stock. */
    public boolean isStockAvailable(int menuId, int requestedQuantity) {
        if (requestedQuantity <= 0) {
            return false;
        }

        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_MENU,
                new String[]{"StockQuantity"},
                "MenuId = ? AND StockQuantity >= ?",
                new String[]{String.valueOf(menuId), String.valueOf(requestedQuantity)},
                null,
                null,
                null,
                "1")) {
            return cursor.moveToFirst();
        }
    }

    private Menu readMenu(Cursor cursor) {
        return new Menu(
                cursor.getInt(cursor.getColumnIndexOrThrow("MenuId")),
                cursor.getString(cursor.getColumnIndexOrThrow("MenuName")),
                cursor.getString(cursor.getColumnIndexOrThrow("Image")),
                cursor.getInt(cursor.getColumnIndexOrThrow("CategoryId")),
                cursor.getDouble(cursor.getColumnIndexOrThrow("Price")),
                cursor.getInt(cursor.getColumnIndexOrThrow("StockQuantity"))
        );
    }

    public void close() {
        databaseHelper.close();
    }
}
