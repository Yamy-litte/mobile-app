package com.example.libraryonline.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.LoanReceiptDao;
import com.example.libraryonline.data.model.LoanReceipt;

public class LoanReceiptRepository implements AutoCloseable {
    private final DatabaseHelper helper;
    private final LoanReceiptDao dao;
    public LoanReceiptRepository(Context context) {
        helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        dao = new LoanReceiptDao(db);
    }
    public long create(LoanReceipt x) { return dao.insert(x); }
    public LoanReceipt getById(String id) { return dao.findById(id); }
    public java.util.List<LoanReceipt> getByUser(String userId) { return dao.findByUser(userId); }
    public int update(LoanReceipt x) { return dao.update(x); }

    @Override public void close() { helper.close(); }
}
