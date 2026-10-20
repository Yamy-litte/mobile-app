package com.example.libraryonline.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.BookCopyDao;
import com.example.libraryonline.data.model.BookCopy;

public class BookCopyRepository implements AutoCloseable {
    private final DatabaseHelper helper;
    private final BookCopyDao dao;
    public BookCopyRepository(Context context) {
        helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        dao = new BookCopyDao(db);
    }
    public long add(BookCopy x) { return dao.insert(x); }
    public BookCopy getById(String id) { return dao.findById(id); }
    public java.util.List<BookCopy> getByTitle(String titleId) { return dao.findByTitle(titleId); }
    public java.util.List<BookCopy> getAvailableByTitle(String titleId) { return dao.findAvailableByTitle(titleId); }
    public int update(BookCopy x) { return dao.update(x); }

    @Override public void close() { helper.close(); }
}
