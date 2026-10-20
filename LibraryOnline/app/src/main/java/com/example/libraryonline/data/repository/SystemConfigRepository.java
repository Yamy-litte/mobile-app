package com.example.libraryonline.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.SystemConfigDao;
import com.example.libraryonline.data.model.SystemConfig;

public class SystemConfigRepository implements AutoCloseable {
    private final DatabaseHelper helper;
    private final SystemConfigDao dao;
    public SystemConfigRepository(Context context) {
        helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        dao = new SystemConfigDao(db);
    }
    public SystemConfig getCurrent() { return dao.getCurrent(); }
    public long create(SystemConfig x) { return dao.insert(x); }
    public int update(SystemConfig x) { return dao.update(x); }

    @Override public void close() { helper.close(); }
}
