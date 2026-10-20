package com.example.libraryonline.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.PenaltyPaymentDao;
import com.example.libraryonline.data.model.PenaltyPayment;

public class PenaltyPaymentRepository implements AutoCloseable {
    private final DatabaseHelper helper;
    private final PenaltyPaymentDao dao;
    public PenaltyPaymentRepository(Context context) {
        helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        dao = new PenaltyPaymentDao(db);
    }
    public long create(PenaltyPayment x) { return dao.insert(x); }
    public java.util.List<PenaltyPayment> getByPenalty(String penaltyId) { return dao.findByPenalty(penaltyId); }
    public int update(PenaltyPayment x) { return dao.update(x); }

    @Override public void close() { helper.close(); }
}
