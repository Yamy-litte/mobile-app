package com.example.libraryonline.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.RegistrationRequestDao;
import com.example.libraryonline.data.model.RegistrationRequest;

public class RegistrationRequestRepository implements AutoCloseable {
    private final DatabaseHelper helper;
    private final RegistrationRequestDao dao;
    public RegistrationRequestRepository(Context context) {
        helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        dao = new RegistrationRequestDao(db);
    }
    public long create(RegistrationRequest x) { return dao.insert(x); }
    public RegistrationRequest getById(String id) { return dao.findById(id); }
    public java.util.List<RegistrationRequest> getPending() { return dao.findPending(); }
    public int update(RegistrationRequest x) { return dao.update(x); }

    @Override public void close() { helper.close(); }
}
