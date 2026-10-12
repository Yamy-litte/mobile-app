package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Creates the local SQLite schema and a small demo dataset.
 * Authentication credentials are managed by Firebase Authentication;
 * passwords are intentionally not stored in this database.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "CafeBookingOnline.db";
    public static final int DATABASE_VERSION = 2;

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
        db.execSQL("CREATE TABLE " + TABLE_USER + " (" +
                "UserId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "FullName TEXT NOT NULL, " +
                "Email TEXT NOT NULL UNIQUE, " +
                "Phone TEXT, " +
                "Address TEXT, " +
                "Role TEXT NOT NULL DEFAULT 'Customer' CHECK (Role IN ('Customer','Administrator'))" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_CATEGORY + " (" +
                "CategoryId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "CategoryName TEXT NOT NULL, " +
                "Description TEXT" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_MENU + " (" +
                "MenuId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "CategoryId INTEGER NOT NULL, " +
                "MenuName TEXT NOT NULL, " +
                "Description TEXT, " +
                "ImageUrl TEXT, " +
                "Price REAL NOT NULL CHECK (Price >= 0), " +
                "IsAvailable INTEGER NOT NULL DEFAULT 1 CHECK (IsAvailable IN (0,1)), " +
                "FOREIGN KEY (CategoryId) REFERENCES " + TABLE_CATEGORY + "(CategoryId) ON UPDATE CASCADE ON DELETE RESTRICT" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_BILL + " (" +
                "BillId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "UserId INTEGER NOT NULL, " +
                "DateOfBill TEXT NOT NULL, " +
                "Status TEXT NOT NULL DEFAULT 'Awaiting Confirmation' " +
                "CHECK (Status IN ('Awaiting Confirmation','In Transit','Completed','Cancelled')), " +
                "TotalAmount REAL NOT NULL DEFAULT 0 CHECK (TotalAmount >= 0), " +
                "FOREIGN KEY (UserId) REFERENCES " + TABLE_USER + "(UserId) ON UPDATE CASCADE ON DELETE RESTRICT" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_BILL_DETAIL + " (" +
                "BillDetailId INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "BillId INTEGER NOT NULL, " +
                "MenuId INTEGER NOT NULL, " +
                "Quantity INTEGER NOT NULL CHECK (Quantity > 0), " +
                "UnitPrice REAL NOT NULL CHECK (UnitPrice >= 0), " +
                "FOREIGN KEY (BillId) REFERENCES " + TABLE_BILL + "(BillId) ON UPDATE CASCADE ON DELETE CASCADE, " +
                "FOREIGN KEY (MenuId) REFERENCES " + TABLE_MENU + "(MenuId) ON UPDATE CASCADE ON DELETE RESTRICT" +
                ")");

        insertDemoData(db);
        insertAdditionalMenuData(db);
    }

    private void insertDemoData(SQLiteDatabase db) {
        // Users: demo profile records only; no passwords are stored here.
        insert(db, TABLE_USER, values(
                "FullName", "Nguyen An",
                "Email", "an@example.com",
                "Phone", "0900000001",
                "Address", "Ho Chi Minh City",
                "Role", "Customer"));
        insert(db, TABLE_USER, values(
                "FullName", "Tran Binh",
                "Email", "binh@example.com",
                "Phone", "0900000002",
                "Address", "Thu Duc City",
                "Role", "Customer"));
        insert(db, TABLE_USER, values(
                "FullName", "Cafe Administrator",
                "Email", "admin@example.com",
                "Phone", "0900000003",
                "Address", "Ho Chi Minh City",
                "Role", "Administrator"));

        insert(db, TABLE_CATEGORY, values(
                "CategoryName", "Coffee",
                "Description", "Espresso-based and Vietnamese coffee"));
        insert(db, TABLE_CATEGORY, values(
                "CategoryName", "Tea",
                "Description", "Hot and iced tea"));
        insert(db, TABLE_CATEGORY, values(
                "CategoryName", "Cold Drinks",
                "Description", "Refreshing cold beverages"));

        insert(db, TABLE_MENU, values(
                "CategoryId", 1,
                "MenuName", "Vietnamese Iced Coffee",
                "Description", "Strong coffee with condensed milk",
                "ImageUrl", "",
                "Price", 35000,
                "IsAvailable", 1));
        insert(db, TABLE_MENU, values(
                "CategoryId", 2,
                "MenuName", "Peach Tea",
                "Description", "Chilled tea with peach",
                "ImageUrl", "",
                "Price", 40000,
                "IsAvailable", 1));
        insert(db, TABLE_MENU, values(
                "CategoryId", 3,
                "MenuName", "Lemon Soda",
                "Description", "Sparkling soda with fresh lemon",
                "ImageUrl", "",
                "Price", 30000,
                "IsAvailable", 1));

        insert(db, TABLE_BILL, values(
                "UserId", 1,
                "DateOfBill", "2026-10-12T08:00:00",
                "Status", "Awaiting Confirmation",
                "TotalAmount", 70000));
        insert(db, TABLE_BILL, values(
                "UserId", 2,
                "DateOfBill", "2026-10-12T09:15:00",
                "Status", "In Transit",
                "TotalAmount", 80000));
        insert(db, TABLE_BILL, values(
                "UserId", 1,
                "DateOfBill", "2026-10-12T10:30:00",
                "Status", "Completed",
                "TotalAmount", 60000));

        // UnitPrice stores the menu price at purchase time, not a live menu lookup.
        insert(db, TABLE_BILL_DETAIL, values(
                "BillId", 1, "MenuId", 1, "Quantity", 2, "UnitPrice", 35000));
        insert(db, TABLE_BILL_DETAIL, values(
                "BillId", 2, "MenuId", 2, "Quantity", 2, "UnitPrice", 40000));
        insert(db, TABLE_BILL_DETAIL, values(
                "BillId", 3, "MenuId", 3, "Quantity", 2, "UnitPrice", 30000));
    }

    private ContentValues values(Object... pairs) {
        ContentValues contentValues = new ContentValues();
        for (int i = 0; i < pairs.length; i += 2) {
            String key = (String) pairs[i];
            Object value = pairs[i + 1];

            if (value instanceof String) {
                contentValues.put(key, (String) value);
            } else if (value instanceof Integer) {
                contentValues.put(key, (Integer) value);
            } else if (value instanceof Long) {
                contentValues.put(key, (Long) value);
            } else if (value instanceof Double) {
                contentValues.put(key, (Double) value);
            } else if (value instanceof Float) {
                contentValues.put(key, (Float) value);
            } else if (value == null) {
                contentValues.putNull(key);
            } else {
                throw new IllegalArgumentException("Unsupported ContentValues type for " + key);
            }
        }
        return contentValues;
    }

    private long insert(SQLiteDatabase db, String table, ContentValues values) {
        return db.insertOrThrow(table, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            insertAdditionalMenuData(db);
        }
    }

    private void insertAdditionalMenuData(SQLiteDatabase db) {
        insert(db, TABLE_CATEGORY, values("CategoryName", "Bakery", "Description", "Fresh baked treats"));
        insert(db, TABLE_CATEGORY, values("CategoryName", "Ice Blended", "Description", "Blended coffee and fruit drinks"));
        insert(db, TABLE_CATEGORY, values("CategoryName", "Snacks", "Description", "Light bites and desserts"));

        insertMenu(db, 1, "Espresso", "A short, rich espresso shot", 25000);
        insertMenu(db, 1, "Americano", "Espresso with hot water", 30000);
        insertMenu(db, 1, "Cappuccino", "Espresso with steamed milk foam", 42000);
        insertMenu(db, 1, "Cafe Latte", "Smooth espresso with milk", 45000);
        insertMenu(db, 1, "Caramel Macchiato", "Espresso, milk and caramel", 50000);
        insertMenu(db, 1, "Coconut Coffee", "Vietnamese coffee with coconut cream", 48000);
        insertMenu(db, 1, "Salt Coffee", "Creamy Vietnamese salted coffee", 45000);
        insertMenu(db, 1, "Mocha", "Chocolate espresso with milk", 48000);
        insertMenu(db, 2, "Milk Tea", "Classic black tea with milk", 38000);
        insertMenu(db, 2, "Matcha Latte", "Japanese matcha with creamy milk", 45000);
        insertMenu(db, 2, "Jasmine Tea", "Fragrant jasmine tea", 28000);
        insertMenu(db, 2, "Lychee Tea", "Iced tea with lychee", 40000);
        insertMenu(db, 2, "Lemon Honey Tea", "Tea with lemon and honey", 35000);
        insertMenu(db, 2, "Ginger Tea", "Warm ginger tea", 30000);
        insertMenu(db, 3, "Orange Juice", "Freshly squeezed orange juice", 40000);
        insertMenu(db, 3, "Watermelon Juice", "Fresh watermelon juice", 38000);
        insertMenu(db, 3, "Mango Smoothie", "Mango blended with yogurt", 45000);
        insertMenu(db, 3, "Strawberry Smoothie", "Strawberry blended drink", 48000);
        insertMenu(db, 3, "Passion Fruit Soda", "Sparkling passion fruit drink", 35000);
        insertMenu(db, 3, "Mineral Water", "Bottled drinking water", 15000);
        insertMenu(db, 4, "Butter Croissant", "Flaky butter pastry", 32000);
        insertMenu(db, 4, "Chocolate Muffin", "Soft muffin with chocolate chips", 30000);
        insertMenu(db, 4, "Blueberry Muffin", "Muffin with blueberries", 32000);
        insertMenu(db, 4, "Cheese Danish", "Pastry filled with cheese", 35000);
        insertMenu(db, 4, "Chocolate Donut", "Donut topped with chocolate", 25000);
        insertMenu(db, 5, "Caramel Frappe", "Blended coffee with caramel", 50000);
        insertMenu(db, 5, "Mocha Frappe", "Blended mocha with chocolate", 52000);
        insertMenu(db, 5, "Matcha Frappe", "Iced blended matcha drink", 50000);
        insertMenu(db, 5, "Oreo Frappe", "Cookies and cream blended drink", 52000);
        insertMenu(db, 5, "Mango Ice Blend", "Blended mango over ice", 48000);
        insertMenu(db, 6, "French Fries", "Crispy golden potato fries", 35000);
        insertMenu(db, 6, "Chicken Nuggets", "Crispy chicken bites", 45000);
        insertMenu(db, 6, "Club Sandwich", "Toasted sandwich with chicken and vegetables", 55000);
        insertMenu(db, 6, "Cheesecake", "Creamy slice of cheesecake", 48000);
        insertMenu(db, 6, "Tiramisu", "Coffee-flavoured Italian dessert", 50000);
    }

    private void insertMenu(SQLiteDatabase db, long categoryId, String name, String description, double price) {
        insert(db, TABLE_MENU, values(
                "CategoryId", categoryId,
                "MenuName", name,
                "Description", description,
                "ImageUrl", "",
                "Price", price,
                "IsAvailable", 1));
    }
}
