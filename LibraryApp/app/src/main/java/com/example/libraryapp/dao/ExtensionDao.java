package com.example.libraryapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Extension;
import java.util.ArrayList;
import java.util.List;

public class ExtensionDao {
    private final LibraryDbHelper helper;
    public ExtensionDao(Context context){helper=new LibraryDbHelper(context.getApplicationContext());}
    public long insert(Extension x){return helper.getWritableDatabase().insert(LibraryDbHelper.TABLE_EXTENSIONS,null,values(x));}
    public int update(Extension x){if(x==null||x.getId()<=0)return 0;return helper.getWritableDatabase().update(LibraryDbHelper.TABLE_EXTENSIONS,values(x),"id=?",new String[]{String.valueOf(x.getId())});}
    public int delete(int id){return helper.getWritableDatabase().delete(LibraryDbHelper.TABLE_EXTENSIONS,"id=?",new String[]{String.valueOf(id)});}
    public Extension getById(int id){try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_EXTENSIONS,null,"id=?",new String[]{String.valueOf(id)},null,null,null)){return c.moveToFirst()?read(c):null;}}
    public List<Extension> getByBorrowRecordId(int id){List<Extension> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_EXTENSIONS,null,"borrow_record_id=?",new String[]{String.valueOf(id)},null,null,"request_date DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    public List<Extension> getAll(){List<Extension> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_EXTENSIONS,null,null,null,null,null,"request_date DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    private ContentValues values(Extension x){ContentValues v=new ContentValues();v.put("borrow_record_id",x.getBorrowRecordId());v.put("request_date",x.getRequestDate());v.put("extended_days",x.getExtendedDays());v.put("status",x.getStatus()==null?"pending":x.getStatus());v.put("notes",x.getNotes());return v;}
    private Extension read(Cursor c){return new Extension(c.getInt(c.getColumnIndexOrThrow("id")),c.getInt(c.getColumnIndexOrThrow("borrow_record_id")),c.getString(c.getColumnIndexOrThrow("request_date")),c.getInt(c.getColumnIndexOrThrow("extended_days")),c.getString(c.getColumnIndexOrThrow("status")),c.getString(c.getColumnIndexOrThrow("notes")));}
    public void close(){helper.close();}
}
