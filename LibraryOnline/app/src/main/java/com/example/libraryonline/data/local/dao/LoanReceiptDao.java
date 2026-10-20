package com.example.libraryonline.data.local.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.model.LoanReceipt;
import java.util.ArrayList;
import java.util.List;

public class LoanReceiptDao {
    private final SQLiteDatabase db;
    public LoanReceiptDao(SQLiteDatabase db) { this.db=db; }
    public long insert(LoanReceipt x) { return db.insertOrThrow("LoanReceipts", null, values(x)); }
    public int update(LoanReceipt x) { return db.update("LoanReceipts", values(x), "loanReceiptId=?", new String[]{x.loanReceiptId}); }
    public LoanReceipt findById(String id) {
        try(Cursor c=db.query("LoanReceipts",null,"loanReceiptId=?",new String[]{id},null,null,null)) { return c.moveToFirst()?read(c):null; }
    }
    public List<LoanReceipt> findByUser(String userId) {
        List<LoanReceipt> out=new ArrayList<>();
        try(Cursor c=db.query("LoanReceipts",null,"userId=?",new String[]{userId},null,null,"createdAt DESC")) { while(c.moveToNext()) out.add(read(c)); }
        return out;
    }
    private static ContentValues values(LoanReceipt x) {
        ContentValues v=new ContentValues(); v.put("loanReceiptId",x.loanReceiptId); v.put("userId",x.userId); v.put("adminId",x.adminId);
        v.put("loanType",x.loanType); v.put("receiptStatus",x.receiptStatus==null?"Open":x.receiptStatus);
        v.put("createdAt",x.createdAt); v.put("checkoutAt",x.checkoutAt); v.put("closedAt",x.closedAt); return v;
    }
    private static LoanReceipt read(Cursor c) {
        LoanReceipt x=new LoanReceipt(); x.loanReceiptId=c.getString(c.getColumnIndexOrThrow("loanReceiptId"));
        x.userId=c.getString(c.getColumnIndexOrThrow("userId")); x.adminId=c.getString(c.getColumnIndexOrThrow("adminId"));
        x.loanType=c.getString(c.getColumnIndexOrThrow("loanType")); x.receiptStatus=c.getString(c.getColumnIndexOrThrow("receiptStatus"));
        x.createdAt=c.getString(c.getColumnIndexOrThrow("createdAt")); x.checkoutAt=c.getString(c.getColumnIndexOrThrow("checkoutAt"));
        x.closedAt=c.getString(c.getColumnIndexOrThrow("closedAt")); return x;
    }
}