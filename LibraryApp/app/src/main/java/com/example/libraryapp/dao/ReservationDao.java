package com.example.libraryapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Reservation;
import java.util.ArrayList;
import java.util.List;

public class ReservationDao {
    private final LibraryDbHelper helper;
    public ReservationDao(Context context){helper=new LibraryDbHelper(context.getApplicationContext());}
    public long insert(Reservation x){return helper.getWritableDatabase().insert(LibraryDbHelper.TABLE_RESERVATIONS,null,values(x));}
    public int update(Reservation x){if(x==null||x.getId()<=0)return 0;return helper.getWritableDatabase().update(LibraryDbHelper.TABLE_RESERVATIONS,values(x),"id=?",new String[]{String.valueOf(x.getId())});}
    public int delete(int id){return helper.getWritableDatabase().delete(LibraryDbHelper.TABLE_RESERVATIONS,"id=?",new String[]{String.valueOf(id)});}
    public Reservation getById(int id){try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_RESERVATIONS,null,"id=?",new String[]{String.valueOf(id)},null,null,null)){return c.moveToFirst()?read(c):null;}}
    public List<Reservation> getByCustomerId(int id){List<Reservation> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_RESERVATIONS,null,"customer_id=?",new String[]{String.valueOf(id)},null,null,"reservation_date DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    public List<Reservation> getByStatus(String status){List<Reservation> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_RESERVATIONS,null,"status=?",new String[]{status},null,null,"reservation_date DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    public List<Reservation> getAll(){List<Reservation> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_RESERVATIONS,null,null,null,null,null,"reservation_date DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    private ContentValues values(Reservation x){ContentValues v=new ContentValues();v.put("customer_id",x.getCustomerId());v.put("book_id",x.getBookId());if(x.getBookCopyId()>0)v.put("book_copy_id",x.getBookCopyId());else v.putNull("book_copy_id");v.put("reservation_date",x.getReservationDate());v.put("status",x.getStatus()==null?"pending":x.getStatus());v.put("expiration_date",x.getExpirationDate());return v;}
    private Reservation read(Cursor c){return new Reservation(c.getInt(c.getColumnIndexOrThrow("id")),c.getInt(c.getColumnIndexOrThrow("customer_id")),c.getInt(c.getColumnIndexOrThrow("book_id")),c.isNull(c.getColumnIndexOrThrow("book_copy_id"))?0:c.getInt(c.getColumnIndexOrThrow("book_copy_id")),c.getString(c.getColumnIndexOrThrow("reservation_date")),c.getString(c.getColumnIndexOrThrow("status")),c.getString(c.getColumnIndexOrThrow("expiration_date")));}
    public void close(){helper.close();}
}
