package com.example.libraryapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Fine;
import java.util.ArrayList;
import java.util.List;

public class FineDao {
    private final LibraryDbHelper helper;
    public FineDao(Context context){helper=new LibraryDbHelper(context.getApplicationContext());}
    public long insert(Fine x){return helper.getWritableDatabase().insert(LibraryDbHelper.TABLE_FINES,null,values(x));}
    public int update(Fine x){if(x==null||x.getId()<=0)return 0;return helper.getWritableDatabase().update(LibraryDbHelper.TABLE_FINES,values(x),"id=?",new String[]{String.valueOf(x.getId())});}
    public int delete(int id){return helper.getWritableDatabase().delete(LibraryDbHelper.TABLE_FINES,"id=?",new String[]{String.valueOf(id)});}
    public Fine getById(int id){try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_FINES,null,"id=?",new String[]{String.valueOf(id)},null,null,null)){return c.moveToFirst()?read(c):null;}}
    public List<Fine> getByBorrowRecordId(int id){List<Fine> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_FINES,null,"borrow_record_id=?",new String[]{String.valueOf(id)},null,null,"created_at DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    public List<Fine> getByStatus(String status){List<Fine> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_FINES,null,"status=?",new String[]{status},null,null,"created_at DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    public List<Fine> getAll(){List<Fine> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_FINES,null,null,null,null,null,"created_at DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    private ContentValues values(Fine x){ContentValues v=new ContentValues();v.put("borrow_record_id",x.getBorrowRecordId());v.put("fine_amount",x.getFineAmount());v.put("fine_reason",x.getFineReason());v.put("status",x.getStatus()==null?"unpaid":x.getStatus());v.put("created_at",x.getCreatedAt());v.put("paid_at",x.getPaidAt());return v;}
    private Fine read(Cursor c){return new Fine(c.getInt(c.getColumnIndexOrThrow("id")),c.getInt(c.getColumnIndexOrThrow("borrow_record_id")),c.getDouble(c.getColumnIndexOrThrow("fine_amount")),c.getString(c.getColumnIndexOrThrow("fine_reason")),c.getString(c.getColumnIndexOrThrow("status")),c.getString(c.getColumnIndexOrThrow("created_at")),c.getString(c.getColumnIndexOrThrow("paid_at")));}
    public void close(){helper.close();}
}
