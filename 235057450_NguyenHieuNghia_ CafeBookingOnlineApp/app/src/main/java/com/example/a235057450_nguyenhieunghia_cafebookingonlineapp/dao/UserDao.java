package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.database.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.User;

/**
 * Stores the local user profile and role after Firebase Authentication succeeds.
 * Passwords must never be stored in this table.
 */
public class UserDao {
    private final DatabaseHelper databaseHelper;

    public UserDao(Context context) {
        databaseHelper = new DatabaseHelper(context.getApplicationContext());
    }

    public User getByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_USERS,
                new String[]{"UserId", "Email", "Role"},
                "Email = ? COLLATE NOCASE",
                new String[]{email.trim()},
                null, null, null, "1")) {
            if (cursor.moveToFirst()) {
                return new User(
                        cursor.getInt(cursor.getColumnIndexOrThrow("UserId")),
                        cursor.getString(cursor.getColumnIndexOrThrow("Email")),
                        cursor.getString(cursor.getColumnIndexOrThrow("Role"))
                );
            }
        }
        return null;
    }

    public User getById(int userId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_USERS,
                new String[]{"UserId", "Email", "Role"},
                "UserId = ?",
                new String[]{String.valueOf(userId)},
                null, null, null, "1")) {
            if (cursor.moveToFirst()) {
                return new User(
                        cursor.getInt(cursor.getColumnIndexOrThrow("UserId")),
                        cursor.getString(cursor.getColumnIndexOrThrow("Email")),
                        cursor.getString(cursor.getColumnIndexOrThrow("Role"))
                );
            }
        }
        return null;
    }

    /**
     * Creates or updates a local profile. Role is restricted to Customer/Admin.
     * Returns the local UserId, or -1 when validation/database work fails.
     */
    public long saveProfile(String email, String role) {
        if (email == null || email.trim().isEmpty() ||
                role == null || !("Customer".equals(role) || "Admin".equals(role))) {
            return -1;
        }

        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("Email", normalizedEmail);
        values.put("Role", role);

        try {
            long existingId = -1;
            try (Cursor cursor = db.query(
                    DatabaseHelper.TABLE_USERS,
                    new String[]{"UserId"},
                    "Email = ? COLLATE NOCASE",
                    new String[]{normalizedEmail},
                    null, null, null, "1")) {
                if (cursor.moveToFirst()) {
                    existingId = cursor.getLong(cursor.getColumnIndexOrThrow("UserId"));
                }
            }

            if (existingId > 0) {
                String existingRole = null;
                try (Cursor cursor = db.query(
                        DatabaseHelper.TABLE_USERS,
                        new String[]{"Role"},
                        "UserId = ?",
                        new String[]{String.valueOf(existingId)},
                        null, null, null, "1")) {
                    if (cursor.moveToFirst()) {
                        existingRole = cursor.getString(0);
                    }
                }
                // Never allow a login/register flow to overwrite an existing role.
                if (!role.equals(existingRole)) {
                    return -1;
                }
                db.update(DatabaseHelper.TABLE_USERS, values,
                        "UserId = ?", new String[]{String.valueOf(existingId)});
                return existingId;
            }
            return db.insert(DatabaseHelper.TABLE_USERS, null, values);
        } catch (android.database.sqlite.SQLiteException ex) {
            return -1;
        }
    }

    public void close() {
        databaseHelper.close();
    }
}
