package com.example.libraryapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.BorrowRecord;
import java.util.ArrayList;
import java.util.List;

public class BorrowRecordDao {
    private final LibraryDbHelper helper;
    public BorrowRecordDao(Context context){helper=new LibraryDbHelper(context.getApplicationContext());}
    public long insert(BorrowRecord x){return helper.getWritableDatabase().insert(LibraryDbHelper.TABLE_BORROW_RECORDS,null,values(x));}
    public int update(BorrowRecord x){if(x==null||x.getId()<=0)return 0;return helper.getWritableDatabase().update(LibraryDbHelper.TABLE_BORROW_RECORDS,values(x),"id=?",new String[]{String.valueOf(x.getId())});}
    public int delete(int id){return helper.getWritableDatabase().delete(LibraryDbHelper.TABLE_BORROW_RECORDS,"id=?",new String[]{String.valueOf(id)});}
    public BorrowRecord getById(int id){try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_BORROW_RECORDS,null,"id=?",new String[]{String.valueOf(id)},null,null,null)){return c.moveToFirst()?read(c):null;}}
    public List<BorrowRecord> getByCustomerId(int id){List<BorrowRecord> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_BORROW_RECORDS,null,"customer_id=?",new String[]{String.valueOf(id)},null,null,"borrow_date DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    public List<BorrowRecord> getByStatus(String status){List<BorrowRecord> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_BORROW_RECORDS,null,"status=?",new String[]{status},null,null,"borrow_date DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    public List<BorrowRecord> getAll(){List<BorrowRecord> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_BORROW_RECORDS,null,null,null,null,null,"borrow_date DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    private ContentValues values(BorrowRecord x){ContentValues v=new ContentValues();v.put("customer_id",x.getCustomerId());v.put("book_copy_id",x.getBookCopyId());if(x.getAdminBorrowId()>0)v.put("admin_borrow_id",x.getAdminBorrowId());else v.putNull("admin_borrow_id");if(x.getAdminReturnId()>0)v.put("admin_return_id",x.getAdminReturnId());else v.putNull("admin_return_id");v.put("borrow_date",x.getBorrowDate());v.put("due_date",x.getDueDate());v.put("return_date",x.getReturnDate());v.put("physical_condition_on_return",x.getPhysicalConditionOnReturn());v.put("status",x.getStatus()==null?"borrowed":x.getStatus());return v;}
    private BorrowRecord read(Cursor c){return new BorrowRecord(c.getInt(c.getColumnIndexOrThrow("id")),c.getInt(c.getColumnIndexOrThrow("customer_id")),c.getInt(c.getColumnIndexOrThrow("book_copy_id")),c.isNull(c.getColumnIndexOrThrow("admin_borrow_id"))?0:c.getInt(c.getColumnIndexOrThrow("admin_borrow_id")),c.isNull(c.getColumnIndexOrThrow("admin_return_id"))?0:c.getInt(c.getColumnIndexOrThrow("admin_return_id")),c.getString(c.getColumnIndexOrThrow("borrow_date")),c.getString(c.getColumnIndexOrThrow("due_date")),c.getString(c.getColumnIndexOrThrow("return_date")),c.getString(c.getColumnIndexOrThrow("physical_condition_on_return")),c.getString(c.getColumnIndexOrThrow("status")));}
    public void close(){helper.close();}
}
