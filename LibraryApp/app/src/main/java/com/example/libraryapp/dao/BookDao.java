package com.example.libraryapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Book;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite data-access methods for books.
 */
public class BookDao {
    private final LibraryDbHelper dbHelper;

    public BookDao(Context context) {
        dbHelper = new LibraryDbHelper(context.getApplicationContext());
    }

    public long insert(Book book) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.insert(LibraryDbHelper.TABLE_BOOKS, null, toValues(book));
    }

    public int update(Book book) {
        if (book.getId() <= 0) {
            return 0;
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.update(
                LibraryDbHelper.TABLE_BOOKS,
                toValues(book),
                "id = ?",
                new String[]{String.valueOf(book.getId())}
        );
    }

    public int delete(int bookId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(
                LibraryDbHelper.TABLE_BOOKS,
                "id = ?",
                new String[]{String.valueOf(bookId)}
        );
    }

    public Book getById(int bookId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                LibraryDbHelper.TABLE_BOOKS,
                null,
                "id = ?",
                new String[]{String.valueOf(bookId)},
                null,
                null,
                null
        )) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public List<Book> getAll() {
        List<Book> books = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                LibraryDbHelper.TABLE_BOOKS,
                null,
                null,
                null,
                null,
                null,
                "title COLLATE NOCASE ASC"
        )) {
            while (cursor.moveToNext()) {
                books.add(fromCursor(cursor));
            }
        }
        return books;
    }

    public List<Book> search(String query) {
        List<Book> books = new ArrayList<>();
        String term = query == null ? "" : query.trim();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                LibraryDbHelper.TABLE_BOOKS,
                null,
                "title LIKE ? OR author LIKE ? OR publisher LIKE ?",
                new String[]{"%" + term + "%", "%" + term + "%", "%" + term + "%"},
                null,
                null,
                "title COLLATE NOCASE ASC"
        )) {
            while (cursor.moveToNext()) {
                books.add(fromCursor(cursor));
            }
        }
        return books;
    }

    private ContentValues toValues(Book book) {
        ContentValues values = new ContentValues();
        values.put("category_id", book.getCategoryId());
        values.put("location_id", book.getLocationId());
        values.put("title", book.getTitle());
        values.put("author", book.getAuthor());
        values.put("publisher", book.getPublisher());
        values.put("publish_year", book.getPublishYear());
        values.put("summary", book.getSummary());
        values.put("image_url", book.getImageUrl());
        values.put("active", book.isActive() ? 1 : 0);
        values.put("pages", book.getPages());
        values.put("total_quantity", book.getTotalQuantity());
        values.put("available_quantity", book.getAvailableQuantity());
        return values;
    }

    private Book fromCursor(Cursor cursor) {
        Book book = new Book(
                cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                cursor.getInt(cursor.getColumnIndexOrThrow("category_id")),
                cursor.getInt(cursor.getColumnIndexOrThrow("location_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getString(cursor.getColumnIndexOrThrow("author")),
                cursor.getString(cursor.getColumnIndexOrThrow("publisher")),
                cursor.getInt(cursor.getColumnIndexOrThrow("publish_year")),
                cursor.getString(cursor.getColumnIndexOrThrow("summary")),
                cursor.getInt(cursor.getColumnIndexOrThrow("pages")),
                cursor.getInt(cursor.getColumnIndexOrThrow("total_quantity")),
                cursor.getInt(cursor.getColumnIndexOrThrow("available_quantity"))
        );
        int imageColumn = cursor.getColumnIndex("image_url");
        if (imageColumn >= 0) book.setImageUrl(cursor.getString(imageColumn));
        int activeColumn = cursor.getColumnIndex("active");
        if (activeColumn >= 0) book.setActive(cursor.getInt(activeColumn) == 1);
        return book;
    }

    public void close() {
        dbHelper.close();
    }
}
