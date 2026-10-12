package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.BillDetail;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.database.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

public class BillDetailDAO {
    private final DatabaseHelper helper;

    public BillDetailDAO(Context context) {
        helper = new DatabaseHelper(context.getApplicationContext());
    }

    public long insert(BillDetail detail) {
        return helper.getWritableDatabase().insert(DatabaseHelper.TABLE_BILL_DETAIL, null,
                toValues(detail));
    }

    public BillDetail getById(long detailId) {
        try (Cursor cursor = helper.getReadableDatabase().query(DatabaseHelper.TABLE_BILL_DETAIL,
                null, "BillDetailId = ?", new String[]{String.valueOf(detailId)},
                null, null, null)) {
            if (cursor.moveToFirst()) return fromCursor(cursor);
        }
        return null;
    }

    public List<BillDetail> getByBill(long billId) {
        List<BillDetail> result = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().query(DatabaseHelper.TABLE_BILL_DETAIL,
                null, "BillId = ?", new String[]{String.valueOf(billId)},
                null, null, "BillDetailId ASC")) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public int updateQuantity(long detailId, int quantity) {
        if (quantity <= 0) return 0;
        ContentValues values = new ContentValues();
        values.put("Quantity", quantity);
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_BILL_DETAIL, values,
                "BillDetailId = ?", new String[]{String.valueOf(detailId)});
    }

    public int delete(long detailId) {
        return helper.getWritableDatabase().delete(DatabaseHelper.TABLE_BILL_DETAIL,
                "BillDetailId = ?", new String[]{String.valueOf(detailId)});
    }

    public int deleteByBill(long billId) {
        return helper.getWritableDatabase().delete(DatabaseHelper.TABLE_BILL_DETAIL,
                "BillId = ?", new String[]{String.valueOf(billId)});
    }

    private ContentValues toValues(BillDetail detail) {
        ContentValues values = new ContentValues();
        values.put("BillId", detail.getBillId());
        values.put("MenuId", detail.getMenuId());
        values.put("Quantity", detail.getQuantity());
        values.put("UnitPrice", detail.getUnitPrice());
        return values;
    }

    private BillDetail fromCursor(Cursor cursor) {
        return new BillDetail(
                cursor.getLong(cursor.getColumnIndexOrThrow("BillDetailId")),
                cursor.getLong(cursor.getColumnIndexOrThrow("BillId")),
                cursor.getLong(cursor.getColumnIndexOrThrow("MenuId")),
                cursor.getInt(cursor.getColumnIndexOrThrow("Quantity")),
                cursor.getDouble(cursor.getColumnIndexOrThrow("UnitPrice")));
    }

    public void close() {
        helper.close();
    }
}
