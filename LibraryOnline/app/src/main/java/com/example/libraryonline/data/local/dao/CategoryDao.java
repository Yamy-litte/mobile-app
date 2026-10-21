package com.example.libraryonline.data.local.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

/** SQLite data access for the normalized Categories table. */
public final class CategoryDao {
    public static final class Category {
        public final String categoryId;
        public final String name;
        public final boolean isActive;
        public Category(String categoryId, String name, boolean isActive) {
            this.categoryId = categoryId;
            this.name = name;
            this.isActive = isActive;
        }
    }

    private final SQLiteDatabase db;
    public CategoryDao(SQLiteDatabase db) { this.db = db; }

    public static String idForName(String name) {
        if (name == null || name.trim().isEmpty()) return "khac";
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isEmpty() ? "khac" : normalized;
    }

    public long upsert(String id, String name, boolean active) {
        if (id == null || id.trim().isEmpty()) id = idForName(name);
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Tên thể loại không được để trống");
        ContentValues values = new ContentValues();
        values.put("categoryId", id);
        values.put("name", name.trim());
        values.put("isActive", active ? 1 : 0);
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        values.put("updatedAt", now);
        int changed = db.update("Categories", values, "categoryId = ?", new String[]{id});
        if (changed > 0) return changed;
        values.put("createdAt", now);
        return db.insertOrThrow("Categories", null, values);
    }

    public Category findById(String id) {
        if (id == null) return null;
        try (Cursor cursor = db.query("Categories", new String[]{"categoryId", "name", "isActive"},
                "categoryId = ?", new String[]{id}, null, null, null, "1")) {
            return cursor.moveToFirst()
                    ? new Category(cursor.getString(0), cursor.getString(1), cursor.getInt(2) == 1) : null;
        }
    }

    public List<Category> getAll() {
        List<Category> result = new ArrayList<>();
        try (Cursor c = db.query("Categories", new String[]{"categoryId", "name", "isActive"},
                "isActive = 1", null, null, null, "name COLLATE NOCASE ASC")) {
            while (c.moveToNext()) result.add(new Category(c.getString(0), c.getString(1), c.getInt(2) == 1));
        }
        return result;
    }

    public String getName(String id) {
        if (id == null) return null;
        try (Cursor c = db.query("Categories", new String[]{"name"}, "categoryId = ?",
                new String[]{id}, null, null, null, "1")) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }
}