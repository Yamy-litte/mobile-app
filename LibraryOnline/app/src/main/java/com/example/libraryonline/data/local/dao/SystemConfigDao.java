package com.example.libraryonline.data.local.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.model.SystemConfig;

public class SystemConfigDao {
    private final SQLiteDatabase db;
    public SystemConfigDao(SQLiteDatabase db) { this.db=db; }
    public SystemConfig getCurrent() {
        try(Cursor c=db.query("SystemConfigs",null,null,null,null,null,"updatedAt DESC", "1")) { return c.moveToFirst()?read(c):null; }
    }
    public long insert(SystemConfig x) { return db.insertOrThrow("SystemConfigs",null,values(x)); }
    public int update(SystemConfig x) { return db.update("SystemConfigs",values(x),"configId=?",new String[]{x.configId}); }
    private static ContentValues values(SystemConfig x) {
        ContentValues v=new ContentValues(); v.put("configId",x.configId); v.put("maxBooksPerUser",x.maxBooksPerUser); v.put("maxLoanDays",x.maxLoanDays);
        v.put("onlineHoldHours",x.onlineHoldHours); v.put("reservationHoldHours",x.reservationHoldHours); v.put("lateFeePerDay",x.lateFeePerDay);
        v.put("lostBookMultiplier",x.lostBookMultiplier); v.put("updatedBy",x.updatedBy); v.put("updatedAt",x.updatedAt); return v;
    }
    private static SystemConfig read(Cursor c) {
        SystemConfig x=new SystemConfig(); x.configId=c.getString(c.getColumnIndexOrThrow("configId"));
        x.maxBooksPerUser=c.getInt(c.getColumnIndexOrThrow("maxBooksPerUser")); x.maxLoanDays=c.getInt(c.getColumnIndexOrThrow("maxLoanDays"));
        x.onlineHoldHours=c.getInt(c.getColumnIndexOrThrow("onlineHoldHours")); x.reservationHoldHours=c.getInt(c.getColumnIndexOrThrow("reservationHoldHours"));
        x.lateFeePerDay=c.getInt(c.getColumnIndexOrThrow("lateFeePerDay")); x.lostBookMultiplier=c.getInt(c.getColumnIndexOrThrow("lostBookMultiplier"));
        x.updatedBy=c.getString(c.getColumnIndexOrThrow("updatedBy")); x.updatedAt=c.getString(c.getColumnIndexOrThrow("updatedAt")); return x;
    }
}