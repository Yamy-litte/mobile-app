package com.example.libraryapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Customer;

/** Stores the local Reader profile. Credentials are managed by Firebase Authentication. */
public class CustomerDao {
    private final LibraryDbHelper dbHelper;

    public CustomerDao(Context context) {
        dbHelper = new LibraryDbHelper(context.getApplicationContext());
    }

    public long insert(Customer customer) {
        if (customer == null || isBlank(customer.getFirebaseUid()) || isBlank(customer.getEmail())) {
            return -1;
        }
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

    public Customer getByFirebaseUid(String firebaseUid) {
        if (isBlank(firebaseUid)) return null;
        try (Cursor cursor = dbHelper.getReadableDatabase().query(
                LibraryDbHelper.TABLE_CUSTOMERS, null, "firebase_uid = ?",
                new String[]{firebaseUid.trim()}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public Customer getByEmail(String email) {
        if (isBlank(email)) return null;
        try (Cursor cursor = dbHelper.getReadableDatabase().query(
                LibraryDbHelper.TABLE_CUSTOMERS, null, "email = ?",
                new String[]{email.trim()}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public int updateProfile(Customer customer) {
        if (customer == null || customer.getId() <= 0) return 0;
        ContentValues values = new ContentValues();
        values.put("full_name", customer.getFullName());
        values.put("phone_number", customer.getPhoneNumber());
        values.put("avatar_url", customer.getAvatarUrl());
        values.put("updated_at", customer.getUpdatedAt());
        return dbHelper.getWritableDatabase().update(
                LibraryDbHelper.TABLE_CUSTOMERS, values, "id = ?",
                new String[]{String.valueOf(customer.getId())});
    }

    private ContentValues toValues(Customer customer) {
        ContentValues values = new ContentValues();
        values.put("firebase_uid", customer.getFirebaseUid());
        values.put("email", customer.getEmail());
        values.put("full_name", customer.getFullName());
        values.put("phone_number", customer.getPhoneNumber());
        values.put("avatar_url", customer.getAvatarUrl());
        values.put("status", customer.getStatus() == null ? "active" : customer.getStatus());
        if (!isBlank(customer.getCreatedAt())) values.put("created_at", customer.getCreatedAt());
        if (!isBlank(customer.getUpdatedAt())) values.put("updated_at", customer.getUpdatedAt());
        return values;
    }

    private Customer fromCursor(Cursor cursor) {
        return new Customer(
                cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("firebase_uid")),
                cursor.getString(cursor.getColumnIndexOrThrow("email")),
                cursor.getString(cursor.getColumnIndexOrThrow("full_name")),
                cursor.getString(cursor.getColumnIndexOrThrow("phone_number")),
                cursor.getString(cursor.getColumnIndexOrThrow("avatar_url")),
                cursor.getString(cursor.getColumnIndexOrThrow("status")),
                cursor.getString(cursor.getColumnIndexOrThrow("created_at")),
                cursor.getString(cursor.getColumnIndexOrThrow("updated_at")));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public void close() {
        dbHelper.close();
    }
}
