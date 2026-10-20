package com.example.libraryonline.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.LibraryNotificationDao;
import com.example.libraryonline.data.model.LibraryNotification;

public class LibraryNotificationRepository implements AutoCloseable {
    private final DatabaseHelper helper;
    private final LibraryNotificationDao dao;
    public LibraryNotificationRepository(Context context) {
        helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        dao = new LibraryNotificationDao(db);
    }
    public long create(LibraryNotification x) { return dao.insert(x); }
    public java.util.List<LibraryNotification> getByUser(String userId) { return dao.findByUser(userId); }
    public int markRead(String id, String readAt) { return dao.markRead(id, readAt); }
    public int update(LibraryNotification x) { return dao.update(x); }

    @Override public void close() { helper.close(); }
}
