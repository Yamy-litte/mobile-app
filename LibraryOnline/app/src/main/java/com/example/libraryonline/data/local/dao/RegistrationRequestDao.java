package com.example.libraryonline.data.local.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.model.RegistrationRequest;
import java.util.ArrayList;
import java.util.List;

public class RegistrationRequestDao {
    private final SQLiteDatabase db;
    public RegistrationRequestDao(SQLiteDatabase db) { this.db=db; }
    public long insert(RegistrationRequest x) { return db.insertOrThrow("RegistrationRequests",null,values(x)); }
    public int update(RegistrationRequest x) { return db.update("RegistrationRequests",values(x),"requestId=?",new String[]{x.requestId}); }
    public RegistrationRequest findById(String id) {
        try(Cursor c=db.query("RegistrationRequests",null,"requestId=?",new String[]{id},null,null,null)) { return c.moveToFirst()?read(c):null; }
    }
    public List<RegistrationRequest> findPending() {
        List<RegistrationRequest> out=new ArrayList<>();
        try(Cursor c=db.query("RegistrationRequests",null,"status='Pending'",null,null,null,"createdAt ASC")) { while(c.moveToNext()) out.add(read(c)); }
        return out;
    }
    private static ContentValues values(RegistrationRequest x) {
        ContentValues v=new ContentValues(); v.put("requestId",x.requestId); v.put("fullName",x.fullName); v.put("dateOfBirth",x.dateOfBirth);
        v.put("email",x.email); v.put("phone",x.phone); v.put("status",x.status==null?"Pending":x.status); v.put("rejectionReason",x.rejectionReason);
        v.put("reviewedBy",x.reviewedBy); v.put("reviewedAt",x.reviewedAt); v.put("createdAt",x.createdAt); return v;
    }
    private static RegistrationRequest read(Cursor c) {
        RegistrationRequest x=new RegistrationRequest(); x.requestId=c.getString(c.getColumnIndexOrThrow("requestId")); x.fullName=c.getString(c.getColumnIndexOrThrow("fullName"));
        x.dateOfBirth=c.getString(c.getColumnIndexOrThrow("dateOfBirth")); x.email=c.getString(c.getColumnIndexOrThrow("email")); x.phone=c.getString(c.getColumnIndexOrThrow("phone"));
        x.status=c.getString(c.getColumnIndexOrThrow("status")); x.rejectionReason=c.getString(c.getColumnIndexOrThrow("rejectionReason"));
        x.reviewedBy=c.getString(c.getColumnIndexOrThrow("reviewedBy")); x.reviewedAt=c.getString(c.getColumnIndexOrThrow("reviewedAt")); x.createdAt=c.getString(c.getColumnIndexOrThrow("createdAt")); return x;
    }
}