package com.example.libraryapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Notification;
import java.util.ArrayList;
import java.util.List;

public class NotificationDao {
    private final LibraryDbHelper helper;
    public NotificationDao(Context context){helper=new LibraryDbHelper(context.getApplicationContext());}
    public long insert(Notification x){return helper.getWritableDatabase().insert(LibraryDbHelper.TABLE_NOTIFICATIONS,null,values(x));}
    public int update(Notification x){if(x==null||x.getId()<=0)return 0;return helper.getWritableDatabase().update(LibraryDbHelper.TABLE_NOTIFICATIONS,values(x),"id=?",new String[]{String.valueOf(x.getId())});}
    public int delete(int id){return helper.getWritableDatabase().delete(LibraryDbHelper.TABLE_NOTIFICATIONS,"id=?",new String[]{String.valueOf(id)});}
    public Notification getById(int id){try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_NOTIFICATIONS,null,"id=?",new String[]{String.valueOf(id)},null,null,null)){return c.moveToFirst()?read(c):null;}}
    public List<Notification> getByCustomerId(int id){List<Notification> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_NOTIFICATIONS,null,"customer_id=?",new String[]{String.valueOf(id)},null,null,"created_at DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    public List<Notification> getByAdminId(int id){List<Notification> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_NOTIFICATIONS,null,"admin_id=?",new String[]{String.valueOf(id)},null,null,"created_at DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    public int markRead(int id){ContentValues v=new ContentValues();v.put("is_read",1);return helper.getWritableDatabase().update(LibraryDbHelper.TABLE_NOTIFICATIONS,v,"id=?",new String[]{String.valueOf(id)});}
    public List<Notification> getAll(){List<Notification> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_NOTIFICATIONS,null,null,null,null,null,"created_at DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    private ContentValues values(Notification x){ContentValues v=new ContentValues();if(x.getCustomerId()>0)v.put("customer_id",x.getCustomerId());else v.putNull("customer_id");if(x.getAdminId()>0)v.put("admin_id",x.getAdminId());else v.putNull("admin_id");v.put("title",x.getTitle());v.put("content",x.getContent());v.put("is_read",x.getIsRead()?1:0);v.put("created_at",x.getCreatedAt());return v;}
    private Notification read(Cursor c){return new Notification(c.getInt(c.getColumnIndexOrThrow("id")),c.isNull(c.getColumnIndexOrThrow("customer_id"))?0:c.getInt(c.getColumnIndexOrThrow("customer_id")),c.isNull(c.getColumnIndexOrThrow("admin_id"))?0:c.getInt(c.getColumnIndexOrThrow("admin_id")),c.getString(c.getColumnIndexOrThrow("title")),c.getString(c.getColumnIndexOrThrow("content")),c.getInt(c.getColumnIndexOrThrow("is_read"))==1,c.getString(c.getColumnIndexOrThrow("created_at")));}
    public void close(){helper.close();}
}
