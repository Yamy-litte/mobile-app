package com.example.libraryonline.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.libraryonline.data.local.dao.BookTitleDao;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.model.BookTitle;

import java.util.List;

/** Repository boundary for book catalogue persistence and search. */
public final class BookRepository {
    private final DatabaseHelper helper;

    public BookRepository(Context context) { helper = new DatabaseHelper(context); }

    public long addBookTitle(BookTitle book) {
        return new BookTitleDao(helper.getWritableDatabase()).insert(book);
    }

    public BookTitle getBookTitle(String id) {
        return new BookTitleDao(helper.getReadableDatabase()).findById(id);
    }

    public List<BookTitle> search(String query) {
        return new BookTitleDao(helper.getReadableDatabase()).search(query);
    }

    public int updateBookTitle(BookTitle book) {
        return new BookTitleDao(helper.getWritableDatabase()).update(book);
    }

    public void close() { helper.close(); }
}