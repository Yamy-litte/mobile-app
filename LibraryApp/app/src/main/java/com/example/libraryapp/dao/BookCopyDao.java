package com.example.libraryapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.BookCopy;
import java.util.ArrayList;
import java.util.List;

public class BookCopyDao {
    private final LibraryDbHelper helper;
    public BookCopyDao(Context context) { helper = new LibraryDbHelper(context.getApplicationContext()); }
    public long insert(BookCopy x) { return helper.getWritableDatabase().insert(LibraryDbHelper.TABLE_BOOK_COPIES, null, values(x)); }
    public int update(BookCopy x) { if (x == null || x.getId() <= 0) return 0; return helper.getWritableDatabase().update(LibraryDbHelper.TABLE_BOOK_COPIES, values(x), "id=?", new String[]{String.valueOf(x.getId())}); }
    public int delete(int id) { return helper.getWritableDatabase().delete(LibraryDbHelper.TABLE_BOOK_COPIES, "id=?", new String[]{String.valueOf(id)}); }
    public BookCopy getById(int id) { try (Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_BOOK_COPIES,null,"id=?",new String[]{String.valueOf(id)},null,null,null)) { return c.moveToFirst()?read(c):null; } }
    public BookCopy getByBarcode(String barcode) { try (Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_BOOK_COPIES,null,"barcode=?",new String[]{barcode},null,null,null)) { return c.moveToFirst()?read(c):null; } }
    public List<BookCopy> getByBookId(int bookId) { List<BookCopy> out=new ArrayList<>(); try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_BOOK_COPIES,null,"book_id=?",new String[]{String.valueOf(bookId)},null,null,"barcode ASC")){while(c.moveToNext())out.add(read(c));} return out; }
    public List<BookCopy> getAll() { List<BookCopy> out=new ArrayList<>(); try(Cursor c=helper.getReadableDatabase().query(LibraryDbHelper.TABLE_BOOK_COPIES,null,null,null,null,null,"barcode ASC")){while(c.moveToNext())out.add(read(c));} return out; }
    private ContentValues values(BookCopy x){ContentValues v=new ContentValues();v.put("book_id",x.getBookId());v.put("barcode",x.getBarcode());v.put("status",x.getStatus()==null?"available":x.getStatus());return v;}
    private BookCopy read(Cursor c){return new BookCopy(c.getInt(c.getColumnIndexOrThrow("id")),c.getInt(c.getColumnIndexOrThrow("book_id")),c.getString(c.getColumnIndexOrThrow("barcode")),c.getString(c.getColumnIndexOrThrow("status")));}
    public void close(){helper.close();}
}
