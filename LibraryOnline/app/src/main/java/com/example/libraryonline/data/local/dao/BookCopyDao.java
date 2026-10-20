package com.example.libraryonline.data.local.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.model.BookCopy;
import java.util.ArrayList;
import java.util.List;

public class BookCopyDao {
    private final SQLiteDatabase db;
    public BookCopyDao(SQLiteDatabase db) { this.db = db; }

    public long insert(BookCopy x) { return db.insertOrThrow("BookCopies", null, values(x)); }
    public int update(BookCopy x) { return db.update("BookCopies", values(x), "copyId=?", new String[]{x.copyId}); }
    public BookCopy findById(String id) {
        try (Cursor c = db.query("BookCopies", null, "copyId=?", new String[]{id}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }
    public List<BookCopy> findByTitle(String titleId) {
        List<BookCopy> out = new ArrayList<>();
        try (Cursor c = db.query("BookCopies", null, "bookTitleId=? AND isHidden=0", new String[]{titleId}, null, null, "barcode ASC")) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }
    public List<BookCopy> findAvailableByTitle(String titleId) {
        List<BookCopy> out = new ArrayList<>();
        try (Cursor c = db.query("BookCopies", null, "bookTitleId=? AND copyStatus='SanSang' AND isHidden=0", new String[]{titleId}, null, null, "barcode ASC")) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }
    private static ContentValues values(BookCopy x) {
        ContentValues v = new ContentValues();
        v.put("copyId", x.copyId); v.put("bookTitleId", x.bookTitleId); v.put("barcode", x.barcode);
        v.put("shelfLocation", x.shelfLocation); v.put("copyStatus", x.copyStatus == null ? "SanSang" : x.copyStatus);
        v.put("priorityUserId", x.priorityUserId); v.put("isHidden", x.isHidden ? 1 : 0);
        v.put("createdAt", x.createdAt); v.put("updatedAt", x.updatedAt); return v;
    }
    private static BookCopy read(Cursor c) {
        BookCopy x = new BookCopy();
        x.copyId=c.getString(c.getColumnIndexOrThrow("copyId")); x.bookTitleId=c.getString(c.getColumnIndexOrThrow("bookTitleId"));
        x.barcode=c.getString(c.getColumnIndexOrThrow("barcode")); x.shelfLocation=c.getString(c.getColumnIndexOrThrow("shelfLocation"));
        x.copyStatus=c.getString(c.getColumnIndexOrThrow("copyStatus")); x.priorityUserId=c.getString(c.getColumnIndexOrThrow("priorityUserId"));
        x.isHidden=c.getInt(c.getColumnIndexOrThrow("isHidden"))==1; x.createdAt=c.getString(c.getColumnIndexOrThrow("createdAt"));
        x.updatedAt=c.getString(c.getColumnIndexOrThrow("updatedAt")); return x;
    }
}