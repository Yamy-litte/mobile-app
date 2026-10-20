package com.example.libraryonline.data.local.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.model.LoanDetail;
import java.util.ArrayList;
import java.util.List;

public class LoanDetailDao {
    private final SQLiteDatabase db;
    public LoanDetailDao(SQLiteDatabase db) { this.db=db; }
    public long insert(LoanDetail x) { return db.insertOrThrow("LoanDetails",null,values(x)); }
    public int update(LoanDetail x) { return db.update("LoanDetails",values(x),"loanDetailId=?",new String[]{x.loanDetailId}); }
    public LoanDetail findById(String id) {
        try(Cursor c=db.query("LoanDetails",null,"loanDetailId=?",new String[]{id},null,null,null)) { return c.moveToFirst()?read(c):null; }
    }
    public List<LoanDetail> findByReceipt(String receiptId) {
        List<LoanDetail> out=new ArrayList<>();
        try(Cursor c=db.query("LoanDetails",null,"loanReceiptId=?",new String[]{receiptId},null,null,"createdAt ASC")) { while(c.moveToNext()) out.add(read(c)); }
        return out;
    }
    public List<LoanDetail> findActiveByUser(String userId) {
        List<LoanDetail> out=new ArrayList<>();
        String sql="SELECT d.* FROM LoanDetails d JOIN LoanReceipts r ON r.loanReceiptId=d.loanReceiptId WHERE r.userId=? AND d.returnDate IS NULL AND d.detailStatus IN ('DangMuon','QuaHan') ORDER BY d.dueDate ASC";
        try(Cursor c=db.rawQuery(sql,new String[]{userId})) { while(c.moveToNext()) out.add(read(c)); }
        return out;
    }
    private static ContentValues values(LoanDetail x) {
        ContentValues v=new ContentValues(); v.put("loanDetailId",x.loanDetailId); v.put("loanReceiptId",x.loanReceiptId); v.put("copyId",x.copyId);
        v.put("borrowDate",x.borrowDate); v.put("dueDate",x.dueDate); v.put("returnDate",x.returnDate);
        v.put("detailStatus",x.detailStatus==null?"DangMuon":x.detailStatus); v.put("extensionCount",x.extensionCount);
        v.put("extended",x.extended?1:0); v.put("originalDueDate",x.originalDueDate); v.put("createdAt",x.createdAt); return v;
    }
    private static LoanDetail read(Cursor c) {
        LoanDetail x=new LoanDetail(); x.loanDetailId=c.getString(c.getColumnIndexOrThrow("loanDetailId"));
        x.loanReceiptId=c.getString(c.getColumnIndexOrThrow("loanReceiptId")); x.copyId=c.getString(c.getColumnIndexOrThrow("copyId"));
        x.borrowDate=c.getString(c.getColumnIndexOrThrow("borrowDate")); x.dueDate=c.getString(c.getColumnIndexOrThrow("dueDate"));
        x.returnDate=c.getString(c.getColumnIndexOrThrow("returnDate")); x.detailStatus=c.getString(c.getColumnIndexOrThrow("detailStatus"));
        x.extensionCount=c.getInt(c.getColumnIndexOrThrow("extensionCount")); x.extended=c.getInt(c.getColumnIndexOrThrow("extended"))==1;
        x.originalDueDate=c.getString(c.getColumnIndexOrThrow("originalDueDate")); x.createdAt=c.getString(c.getColumnIndexOrThrow("createdAt")); return x;
    }
}