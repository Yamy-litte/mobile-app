package com.example.libraryonline.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.PenaltyDao;
import com.example.libraryonline.data.model.Penalty;

public class PenaltyRepository implements AutoCloseable {
    private final DatabaseHelper helper;
    private final PenaltyDao dao;
    public PenaltyRepository(Context context) {
        helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        dao = new PenaltyDao(db);
    }
    public long create(Penalty x) { return dao.insert(x); }
    public Penalty getById(String id) { return dao.findById(id); }
    public java.util.List<Penalty> getByUser(String userId) { return dao.findByUser(userId); }
    public java.util.List<Penalty> getUnpaidByUser(String userId) { return dao.findUnpaidByUser(userId); }
    public int update(Penalty x) { return dao.update(x); }

    @Override public void close() { helper.close(); }
}
