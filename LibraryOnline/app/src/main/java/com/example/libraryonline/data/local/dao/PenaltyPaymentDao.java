package com.example.libraryonline.data.local.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.model.PenaltyPayment;
import java.util.ArrayList;
import java.util.List;

public class PenaltyPaymentDao {
    private final SQLiteDatabase db;
    public PenaltyPaymentDao(SQLiteDatabase db) { this.db=db; }
    public long insert(PenaltyPayment x) { return db.insertOrThrow("PenaltyPayments",null,values(x)); }
    public int update(PenaltyPayment x) { return db.update("PenaltyPayments",values(x),"paymentId=?",new String[]{x.paymentId}); }
    public List<PenaltyPayment> findByPenalty(String penaltyId) {
        List<PenaltyPayment> out=new ArrayList<>();
        try(Cursor c=db.query("PenaltyPayments",null,"penaltyId=?",new String[]{penaltyId},null,null,"receivedAt DESC")) { while(c.moveToNext()) out.add(read(c)); }
        return out;
    }
    private static ContentValues values(PenaltyPayment x) {
        ContentValues v=new ContentValues(); v.put("paymentId",x.paymentId); v.put("penaltyId",x.penaltyId); v.put("receivedBy",x.receivedBy);
        v.put("amountReceived",x.amountReceived); v.put("paymentMethod",x.paymentMethod); v.put("paymentStatus",x.paymentStatus==null?"Confirmed":x.paymentStatus);
        v.put("receivedAt",x.receivedAt); v.put("note",x.note); return v;
    }
    private static PenaltyPayment read(Cursor c) {
        PenaltyPayment x=new PenaltyPayment(); x.paymentId=c.getString(c.getColumnIndexOrThrow("paymentId")); x.penaltyId=c.getString(c.getColumnIndexOrThrow("penaltyId"));
        x.receivedBy=c.getString(c.getColumnIndexOrThrow("receivedBy")); x.amountReceived=c.getInt(c.getColumnIndexOrThrow("amountReceived"));
        x.paymentMethod=c.getString(c.getColumnIndexOrThrow("paymentMethod")); x.paymentStatus=c.getString(c.getColumnIndexOrThrow("paymentStatus"));
        x.receivedAt=c.getString(c.getColumnIndexOrThrow("receivedAt")); x.note=c.getString(c.getColumnIndexOrThrow("note")); return x;
    }
}