package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.database.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Bill;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.BillDetail;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Creates orders atomically and retrieves a customer's bill history.
 * checkout() returns the new BillId, or -1 when validation/database work fails.
 */
public class BillDao {
    public static final String STATUS_AWAITING_CONFIRMATION = "awaiting confirmation";

    private final DatabaseHelper databaseHelper;

    public BillDao(Context context) {
        databaseHelper = new DatabaseHelper(context.getApplicationContext());
    }

    /**
     * Creates a bill and its details in one transaction.
     * Stock is checked and reduced inside the same transaction to avoid overselling.
     * Each BillDetail.price is set from the current database menu price.
     */
    public long checkout(int userId, List<BillDetail> items) {
        if (userId <= 0 || items == null || items.isEmpty()) {
            return -1;
        }

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        long billId = -1;
        boolean transactionStarted = false;

        try {
            db.beginTransaction();
            transactionStarted = true;

            // Validate that the customer exists.
            try (Cursor userCursor = db.query(
                    DatabaseHelper.TABLE_USERS,
                    new String[]{"UserId"},
                    "UserId = ? AND Role = ?",
                    new String[]{String.valueOf(userId), "Customer"},
                    null, null, null, "1")) {
                if (!userCursor.moveToFirst()) {
                    return -1;
                }
            }

            double totalAmount = 0;
            for (BillDetail item : items) {
                if (item == null || item.getMenuId() <= 0 || item.getQuantity() <= 0) {
                    return -1;
                }

                try (Cursor menuCursor = db.query(
                        DatabaseHelper.TABLE_MENU,
                        new String[]{"Price", "StockQuantity"},
                        "MenuId = ?",
                        new String[]{String.valueOf(item.getMenuId())},
                        null, null, null, "1")) {
                    if (!menuCursor.moveToFirst()) {
                        return -1;
                    }

                    int stock = menuCursor.getInt(menuCursor.getColumnIndexOrThrow("StockQuantity"));
                    double currentPrice = menuCursor.getDouble(menuCursor.getColumnIndexOrThrow("Price"));
                    if (stock < item.getQuantity() || currentPrice < 0) {
                        return -1;
                    }
                    totalAmount += currentPrice * item.getQuantity();
                }
            }

            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());
            ContentValues billValues = new ContentValues();
            billValues.put("UserId", userId);
            billValues.put("DateOfBill", date);
            billValues.put("Status", STATUS_AWAITING_CONFIRMATION);
            billValues.put("TotalAmount", totalAmount);
            billId = db.insertOrThrow(DatabaseHelper.TABLE_BILLS, null, billValues);

            for (BillDetail item : items) {
                double currentPrice;
                int currentStock;

                try (Cursor menuCursor = db.query(
                        DatabaseHelper.TABLE_MENU,
                        new String[]{"Price", "StockQuantity"},
                        "MenuId = ?",
                        new String[]{String.valueOf(item.getMenuId())},
                        null, null, null, "1")) {
                    if (!menuCursor.moveToFirst()) {
                        throw new SQLiteException("Menu item no longer exists.");
                    }
                    currentPrice = menuCursor.getDouble(menuCursor.getColumnIndexOrThrow("Price"));
                    currentStock = menuCursor.getInt(menuCursor.getColumnIndexOrThrow("StockQuantity"));
                }

                if (currentStock < item.getQuantity()) {
                    throw new SQLiteException("Insufficient stock.");
                }

                ContentValues detailValues = new ContentValues();
                detailValues.put("MenuId", item.getMenuId());
                detailValues.put("BillId", billId);
                detailValues.put("Quantity", item.getQuantity());
                detailValues.put("Price", currentPrice);
                db.insertOrThrow(DatabaseHelper.TABLE_BILL_DETAILS, null, detailValues);

                ContentValues stockValues = new ContentValues();
                stockValues.put("StockQuantity", currentStock - item.getQuantity());
                int updated = db.update(
                        DatabaseHelper.TABLE_MENU,
                        stockValues,
                        "MenuId = ? AND StockQuantity >= ?",
                        new String[]{String.valueOf(item.getMenuId()),
                                String.valueOf(item.getQuantity())});
                if (updated != 1) {
                    throw new SQLiteException("Stock changed during checkout.");
                }
            }

            db.setTransactionSuccessful();
            return billId;
        } catch (SQLiteException | IllegalArgumentException ex) {
            return -1;
        } finally {
            if (transactionStarted) {
                db.endTransaction();
            }
        }
    }

    public Bill getBillById(int billId, int userId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_BILLS,
                new String[]{"BillId", "UserId", "DateOfBill", "Status", "TotalAmount"},
                "BillId = ? AND UserId = ?",
                new String[]{String.valueOf(billId), String.valueOf(userId)},
                null, null, null, "1")) {
            if (cursor.moveToFirst()) {
                return readBill(cursor);
            }
        }
        return null;
    }

    public List<Bill> getBillsByUserId(int userId) {
        java.util.List<Bill> bills = new java.util.ArrayList<>();
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                DatabaseHelper.TABLE_BILLS,
                new String[]{"BillId", "UserId", "DateOfBill", "Status", "TotalAmount"},
                "UserId = ?",
                new String[]{String.valueOf(userId)},
                null, null, "DateOfBill DESC, BillId DESC")) {
            while (cursor.moveToNext()) {
                bills.add(readBill(cursor));
            }
        }
        return bills;
    }

    private Bill readBill(Cursor cursor) {
        return new Bill(
                cursor.getInt(cursor.getColumnIndexOrThrow("BillId")),
                cursor.getInt(cursor.getColumnIndexOrThrow("UserId")),
                cursor.getString(cursor.getColumnIndexOrThrow("DateOfBill")),
                cursor.getString(cursor.getColumnIndexOrThrow("Status")),
                cursor.getDouble(cursor.getColumnIndexOrThrow("TotalAmount"))
        );
    }

    public void close() {
        databaseHelper.close();
    }
}
