package com.example.libraryonline.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.AdminDao;
import com.example.libraryonline.data.model.Admin;

public class AdminRepository implements AutoCloseable {
    private final DatabaseHelper helper;
    private final AdminDao dao;
    public AdminRepository(Context context) {
        helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        dao = new AdminDao(db);
    }
    public long create(Admin x) { return dao.insert(x); }
    public Admin getById(String id) { return dao.findById(id); }
    public Admin getByUserId(String userId) { return dao.findByUserId(userId); }
    public int update(Admin x) { return dao.update(x); }

    @Override public void close() { helper.close(); }
}
