package com.example.libraryonline.data.local.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.model.Penalty;
import java.util.ArrayList;
import java.util.List;

public class PenaltyDao {
    private final SQLiteDatabase db;
    public PenaltyDao(SQLiteDatabase db) { this.db=db; }
    public long insert(Penalty x) { return db.insertOrThrow("Penalties",null,values(x)); }
    public int update(Penalty x) { return db.update("Penalties",values(x),"penaltyId=?",new String[]{x.penaltyId}); }
    public Penalty findById(String id) {
        try(Cursor c=db.query("Penalties",null,"penaltyId=?",new String[]{id},null,null,null)) { return c.moveToFirst()?read(c):null; }
    }
    public List<Penalty> findByUser(String userId) {
        List<Penalty> out=new ArrayList<>();
        try(Cursor c=db.query("Penalties",null,"userId=?",new String[]{userId},null,null,"createdAt DESC")) { while(c.moveToNext()) out.add(read(c)); }
        return out;
    }
    public List<Penalty> findUnpaidByUser(String userId) {
        List<Penalty> out=new ArrayList<>();
        try(Cursor c=db.query("Penalties",null,"userId=? AND penaltyStatus IN ('Unpaid','Partial')",new String[]{userId},null,null,"createdAt DESC")) { while(c.moveToNext()) out.add(read(c)); }
        return out;
    }
    private static ContentValues values(Penalty x) {
        ContentValues v=new ContentValues(); v.put("penaltyId",x.penaltyId); v.put("userId",x.userId); v.put("loanDetailId",x.loanDetailId);
        v.put("copyId",x.copyId); v.put("penaltyType",x.penaltyType); v.put("daysLate",x.daysLate); v.put("amountDue",x.amountDue);
        v.put("amountPaid",x.amountPaid); v.put("penaltyStatus",x.penaltyStatus==null?"Unpaid":x.penaltyStatus); v.put("createdAt",x.createdAt); v.put("paidAt",x.paidAt); return v;
    }
    private static Penalty read(Cursor c) {
        Penalty x=new Penalty(); x.penaltyId=c.getString(c.getColumnIndexOrThrow("penaltyId")); x.userId=c.getString(c.getColumnIndexOrThrow("userId"));
        x.loanDetailId=c.getString(c.getColumnIndexOrThrow("loanDetailId")); x.copyId=c.getString(c.getColumnIndexOrThrow("copyId"));
        x.penaltyType=c.getString(c.getColumnIndexOrThrow("penaltyType")); x.daysLate=c.getInt(c.getColumnIndexOrThrow("daysLate"));
        x.amountDue=c.getInt(c.getColumnIndexOrThrow("amountDue")); x.amountPaid=c.getInt(c.getColumnIndexOrThrow("amountPaid"));
        x.penaltyStatus=c.getString(c.getColumnIndexOrThrow("penaltyStatus")); x.createdAt=c.getString(c.getColumnIndexOrThrow("createdAt")); x.paidAt=c.getString(c.getColumnIndexOrThrow("paidAt")); return x;
    }
}