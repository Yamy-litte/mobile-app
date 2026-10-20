package com.example.libraryonline.data.local.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.model.Reservation;
import java.util.ArrayList;
import java.util.List;

public class ReservationDao {
    private final SQLiteDatabase db;
    public ReservationDao(SQLiteDatabase db) { this.db=db; }
    public long insert(Reservation x) { return db.insertOrThrow("Reservations",null,values(x)); }
    public int update(Reservation x) { return db.update("Reservations",values(x),"reservationId=?",new String[]{x.reservationId}); }
    public Reservation findById(String id) {
        try(Cursor c=db.query("Reservations",null,"reservationId=?",new String[]{id},null,null,null)) { return c.moveToFirst()?read(c):null; }
    }
    public List<Reservation> findQueue(String titleId) {
        List<Reservation> out=new ArrayList<>();
        try(Cursor c=db.query("Reservations",null,"bookTitleId=? AND reservationStatus='Pending'",new String[]{titleId},null,null,"queuePosition ASC, createdAt ASC")) { while(c.moveToNext()) out.add(read(c)); }
        return out;
    }
    private static ContentValues values(Reservation x) {
        ContentValues v=new ContentValues(); v.put("reservationId",x.reservationId); v.put("userId",x.userId); v.put("bookTitleId",x.bookTitleId);
        v.put("copyId",x.copyId); v.put("reservationType",x.reservationType); v.put("reservationStatus",x.reservationStatus==null?"Pending":x.reservationStatus);
        if(x.queuePosition==null) v.putNull("queuePosition"); else v.put("queuePosition",x.queuePosition);
        v.put("requestedLoanDays",x.requestedLoanDays); v.put("createdAt",x.createdAt); v.put("holdUntil",x.holdUntil);
        v.put("priorityUntil",x.priorityUntil); v.put("notifiedAt",x.notifiedAt); v.put("cancelledAt",x.cancelledAt); return v;
    }
    private static Reservation read(Cursor c) {
        Reservation x=new Reservation(); x.reservationId=c.getString(c.getColumnIndexOrThrow("reservationId")); x.userId=c.getString(c.getColumnIndexOrThrow("userId"));
        x.bookTitleId=c.getString(c.getColumnIndexOrThrow("bookTitleId")); x.copyId=c.getString(c.getColumnIndexOrThrow("copyId"));
        x.reservationType=c.getString(c.getColumnIndexOrThrow("reservationType")); x.reservationStatus=c.getString(c.getColumnIndexOrThrow("reservationStatus"));
        int q=c.getColumnIndexOrThrow("queuePosition"); x.queuePosition=c.isNull(q)?null:c.getInt(q);
        x.requestedLoanDays=c.getInt(c.getColumnIndexOrThrow("requestedLoanDays")); x.createdAt=c.getString(c.getColumnIndexOrThrow("createdAt"));
        x.holdUntil=c.getString(c.getColumnIndexOrThrow("holdUntil")); x.priorityUntil=c.getString(c.getColumnIndexOrThrow("priorityUntil"));
        x.notifiedAt=c.getString(c.getColumnIndexOrThrow("notifiedAt")); x.cancelledAt=c.getString(c.getColumnIndexOrThrow("cancelledAt")); return x;
    }
}