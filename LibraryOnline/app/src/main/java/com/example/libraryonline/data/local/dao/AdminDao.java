package com.example.libraryonline.data.local.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.model.Admin;

public class AdminDao {
    private final SQLiteDatabase db;
    public AdminDao(SQLiteDatabase db) { this.db=db; }
    public long insert(Admin x) { return db.insertOrThrow("Admins",null,values(x)); }
    public int update(Admin x) { return db.update("Admins",values(x),"adminId=?",new String[]{x.adminId}); }
    public Admin findById(String id) {
        try(Cursor c=db.query("Admins",null,"adminId=?",new String[]{id},null,null,null)) { return c.moveToFirst()?read(c):null; }
    }
    public Admin findByUserId(String userId) {
        try(Cursor c=db.query("Admins",null,"userId=?",new String[]{userId},null,null,null)) { return c.moveToFirst()?read(c):null; }
    }
    private static ContentValues values(Admin x) {
        ContentValues v=new ContentValues(); v.put("adminId",x.adminId); v.put("userId",x.userId); v.put("employeeCode",x.employeeCode);
        v.put("adminStatus",x.adminStatus==null?"Active":x.adminStatus); v.put("createdAt",x.createdAt); v.put("updatedAt",x.updatedAt); return v;
    }
    private static Admin read(Cursor c) {
        Admin x=new Admin(); x.adminId=c.getString(c.getColumnIndexOrThrow("adminId")); x.userId=c.getString(c.getColumnIndexOrThrow("userId"));
        x.employeeCode=c.getString(c.getColumnIndexOrThrow("employeeCode")); x.adminStatus=c.getString(c.getColumnIndexOrThrow("adminStatus"));
        x.createdAt=c.getString(c.getColumnIndexOrThrow("createdAt")); x.updatedAt=c.getString(c.getColumnIndexOrThrow("updatedAt")); return x;
    }
}