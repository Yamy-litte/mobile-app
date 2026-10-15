package com.example.libraryapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Admin;
import java.util.ArrayList;
import java.util.List;

/** Stores administrator profile data only; Firebase Authentication owns credentials. */
public class AdminDao {
    private final LibraryDbHelper helper;
    public AdminDao(Context context) { helper = new LibraryDbHelper(context.getApplicationContext()); }

    public long insert(Admin item) {
        if (item == null || blank(item.getFirebaseUid()) || blank(item.getEmail())) return -1;
        return helper.getWritableDatabase().insert(LibraryDbHelper.TABLE_ADMIN, null, values(item));
    }
    public int update(Admin item) {
        if (item == null || item.getId() <= 0) return 0;
        return helper.getWritableDatabase().update(LibraryDbHelper.TABLE_ADMIN, values(item), "id = ?", new String[]{String.valueOf(item.getId())});
    }
    public int delete(int id) { return helper.getWritableDatabase().delete(LibraryDbHelper.TABLE_ADMIN, "id = ?", new String[]{String.valueOf(id)}); }
    public Admin getById(int id) {
        try (Cursor c = helper.getReadableDatabase().query(LibraryDbHelper.TABLE_ADMIN, null, "id = ?", new String[]{String.valueOf(id)}, null, null, null)) { return c.moveToFirst() ? read(c) : null; }
    }
    public Admin getByFirebaseUid(String uid) {
        if (blank(uid)) return null;
        try (Cursor c = helper.getReadableDatabase().query(LibraryDbHelper.TABLE_ADMIN, null, "firebase_uid = ?", new String[]{uid.trim()}, null, null, null)) { return c.moveToFirst() ? read(c) : null; }
    }
    public Admin getByEmail(String email) {
        if (blank(email)) return null;
        try (Cursor c = helper.getReadableDatabase().query(LibraryDbHelper.TABLE_ADMIN, null, "email = ?", new String[]{email.trim()}, null, null, null)) { return c.moveToFirst() ? read(c) : null; }
    }
    public List<Admin> getAll() {
        List<Admin> list = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query(LibraryDbHelper.TABLE_ADMIN, null, null, null, null, null, "full_name COLLATE NOCASE ASC")) { while (c.moveToNext()) list.add(read(c)); }
        return list;
    }
    private ContentValues values(Admin x) {
        ContentValues v = new ContentValues(); v.put("firebase_uid", x.getFirebaseUid()); v.put("email", x.getEmail());
        v.put("full_name", x.getFullName()); v.put("status", x.getStatus() == null ? "active" : x.getStatus());
        // Let SQLite apply DEFAULT CURRENT_TIMESTAMP when no creation time is supplied.
        if (!blank(x.getCreatedAt())) v.put("created_at", x.getCreatedAt());
        return v;
    }
    private Admin read(Cursor c) { return new Admin(c.getInt(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("firebase_uid")), c.getString(c.getColumnIndexOrThrow("email")), c.getString(c.getColumnIndexOrThrow("full_name")), c.getString(c.getColumnIndexOrThrow("status")), c.getString(c.getColumnIndexOrThrow("created_at"))); }
    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    public void close() { helper.close(); }
}
