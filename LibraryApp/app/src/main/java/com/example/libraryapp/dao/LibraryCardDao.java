package com.example.libraryapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.LibraryCard;
import java.util.ArrayList;
import java.util.List;

public class LibraryCardDao {
    private final LibraryDbHelper helper;
    public LibraryCardDao(Context context){helper=new LibraryDbHelper(context.getApplicationContext());}
    public long insert(LibraryCard x){return helper.getWritableDatabase().insert(LibraryDbHelper.TABLE_LIBRARY_CARDS,null,values(x));}
    public int update(LibraryCard x){if(x==null||x.getId()<=0)return 0;return helper.getWritableDatabase().update(LibraryDbHelper.TABLE_LIBRARY_CARDS,values(x),"id=?",new String[]{String.valueOf(x.getId())});}
    public int delete(int id){return helper.getWritableDatabase().delete(LibraryDbHelper.TABLE_LIBRARY_CARDS,"id=?",new String[]{String.valueOf(id)});}
    public LibraryCard getById(int id){try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_LIBRARY_CARDS,null,"id=?",new String[]{String.valueOf(id)},null,null,null)){return c.moveToFirst()?read(c):null;}}
    public LibraryCard getByCustomerId(int id){try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_LIBRARY_CARDS,null,"customer_id=?",new String[]{String.valueOf(id)},null,null,null)){return c.moveToFirst()?read(c):null;}}
    public LibraryCard getByCardNumber(String number){try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_LIBRARY_CARDS,null,"card_number=?",new String[]{number},null,null,null)){return c.moveToFirst()?read(c):null;}}
    public List<LibraryCard> getAll(){List<LibraryCard> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_LIBRARY_CARDS,null,null,null,null,null,"issue_date DESC")){while(c.moveToNext())out.add(read(c));}return out;}
    private ContentValues values(LibraryCard x){ContentValues v=new ContentValues();v.put("customer_id",x.getCustomerId());v.put("card_number",x.getCardNumber());v.put("status",x.getStatus()==null?"active":x.getStatus());v.put("issue_date",x.getIssueDate());return v;}
    private LibraryCard read(Cursor c){return new LibraryCard(c.getInt(c.getColumnIndexOrThrow("id")),c.getInt(c.getColumnIndexOrThrow("customer_id")),c.getString(c.getColumnIndexOrThrow("card_number")),c.getString(c.getColumnIndexOrThrow("status")),c.getString(c.getColumnIndexOrThrow("issue_date")));}
    public void close(){helper.close();}
}
