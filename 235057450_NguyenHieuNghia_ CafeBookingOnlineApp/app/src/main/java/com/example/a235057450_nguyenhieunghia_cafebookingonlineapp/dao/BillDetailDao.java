package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.database.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.BillDetail;

import java.util.ArrayList;
import java.util.List;

/** Reads line items belonging to a bill. */
public class BillDetailDao {
    private final DatabaseHelper databaseHelper;

    public BillDetailDao(Context context) {
        databaseHelper = new DatabaseHelper(context.getApplicationContext());
    }

    /** Only returns details for the bill owned by the supplied user. */
    public List<BillDetail> getDetailsForCustomerBill(int billId, int userId) {
        List<BillDetail> details = new ArrayList<>();
        SQLiteDatabase db = databaseHelper.getReadableDatabase();

        String sql = "SELECT d.BillDetailId, d.MenuId, d.BillId, d.Quantity, d.Price " +
                "FROM " + DatabaseHelper.TABLE_BILL_DETAILS + " d " +
                "INNER JOIN " + DatabaseHelper.TABLE_BILLS + " b ON b.BillId = d.BillId " +
                "WHERE d.BillId = ? AND b.UserId = ? " +
                "ORDER BY d.BillDetailId ASC";

        try (Cursor cursor = db.rawQuery(
                sql,
                new String[]{String.valueOf(billId), String.valueOf(userId)})) {
            while (cursor.moveToNext()) {
                details.add(new BillDetail(
                        cursor.getInt(cursor.getColumnIndexOrThrow("BillDetailId")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("MenuId")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("BillId")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("Quantity")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("Price"))
                ));
            }
        }
        return details;
    }

    public void close() {
        databaseHelper.close();
    }
}
