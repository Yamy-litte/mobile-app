package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.database.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Review;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReviewDao {
    public static final String TABLE_REVIEWS = "Reviews";
    private final DatabaseHelper helper;

    public ReviewDao(Context context) {
        helper = new DatabaseHelper(context.getApplicationContext());
    }

    public Review getForBill(int billId, int userId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT ReviewId, BillId, Rating, Comment, CreatedAt " +
                "FROM Reviews WHERE BillId = ? AND UserId = ? LIMIT 1",
                new String[]{String.valueOf(billId), String.valueOf(userId)})) {
            if (!c.moveToFirst()) return null;
            return new Review(c.getInt(0), c.getInt(1), "", c.getInt(2),
                    c.getString(3), c.getString(4), 0, "");
        }
    }

    public boolean saveForBill(int billId, int userId, int rating, String comment) {
        if (billId <= 0 || userId <= 0 || rating < 1 || rating > 5
                || comment == null || comment.trim().isEmpty()
                || comment.trim().length() > 1000) return false;

        SQLiteDatabase db = helper.getWritableDatabase();
        boolean started = false;
        try {
            db.beginTransaction();
            started = true;
            try (Cursor c = db.query(DatabaseHelper.TABLE_BILLS,
                    new String[]{"Status"}, "BillId = ? AND UserId = ?",
                    new String[]{String.valueOf(billId), String.valueOf(userId)},
                    null, null, null, "1")) {
                if (!c.moveToFirst()) return false;
                String status = c.getString(0);
                if ("cancelled".equalsIgnoreCase(status)) return false;
            }

            ContentValues values = new ContentValues();
            values.put("UserId", userId);
            values.put("BillId", billId);
            values.put("Rating", rating);
            values.put("Comment", comment.trim());
            values.put("CreatedAt", new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            int changed = db.update(TABLE_REVIEWS, values, "BillId = ? AND UserId = ?",
                    new String[]{String.valueOf(billId), String.valueOf(userId)});
            if (changed == 0) db.insertOrThrow(TABLE_REVIEWS, null, values);
            db.setTransactionSuccessful();
            return true;
        } catch (SQLiteException ex) {
            return false;
        } finally {
            if (started) db.endTransaction();
        }
    }

    public List<Review> getAllReviews() {
        List<Review> reviews = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT r.ReviewId, r.BillId, u.Email, r.Rating, r.Comment, " +
                "r.CreatedAt, b.TotalAmount, " +
                "COALESCE((SELECT group_concat(m.MenuName || ' x' || d.Quantity, ', ') " +
                "FROM BillDetails d INNER JOIN MenuItems m ON m.MenuId = d.MenuId " +
                "WHERE d.BillId = b.BillId), 'Order') AS MenuSummary " +
                "FROM Reviews r INNER JOIN Users u ON u.UserId = r.UserId " +
                "INNER JOIN Bills b ON b.BillId = r.BillId " +
                "WHERE lower(b.Status) != 'cancelled' " +
                "ORDER BY r.CreatedAt DESC, r.ReviewId DESC";
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                reviews.add(new Review(c.getInt(0), c.getInt(1), c.getString(2),
                        c.getInt(3), c.getString(4), c.getString(5),
                        c.getDouble(6), c.getString(7)));
            }
        }
        return reviews;
    }

    public void close() { helper.close(); }
}
