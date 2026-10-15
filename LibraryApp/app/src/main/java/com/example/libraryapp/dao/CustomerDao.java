package com.example.libraryapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Customer;

/** SQLite data-access methods for reader/customer accounts. */
public class CustomerDao {
    private final LibraryDbHelper dbHelper;

    public CustomerDao(Context context) {
        dbHelper = new LibraryDbHelper(context.getApplicationContext());
    }

    public long insert(Customer customer) {
        return dbHelper.getWritableDatabase().insert(
                LibraryDbHelper.TABLE_CUSTOMERS, null, toValues(customer));
    }

    public Customer getById(int customerId) {
        try (Cursor cursor = dbHelper.getReadableDatabase().query(
                LibraryDbHelper.TABLE_CUSTOMERS, null, "id = ?",
                new String[]{String.valueOf(customerId)}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public Customer getByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        try (Cursor cursor = dbHelper.getReadableDatabase().query(
                LibraryDbHelper.TABLE_CUSTOMERS, null, "email = ?",
                new String[]{email.trim()}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public int updateProfile(Customer customer) {
        if (customer.getId() <= 0) {
            return 0;
        }
        ContentValues values = new ContentValues();
        values.put("full_name", customer.getFullName());
        values.put("phone_number", customer.getPhoneNumber());
        values.put("avatar_url", customer.getAvatarUrl());
        values.put("updated_at", customer.getUpdatedAt());
        return dbHelper.getWritableDatabase().update(
                LibraryDbHelper.TABLE_CUSTOMERS, values, "id = ?",
                new String[]{String.valueOf(customer.getId())});
    }

    public int updatePassword(int customerId, String passwordHash) {
        if (customerId <= 0 || passwordHash == null || passwordHash.isEmpty()) {
            return 0;
        }
        ContentValues values = new ContentValues();
        values.put("password_hash", passwordHash);
        values.put("updated_at", String.valueOf(System.currentTimeMillis()));
        return dbHelper.getWritableDatabase().update(
                LibraryDbHelper.TABLE_CUSTOMERS, values, "id = ?",
                new String[]{String.valueOf(customerId)});
    }

    private ContentValues toValues(Customer customer) {
        ContentValues values = new ContentValues();
        values.put("email", customer.getEmail());
        values.put("password_hash", customer.getPasswordHash());
        values.put("full_name", customer.getFullName());
        values.put("phone_number", customer.getPhoneNumber());
        values.put("avatar_url", customer.getAvatarUrl());
        values.put("status", customer.getStatus() == null ? "active" : customer.getStatus());
        values.put("created_at", customer.getCreatedAt());
        values.put("updated_at", customer.getUpdatedAt());
        return values;
    }

    private Customer fromCursor(Cursor cursor) {
        return new Customer(
                cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("email")),
                cursor.getString(cursor.getColumnIndexOrThrow("password_hash")),
                cursor.getString(cursor.getColumnIndexOrThrow("full_name")),
                cursor.getString(cursor.getColumnIndexOrThrow("phone_number")),
                cursor.getString(cursor.getColumnIndexOrThrow("avatar_url")),
                cursor.getString(cursor.getColumnIndexOrThrow("status")),
                cursor.getString(cursor.getColumnIndexOrThrow("created_at")),
                cursor.getString(cursor.getColumnIndexOrThrow("updated_at")));
    }

    public void close() {
        dbHelper.close();
    }
}
