package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.database.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.User;

import java.util.ArrayList;
import java.util.List;

public class UserDAO {
    private final DatabaseHelper helper;

    public UserDAO(Context context) {
        helper = new DatabaseHelper(context.getApplicationContext());
    }

    public long insert(User user) {
        return helper.getWritableDatabase().insert(DatabaseHelper.TABLE_USER, null, toValues(user));
    }

    public User getById(long userId) {
        try (Cursor cursor = helper.getReadableDatabase().query(DatabaseHelper.TABLE_USER, null,
                "UserId = ?", new String[]{String.valueOf(userId)}, null, null, null)) {
            if (cursor.moveToFirst()) return fromCursor(cursor);
        }
        return null;
    }

    public User getByEmail(String email) {
        try (Cursor cursor = helper.getReadableDatabase().query(DatabaseHelper.TABLE_USER, null,
                "Email = ?", new String[]{email}, null, null, null)) {
            if (cursor.moveToFirst()) return fromCursor(cursor);
        }
        return null;
    }

    public List<User> getAll() {
        List<User> result = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().query(DatabaseHelper.TABLE_USER, null,
                null, null, null, null, "FullName COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public int updateProfile(User user) {
        ContentValues values = new ContentValues();
        values.put("FullName", user.getFullName());
        values.put("Phone", user.getPhone());
        values.put("Address", user.getAddress());
        // Email and role are intentionally not changed by profile editing.
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_USER, values,
                "UserId = ?", new String[]{String.valueOf(user.getUserId())});
    }

    public int updateRole(long userId, String role) {
        if (!User.ROLE_CUSTOMER.equals(role) && !User.ROLE_ADMIN.equals(role)) return 0;
        ContentValues values = new ContentValues();
        values.put("Role", role);
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_USER, values,
                "UserId = ?", new String[]{String.valueOf(userId)});
    }

    public int delete(long userId) {
        return helper.getWritableDatabase().delete(DatabaseHelper.TABLE_USER,
                "UserId = ?", new String[]{String.valueOf(userId)});
    }

    private ContentValues toValues(User user) {
        ContentValues values = new ContentValues();
        values.put("FullName", user.getFullName());
        values.put("Email", user.getEmail());
        values.put("Phone", user.getPhone());
        values.put("Address", user.getAddress());
        values.put("Role", user.getRole());
        // Firebase Authentication handles passwords; never write them to SQLite.
        return values;
    }

    private User fromCursor(Cursor cursor) {
        return new User(
                cursor.getLong(cursor.getColumnIndexOrThrow("UserId")),
                cursor.getString(cursor.getColumnIndexOrThrow("FullName")),
                cursor.getString(cursor.getColumnIndexOrThrow("Email")),
                cursor.getString(cursor.getColumnIndexOrThrow("Phone")),
                cursor.getString(cursor.getColumnIndexOrThrow("Address")),
                cursor.getString(cursor.getColumnIndexOrThrow("Role")));
    }

    public void close() {
        helper.close();
    }
}
