package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Local SQLite database for Cafe Booking Online App.
 * Firebase Authentication will handle passwords; this database stores
 * only the local user profile and role.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "cafe_booking.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_USERS = "Users";
    public static final String TABLE_CATEGORIES = "Categories";
    public static final String TABLE_MENU = "MenuItems";
    public static final String TABLE_BILLS = "Bills";
    public static final String TABLE_BILL_DETAILS = "BillDetails";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_USERS + " (" +
                "UserId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "Email TEXT NOT NULL UNIQUE, " +
                "Role TEXT NOT NULL CHECK (Role IN ('Customer', 'Admin'))" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_CATEGORIES + " (" +
                "CategoryId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "CategoryName TEXT NOT NULL UNIQUE" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_MENU + " (" +
                "MenuId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "MenuName TEXT NOT NULL, " +
                "Image TEXT, " +
                "CategoryId INTEGER NOT NULL, " +
                "Price REAL NOT NULL CHECK (Price >= 0), " +
                "StockQuantity INTEGER NOT NULL DEFAULT 0 CHECK (StockQuantity >= 0), " +
                "FOREIGN KEY (CategoryId) REFERENCES " + TABLE_CATEGORIES + "(CategoryId) " +
                "ON UPDATE CASCADE ON DELETE RESTRICT" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_BILLS + " (" +
                "BillId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "UserId INTEGER NOT NULL, " +
                "DateOfBill TEXT NOT NULL, " +
                "Status TEXT NOT NULL DEFAULT 'awaiting confirmation', " +
                "TotalAmount REAL NOT NULL DEFAULT 0 CHECK (TotalAmount >= 0), " +
                "FOREIGN KEY (UserId) REFERENCES " + TABLE_USERS + "(UserId) " +
                "ON UPDATE CASCADE ON DELETE RESTRICT" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_BILL_DETAILS + " (" +
                "BillDetailId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "MenuId INTEGER NOT NULL, " +
                "BillId INTEGER NOT NULL, " +
                "Quantity INTEGER NOT NULL CHECK (Quantity > 0), " +
                "Price REAL NOT NULL CHECK (Price >= 0), " +
                "FOREIGN KEY (MenuId) REFERENCES " + TABLE_MENU + "(MenuId) " +
                "ON UPDATE CASCADE ON DELETE RESTRICT, " +
                "FOREIGN KEY (BillId) REFERENCES " + TABLE_BILLS + "(BillId) " +
                "ON UPDATE CASCADE ON DELETE CASCADE" +
                ")");

        insertSampleData(db);
    }

    private void insertSampleData(SQLiteDatabase db) {
        // Sample profiles for database testing only. They are not Firebase accounts.
        insertUser(db, "customer1@example.com", "Customer");
        insertUser(db, "customer2@example.com", "Customer");
        insertUser(db, "admin@example.com", "Admin");

        insertCategory(db, "Coffee");
        insertCategory(db, "Tea");
        insertCategory(db, "Pastries");

        insertMenu(db, "Americano", "americano", 1, 35000, 20);
        insertMenu(db, "Milk Tea", "milk_tea", 2, 40000, 15);
        insertMenu(db, "Croissant", "croissant", 3, 28000, 10);

        insertBill(db, 1, "2026-10-12", "awaiting confirmation", 98000);
        insertBill(db, 2, "2026-10-12", "confirmed", 40000);
        insertBill(db, 1, "2026-10-11", "completed", 70000);

        insertBillDetail(db, 1, 1, 1, 35000);
        insertBillDetail(db, 3, 1, 1, 28000);
        insertBillDetail(db, 2, 2, 1, 40000);
    }

    private void insertUser(SQLiteDatabase db, String email, String role) {
        ContentValues values = new ContentValues();
        values.put("Email", email);
        values.put("Role", role);
        db.insertOrThrow(TABLE_USERS, null, values);
    }

    private void insertCategory(SQLiteDatabase db, String categoryName) {
        ContentValues values = new ContentValues();
        values.put("CategoryName", categoryName);
        db.insertOrThrow(TABLE_CATEGORIES, null, values);
    }

    private void insertMenu(SQLiteDatabase db, String name, String image,
                            int categoryId, double price, int stockQuantity) {
        ContentValues values = new ContentValues();
        values.put("MenuName", name);
        values.put("Image", image);
        values.put("CategoryId", categoryId);
        values.put("Price", price);
        values.put("StockQuantity", stockQuantity);
        db.insertOrThrow(TABLE_MENU, null, values);
    }

    private void insertBill(SQLiteDatabase db, int userId, String date,
                            String status, double totalAmount) {
        ContentValues values = new ContentValues();
        values.put("UserId", userId);
        values.put("DateOfBill", date);
        values.put("Status", status);
        values.put("TotalAmount", totalAmount);
        db.insertOrThrow(TABLE_BILLS, null, values);
    }

    private void insertBillDetail(SQLiteDatabase db, int menuId, int billId,
                                  int quantity, double price) {
        ContentValues values = new ContentValues();
        values.put("MenuId", menuId);
        values.put("BillId", billId);
        values.put("Quantity", quantity);
        values.put("Price", price);
        db.insertOrThrow(TABLE_BILL_DETAILS, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Add migration steps here when the schema version changes.
        // Avoid dropping tables so existing user data is not deleted.
    }
}
