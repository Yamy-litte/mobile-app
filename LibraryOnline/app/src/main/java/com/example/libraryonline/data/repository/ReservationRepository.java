package com.example.libraryonline.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.ReservationDao;
import com.example.libraryonline.data.model.Reservation;

public class ReservationRepository implements AutoCloseable {
    private final DatabaseHelper helper;
    private final ReservationDao dao;
    public ReservationRepository(Context context) {
        helper = new DatabaseHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        dao = new ReservationDao(db);
    }
    public long create(Reservation x) { return dao.insert(x); }
    public Reservation getById(String id) { return dao.findById(id); }
    public java.util.List<Reservation> getQueue(String titleId) { return dao.findQueue(titleId); }
    public int update(Reservation x) { return dao.update(x); }

    @Override public void close() { helper.close(); }
}
