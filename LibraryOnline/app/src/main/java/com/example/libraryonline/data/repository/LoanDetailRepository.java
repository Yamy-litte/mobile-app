package com.example.libraryonline.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.LoanDetailDao;
import com.example.libraryonline.data.model.LoanDetail;

public class LoanDetailRepository implements AutoCloseable {
    private final DatabaseHelper helper;
    private final LoanDetailDao dao;
    public LoanDetailRepository(Context context) {
        helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        dao = new LoanDetailDao(db);
    }
    public long create(LoanDetail x) { return dao.insert(x); }
    public LoanDetail getById(String id) { return dao.findById(id); }
    public java.util.List<LoanDetail> getByReceipt(String receiptId) { return dao.findByReceipt(receiptId); }
    public java.util.List<LoanDetail> getActiveByUser(String userId) { return dao.findActiveByUser(userId); }
    public java.util.List<LoanDetail> getReturnedByUser(String userId) { return dao.findReturnedByUser(userId); }
    public int update(LoanDetail x) { return dao.update(x); }

    @Override public void close() { helper.close(); }
}
