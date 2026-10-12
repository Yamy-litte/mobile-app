package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Bill;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

public class BillDAO {
    private final DatabaseHelper helper;

    public BillDAO(Context context) {
        helper = new DatabaseHelper(context.getApplicationContext());
    }

    public long insert(Bill bill) {
        return helper.getWritableDatabase().insert(DatabaseHelper.TABLE_BILL, null, toValues(bill));
    }

    public Bill getById(long billId) {
        try (Cursor cursor = helper.getReadableDatabase().query(DatabaseHelper.TABLE_BILL, null,
                "BillId = ?", new String[]{String.valueOf(billId)}, null, null, null)) {
            if (cursor.moveToFirst()) return fromCursor(cursor);
        }
        return null;
    }

    public List<Bill> getByUser(long userId) {
        List<Bill> result = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().query(DatabaseHelper.TABLE_BILL, null,
                "UserId = ?", new String[]{String.valueOf(userId)}, null, null,
                "DateOfBill DESC, BillId DESC")) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public List<Bill> getAll() {
        List<Bill> result = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().query(DatabaseHelper.TABLE_BILL, null,
                null, null, null, null, "DateOfBill DESC, BillId DESC")) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public int updateStatus(long billId, String status) {
        if (!isValidStatus(status)) return 0;
        ContentValues values = new ContentValues();
        values.put("Status", status);
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_BILL, values,
                "BillId = ?", new String[]{String.valueOf(billId)});
    }

    /** Customers may cancel only while the bill awaits confirmation. */
    public int cancelIfAwaitingConfirmation(long billId, long userId) {
        ContentValues values = new ContentValues();
        values.put("Status", Bill.STATUS_CANCELLED);
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_BILL, values,
                "BillId = ? AND UserId = ? AND Status = ?",
                new String[]{String.valueOf(billId), String.valueOf(userId),
                        Bill.STATUS_AWAITING_CONFIRMATION});
    }

    public int update(Bill bill) {
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_BILL, toValues(bill),
                "BillId = ?", new String[]{String.valueOf(bill.getBillId())});
    }

    public int delete(long billId) {
        return helper.getWritableDatabase().delete(DatabaseHelper.TABLE_BILL,
                "BillId = ?", new String[]{String.valueOf(billId)});
    }

    private boolean isValidStatus(String status) {
        return Bill.STATUS_AWAITING_CONFIRMATION.equals(status)
                || Bill.STATUS_IN_TRANSIT.equals(status)
                || Bill.STATUS_COMPLETED.equals(status)
                || Bill.STATUS_CANCELLED.equals(status);
    }

    private ContentValues toValues(Bill bill) {
        ContentValues values = new ContentValues();
        values.put("UserId", bill.getUserId());
        values.put("DateOfBill", bill.getDateOfBill());
        values.put("Status", bill.getStatus());
        values.put("TotalAmount", bill.getTotalAmount());
        return values;
    }

    private Bill fromCursor(Cursor cursor) {
        return new Bill(
                cursor.getLong(cursor.getColumnIndexOrThrow("BillId")),
                cursor.getLong(cursor.getColumnIndexOrThrow("UserId")),
                cursor.getString(cursor.getColumnIndexOrThrow("DateOfBill")),
                cursor.getString(cursor.getColumnIndexOrThrow("Status")),
                cursor.getDouble(cursor.getColumnIndexOrThrow("TotalAmount")));
    }

    public void close() {
        helper.close();
    }
}
