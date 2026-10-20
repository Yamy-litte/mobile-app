package com.example.libraryonline.data.local.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.model.LibraryNotification;
import java.util.ArrayList;
import java.util.List;

public class LibraryNotificationDao {
    private final SQLiteDatabase db;
    public LibraryNotificationDao(SQLiteDatabase db) { this.db=db; }
    public long insert(LibraryNotification x) { return db.insertOrThrow("Notifications",null,values(x)); }
    public int update(LibraryNotification x) { return db.update("Notifications",values(x),"notificationId=?",new String[]{x.notificationId}); }
    public List<LibraryNotification> findByUser(String userId) {
        List<LibraryNotification> out=new ArrayList<>();
        try(Cursor c=db.query("Notifications",null,"userId=?",new String[]{userId},null,null,"scheduledAt DESC")) { while(c.moveToNext()) out.add(read(c)); }
        return out;
    }
    public int markRead(String id, String readAt) {
        ContentValues v=new ContentValues(); v.put("readAt",readAt); v.put("deliveryStatus","Read");
        return db.update("Notifications",v,"notificationId=?",new String[]{id});
    }
    private static ContentValues values(LibraryNotification x) {
        ContentValues v=new ContentValues(); v.put("notificationId",x.notificationId); v.put("userId",x.userId); v.put("notificationType",x.notificationType);
        v.put("title",x.title); v.put("message",x.message); v.put("relatedEntityType",x.relatedEntityType); v.put("relatedEntityId",x.relatedEntityId);
        v.put("scheduledAt",x.scheduledAt); v.put("sentAt",x.sentAt); v.put("readAt",x.readAt);
        v.put("deliveryStatus",x.deliveryStatus==null?"Pending":x.deliveryStatus); return v;
    }
    private static LibraryNotification read(Cursor c) {
        LibraryNotification x=new LibraryNotification(); x.notificationId=c.getString(c.getColumnIndexOrThrow("notificationId"));
        x.userId=c.getString(c.getColumnIndexOrThrow("userId")); x.notificationType=c.getString(c.getColumnIndexOrThrow("notificationType"));
        x.title=c.getString(c.getColumnIndexOrThrow("title")); x.message=c.getString(c.getColumnIndexOrThrow("message"));
        x.relatedEntityType=c.getString(c.getColumnIndexOrThrow("relatedEntityType")); x.relatedEntityId=c.getString(c.getColumnIndexOrThrow("relatedEntityId"));
        x.scheduledAt=c.getString(c.getColumnIndexOrThrow("scheduledAt")); x.sentAt=c.getString(c.getColumnIndexOrThrow("sentAt"));
        x.readAt=c.getString(c.getColumnIndexOrThrow("readAt")); x.deliveryStatus=c.getString(c.getColumnIndexOrThrow("deliveryStatus")); return x;
    }
}