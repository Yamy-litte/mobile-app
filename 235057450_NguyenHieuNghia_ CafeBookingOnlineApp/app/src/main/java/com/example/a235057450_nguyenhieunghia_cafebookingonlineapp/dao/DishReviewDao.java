package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.database.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.DishReview;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DishReviewDao {
    private final DatabaseHelper helper;

    public DishReviewDao(Context context) {
        helper = new DatabaseHelper(context.getApplicationContext());
    }

    public DishReview getForMenu(int menuId, int userId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT DishReviewId, UserId, MenuId, Rating, Comment, CreatedAt " +
                "FROM DishReviews WHERE MenuId = ? AND UserId = ? LIMIT 1",
                new String[]{String.valueOf(menuId), String.valueOf(userId)})) {
            if (!c.moveToFirst()) return null;
            return new DishReview(c.getInt(0), c.getInt(1), c.getInt(2), "",
                    c.getInt(3), c.getString(4), c.getString(5));
        }
    }

    public List<DishReview> getRecentForMenu(int menuId) {
        List<DishReview> reviews = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT r.DishReviewId, r.UserId, r.MenuId, u.Email, r.Rating, " +
                "r.Comment, r.CreatedAt FROM DishReviews r " +
                "INNER JOIN Users u ON u.UserId = r.UserId " +
                "WHERE r.MenuId = ? ORDER BY r.CreatedAt DESC, r.DishReviewId DESC LIMIT 3";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(menuId)})) {
            while (c.moveToNext()) {
                reviews.add(new DishReview(c.getInt(0), c.getInt(1), c.getInt(2),
                        c.getString(3), c.getInt(4), c.getString(5), c.getString(6)));
            }
        }
        return reviews;
    }

    public boolean saveForMenu(int menuId, int userId, int rating, String comment) {
        if (menuId <= 0 || userId <= 0 || rating < 1 || rating > 5
                || comment == null || comment.trim().isEmpty()
                || comment.trim().length() > 1000) return false;
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            try (Cursor c = db.query(DatabaseHelper.TABLE_USERS, new String[]{"Role"},
                    "UserId = ?", new String[]{String.valueOf(userId)},
                    null, null, null, "1")) {
                if (!c.moveToFirst() || !"Customer".equals(c.getString(0))) return false;
            }
            ContentValues values = new ContentValues();
            values.put("UserId", userId);
            values.put("MenuId", menuId);
            values.put("Rating", rating);
            values.put("Comment", comment.trim());
            values.put("CreatedAt", new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            int updated = db.update("DishReviews", values, "MenuId = ? AND UserId = ?",
                    new String[]{String.valueOf(menuId), String.valueOf(userId)});
            if (updated == 0) db.insertOrThrow("DishReviews", null, values);
            return true;
        } catch (SQLiteException ex) {
            return false;
        }
    }

    public void close() { helper.close(); }
}
