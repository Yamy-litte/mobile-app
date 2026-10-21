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
        long rowId = new BookTitleDao(helper.getWritableDatabase()).insert(book);
        BookCatalogueSyncManager.uploadBookToCloud(book);
        return rowId;
    }

    public BookTitle getBookTitle(String id) {
        return new BookTitleDao(helper.getReadableDatabase()).findById(id);
    }

    public List<BookTitle> search(String query) {
        return new BookTitleDao(helper.getReadableDatabase()).search(query);
    }

    public int updateBookTitle(BookTitle book) {
        int changed = new BookTitleDao(helper.getWritableDatabase()).update(book);
        if (changed > 0) BookCatalogueSyncManager.uploadBookToCloud(book);
        return changed;
    }

    public void close() { helper.close(); }
}