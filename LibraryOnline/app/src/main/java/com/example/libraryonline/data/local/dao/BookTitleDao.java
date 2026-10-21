package com.example.libraryonline.data.local.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.libraryonline.data.model.BookTitle;

import java.util.ArrayList;
import java.util.List;

/** SQLite data-access operations for book-title catalogue records. */
public final class BookTitleDao {
    private final SQLiteDatabase db;
    public BookTitleDao(SQLiteDatabase db) { this.db = db; }

    public long insert(BookTitle b) { return db.insertOrThrow("BookTitles", null, toValues(b)); }

    /** Insert or replace a catalogue row received from Firestore. */
    public long upsertFromCloud(BookTitle book) {
        if (book == null || book.bookTitleId == null || book.bookTitleId.trim().isEmpty()) {
            throw new IllegalArgumentException("bookTitleId is required");
        }
        ContentValues values = toValues(book);
        int updated = db.update("BookTitles", values, "bookTitleId = ?",
                new String[]{book.bookTitleId});
        if (updated > 0) return updated;
        return db.insertOrThrow("BookTitles", null, values);
    }

    /** Hide a locally mirrored row when its Firestore document is removed. */
    public int markHiddenById(String id) {
        ContentValues values = new ContentValues();
        values.put("isHidden", 1);
        return db.update("BookTitles", values, "bookTitleId = ?", new String[]{id});
    }

    public int update(BookTitle b) {
        if (b.bookTitleId == null || b.bookTitleId.trim().isEmpty()) throw new IllegalArgumentException("bookTitleId is required");
        return db.update("BookTitles", toValues(b), "bookTitleId = ?", new String[]{b.bookTitleId});
    }

    public List<BookTitle> getAll() {
        List<BookTitle> result = new ArrayList<>();
        try (Cursor c = db.query("BookTitles", null, null, null, null, null, "bookTitleId ASC")) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    public BookTitle findById(String id) {
        try (Cursor c = db.query("BookTitles", null, "bookTitleId = ?", new String[]{id}, null, null, null, "1")) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public List<BookTitle> search(String query) {
        List<BookTitle> result = new ArrayList<>();
        String q = query == null ? "" : query.trim();
        try (Cursor c = db.query("BookTitles", null,
                "isHidden = 0 AND (title LIKE ? OR author LIKE ? OR category LIKE ?)",
                new String[]{"%" + q + "%", "%" + q + "%", "%" + q + "%"},
                null, null, "title COLLATE NOCASE ASC")) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    private static ContentValues toValues(BookTitle b) {
        ContentValues v = new ContentValues();
        put(v,"bookTitleId",b.bookTitleId); put(v,"title",b.title); put(v,"author",b.author);
        put(v,"isbn",b.isbn); put(v,"categoryId",b.categoryId); put(v,"category",b.category); put(v,"publisher",b.publisher);
        if (b.publishYear == null) v.putNull("publishYear"); else v.put("publishYear", b.publishYear);
        put(v,"description",b.description); put(v,"coverUrl",b.coverUrl);
        v.put("totalCopies",b.totalCopies); v.put("availableCopies",b.availableCopies);
        v.put("isHidden",b.isHidden ? 1 : 0); put(v,"createdAt",b.createdAt); put(v,"updatedAt",b.updatedAt);
        return v;
    }

    private static BookTitle fromCursor(Cursor c) {
        BookTitle b = new BookTitle();
        b.bookTitleId=c.getString(c.getColumnIndexOrThrow("bookTitleId"));
        b.title=c.getString(c.getColumnIndexOrThrow("title")); b.author=c.getString(c.getColumnIndexOrThrow("author"));
        b.isbn=c.getString(c.getColumnIndexOrThrow("isbn")); b.categoryId=c.getString(c.getColumnIndexOrThrow("categoryId")); b.category=c.getString(c.getColumnIndexOrThrow("category"));
        b.publisher=c.getString(c.getColumnIndexOrThrow("publisher"));
        int year=c.getColumnIndexOrThrow("publishYear"); b.publishYear=c.isNull(year) ? null : c.getInt(year);
        b.description=c.getString(c.getColumnIndexOrThrow("description")); b.coverUrl=c.getString(c.getColumnIndexOrThrow("coverUrl"));
        b.totalCopies=c.getInt(c.getColumnIndexOrThrow("totalCopies")); b.availableCopies=c.getInt(c.getColumnIndexOrThrow("availableCopies"));
        b.isHidden=c.getInt(c.getColumnIndexOrThrow("isHidden")) == 1;
        b.createdAt=c.getString(c.getColumnIndexOrThrow("createdAt")); b.updatedAt=c.getString(c.getColumnIndexOrThrow("updatedAt"));
        return b;
    }

    private static void put(ContentValues v, String key, String value) {
        if (value == null) v.putNull(key); else v.put(key, value);
    }
}