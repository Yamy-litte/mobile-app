package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Bill;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Category;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.MenuItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Local SQLite store for cafe catalogue and order history.
 * Passwords are intentionally not stored here; Firebase Authentication owns credentials.
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "cafe_booking.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_USER = "User";
    public static final String TABLE_CATEGORY = "Category";
    public static final String TABLE_MENU = "Menu";
    public static final String TABLE_BILL = "Bill";
    public static final String TABLE_BILL_DETAIL = "BillDetail";

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
        db.execSQL("CREATE TABLE User (" +
                "UserId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "FirebaseUid TEXT UNIQUE, " +
                "Email TEXT NOT NULL UNIQUE, " +
                "FullName TEXT NOT NULL, " +
                "Role TEXT NOT NULL CHECK(Role IN ('Customer','Administrator')))");

        db.execSQL("CREATE TABLE Category (" +
                "CategoryId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "CategoryName TEXT NOT NULL UNIQUE, " +
                "Description TEXT)");

        db.execSQL("CREATE TABLE Menu (" +
                "MenuId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "MenuName TEXT NOT NULL, " +
                "Description TEXT, " +
                "ImageUrl TEXT, " +
                "Price REAL NOT NULL CHECK(Price >= 0), " +
                "CategoryId INTEGER NOT NULL, " +
                "IsAvailable INTEGER NOT NULL DEFAULT 1 CHECK(IsAvailable IN (0,1)), " +
                "FOREIGN KEY(CategoryId) REFERENCES Category(CategoryId))");

        db.execSQL("CREATE TABLE Bill (" +
                "BillId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "UserId INTEGER NOT NULL, " +
                "DateOfBill TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "Status TEXT NOT NULL DEFAULT 'awaiting confirmation' " +
                "CHECK(Status IN ('awaiting confirmation','in transit','completed','cancelled')), " +
                "TotalAmount REAL NOT NULL DEFAULT 0 CHECK(TotalAmount >= 0), " +
                "FOREIGN KEY(UserId) REFERENCES User(UserId))");

        db.execSQL("CREATE TABLE BillDetail (" +
                "BillDetailId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "BillId INTEGER NOT NULL, " +
                "MenuId INTEGER NOT NULL, " +
                "Quantity INTEGER NOT NULL CHECK(Quantity > 0), " +
                "UnitPrice REAL NOT NULL CHECK(UnitPrice >= 0), " +
                "FOREIGN KEY(BillId) REFERENCES Bill(BillId) ON DELETE CASCADE, " +
                "FOREIGN KEY(MenuId) REFERENCES Menu(MenuId))");

        db.execSQL("CREATE INDEX idx_menu_category ON Menu(CategoryId)");
        db.execSQL("CREATE INDEX idx_bill_user_date ON Bill(UserId, DateOfBill DESC)");
        db.execSQL("CREATE INDEX idx_bill_detail_bill ON BillDetail(BillId)");

        seedDemoData(db);
    }

    private void seedDemoData(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            insertUser(db, 1, "demo-customer-001", "customer1@example.com", "Demo Customer", "Customer");
            insertUser(db, 2, "demo-customer-002", "customer2@example.com", "Sample Customer", "Customer");
            insertUser(db, 3, "demo-admin-001", "admin@example.com", "Cafe Administrator", "Administrator");

            insertCategory(db, 1, "Coffee", "Espresso-based and brewed coffee.");
            insertCategory(db, 2, "Tea", "Hot and iced tea.");
            insertCategory(db, 3, "Pastry", "Fresh bakery items.");

            insertMenu(db, 1, "Americano", "Espresso with hot water.", "", 35000, 1, true);
            insertMenu(db, 2, "Milk Tea", "Black tea with milk.", "", 40000, 2, true);
            insertMenu(db, 3, "Butter Croissant", "Flaky butter pastry.", "", 28000, 3, true);

            insertBill(db, 1, 1, "2026-10-10 09:30:00", Bill.STATUS_AWAITING_CONFIRMATION, 70000);
            insertBill(db, 2, 2, "2026-10-09 14:10:00", Bill.STATUS_IN_TRANSIT, 40000);
            insertBill(db, 3, 1, "2026-10-08 08:15:00", Bill.STATUS_COMPLETED, 56000);

            insertBillDetail(db, 1, 1, 1, 2, 35000);
            insertBillDetail(db, 2, 2, 2, 1, 40000);
            insertBillDetail(db, 3, 3, 3, 2, 28000);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void insertUser(SQLiteDatabase db, long id, String uid, String email, String name, String role) {
        ContentValues v = new ContentValues();
        v.put("UserId", id); v.put("FirebaseUid", uid); v.put("Email", email);
        v.put("FullName", name); v.put("Role", role);
        db.insertOrThrow(TABLE_USER, null, v);
    }

    private void insertCategory(SQLiteDatabase db, long id, String name, String description) {
        ContentValues v = new ContentValues();
        v.put("CategoryId", id); v.put("CategoryName", name); v.put("Description", description);
        db.insertOrThrow(TABLE_CATEGORY, null, v);
    }

    private void insertMenu(SQLiteDatabase db, long id, String name, String description,
                            String imageUrl, double price, long categoryId, boolean available) {
        ContentValues v = new ContentValues();
        v.put("MenuId", id); v.put("MenuName", name); v.put("Description", description);
        v.put("ImageUrl", imageUrl); v.put("Price", price); v.put("CategoryId", categoryId);
        v.put("IsAvailable", available ? 1 : 0);
        db.insertOrThrow(TABLE_MENU, null, v);
    }

    private void insertBill(SQLiteDatabase db, long id, long userId, String date,
                            String status, double total) {
        ContentValues v = new ContentValues();
        v.put("BillId", id); v.put("UserId", userId); v.put("DateOfBill", date);
        v.put("Status", status); v.put("TotalAmount", total);
        db.insertOrThrow(TABLE_BILL, null, v);
    }

    private void insertBillDetail(SQLiteDatabase db, long id, long billId, long menuId,
                                  int quantity, double unitPrice) {
        ContentValues v = new ContentValues();
        v.put("BillDetailId", id); v.put("BillId", billId); v.put("MenuId", menuId);
        v.put("Quantity", quantity); v.put("UnitPrice", unitPrice);
        db.insertOrThrow(TABLE_BILL_DETAIL, null, v);
    }

    public List<Category> getAllCategories() {
        List<Category> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_CATEGORY,
                new String[]{"CategoryId", "CategoryName", "Description"},
                null, null, null, null, "CategoryName ASC")) {
            while (c.moveToNext()) {
                result.add(new Category(c.getLong(0), c.getString(1), c.getString(2)));
            }
        }
        return result;
    }

    public List<MenuItem> getAllMenuItems() {
        List<MenuItem> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_MENU,
                new String[]{"MenuId", "CategoryId", "MenuName", "Description", "ImageUrl", "Price", "IsAvailable"},
                null, null, null, null, "MenuName ASC")) {
            while (c.moveToNext()) {
                result.add(new MenuItem(c.getLong(0), c.getLong(1), c.getString(2),
                        c.getString(3), c.getString(4), c.getDouble(5), c.getInt(6) == 1));
            }
        }
        return result;
    }

    public List<MenuItem> getMenuByCategory(long categoryId) {
        List<MenuItem> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_MENU,
                new String[]{"MenuId", "CategoryId", "MenuName", "Description", "ImageUrl", "Price", "IsAvailable"},
                "CategoryId = ?", new String[]{String.valueOf(categoryId)},
                null, null, "MenuName ASC")) {
            while (c.moveToNext()) {
                result.add(new MenuItem(c.getLong(0), c.getLong(1), c.getString(2),
                        c.getString(3), c.getString(4), c.getDouble(5), c.getInt(6) == 1));
            }
        }
        return result;
    }

    public List<Bill> getBillsForUser(long userId) {
        List<Bill> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_BILL,
                new String[]{"BillId", "UserId", "DateOfBill", "Status", "TotalAmount"},
                "UserId = ?", new String[]{String.valueOf(userId)},
                null, null, "DateOfBill DESC")) {
            while (c.moveToNext()) {
                result.add(new Bill(c.getLong(0), c.getLong(1), c.getString(2),
                        c.getString(3), c.getDouble(4)));
            }
        }
        return result;
    }

    /** Cancels only orders that are still awaiting confirmation. */
    public boolean cancelBill(long billId, long userId) {
        ContentValues values = new ContentValues();
        values.put("Status", Bill.STATUS_CANCELLED);
        int changed = getWritableDatabase().update(TABLE_BILL, values,
                "BillId = ? AND UserId = ? AND Status = ?",
                new String[]{String.valueOf(billId), String.valueOf(userId),
                        Bill.STATUS_AWAITING_CONFIRMATION});
        return changed == 1;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Add explicit, versioned migration steps here when the schema changes.
        // Do not drop tables: existing customer/order data must be preserved.
    }
}
