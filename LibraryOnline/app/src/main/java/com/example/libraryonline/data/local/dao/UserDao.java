package com.example.libraryonline.data.local.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.libraryonline.data.model.User;

import java.util.ArrayList;
import java.util.List;

/** SQLite data-access operations for Users. Caller owns the database lifecycle. */
public final class UserDao {
    private final SQLiteDatabase db;
    public UserDao(SQLiteDatabase db) { this.db = db; }

    public long insert(User user) {
        return db.insertOrThrow("Users", null, toValues(user));
    }

    public int update(User user) {
        if (user.userId == null || user.userId.trim().isEmpty()) throw new IllegalArgumentException("userId is required");
        return db.update("Users", toValues(user), "userId = ?", new String[]{user.userId});
    }

    public User findById(String userId) {
        try (Cursor c = db.query("Users", null, "userId = ?", new String[]{userId}, null, null, null, "1")) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public User findByEmail(String email) {
        try (Cursor c = db.query("Users", null, "email = ? COLLATE NOCASE", new String[]{email}, null, null, null, "1")) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public List<User> getActiveUsers() {
        List<User> users = new ArrayList<>();
        try (Cursor c = db.query("Users", null, "isDeleted = 0", null, null, null, "fullName COLLATE NOCASE ASC")) {
            while (c.moveToNext()) users.add(fromCursor(c));
        }
        return users;
    }

    private static ContentValues toValues(User u) {
        ContentValues v = new ContentValues();
        put(v, "userId", u.userId); put(v, "fullName", u.fullName); put(v, "dateOfBirth", u.dateOfBirth);
        put(v, "email", u.email); put(v, "phone", u.phone); put(v, "barcode", u.barcode);
        put(v, "role", u.role == null ? "Reader" : u.role);
        put(v, "cardStatus", u.cardStatus == null ? "ChoDuyet" : u.cardStatus);
        put(v, "cardExpiryDate", u.cardExpiryDate); v.put("isDeleted", u.isDeleted ? 1 : 0);
        put(v, "createdAt", u.createdAt); put(v, "updatedAt", u.updatedAt);
        return v;
    }

    private static User fromCursor(Cursor c) {
        User u = new User();
        u.userId = c.getString(c.getColumnIndexOrThrow("userId"));
        u.fullName = c.getString(c.getColumnIndexOrThrow("fullName"));
        u.dateOfBirth = c.getString(c.getColumnIndexOrThrow("dateOfBirth"));
        u.email = c.getString(c.getColumnIndexOrThrow("email"));
        u.phone = c.getString(c.getColumnIndexOrThrow("phone"));
        u.barcode = c.getString(c.getColumnIndexOrThrow("barcode"));
        u.role = c.getString(c.getColumnIndexOrThrow("role"));
        u.cardStatus = c.getString(c.getColumnIndexOrThrow("cardStatus"));
        u.cardExpiryDate = c.getString(c.getColumnIndexOrThrow("cardExpiryDate"));
        u.isDeleted = c.getInt(c.getColumnIndexOrThrow("isDeleted")) == 1;
        u.createdAt = c.getString(c.getColumnIndexOrThrow("createdAt"));
        u.updatedAt = c.getString(c.getColumnIndexOrThrow("updatedAt"));
        return u;
    }

    private static void put(ContentValues v, String key, String value) {
        if (value == null) v.putNull(key); else v.put(key, value);
    }
}