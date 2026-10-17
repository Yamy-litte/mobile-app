package com.example.libraryapp.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Creates and upgrades the local SQLite database for MyLibrary.
 *
 * Date/time values are stored as ISO-8601 TEXT. Foreign-key enforcement is
 * enabled for each database connection. Keep table/column names aligned with
 * the project's ERD when adding DAO methods.
 */
public class LibraryDbHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "mylibrary.db";
    public static final int DATABASE_VERSION = 3;

    public static final String TABLE_ADMIN = "admin";
    public static final String TABLE_CUSTOMERS = "customers";
    public static final String TABLE_NOTIFICATIONS = "notifications";
    public static final String TABLE_LIBRARY_CARDS = "library_cards";
    public static final String TABLE_CATEGORIES = "categories";
    public static final String TABLE_LOCATIONS = "locations";
    public static final String TABLE_BOOKS = "books";
    public static final String TABLE_BOOK_COPIES = "book_copies";
    public static final String TABLE_RESERVATIONS = "reservations";
    public static final String TABLE_BORROW_RECORDS = "borrow_records";
    public static final String TABLE_EXTENSIONS = "extensions";
    public static final String TABLE_FINES = "fines";

    public LibraryDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_ADMIN + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "firebase_uid TEXT NOT NULL UNIQUE, " +
                "email TEXT NOT NULL UNIQUE, " +
                "full_name TEXT NOT NULL, " +
                "status TEXT NOT NULL DEFAULT 'active', " +
                "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        db.execSQL("CREATE TABLE " + TABLE_CUSTOMERS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "firebase_uid TEXT NOT NULL UNIQUE, " +
                "email TEXT NOT NULL UNIQUE, " +
                "full_name TEXT NOT NULL, " +
                "phone_number TEXT, " +
                "avatar_url TEXT, " +
                "status TEXT NOT NULL DEFAULT 'active', " +
                "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_CATEGORIES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "category_name TEXT NOT NULL UNIQUE, " +
                "description TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_LOCATIONS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "building TEXT NOT NULL, " +
                "floor TEXT NOT NULL, " +
                "shelf_number TEXT NOT NULL, " +
                "UNIQUE(building, floor, shelf_number))");

        db.execSQL("CREATE TABLE " + TABLE_LIBRARY_CARDS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "customer_id INTEGER NOT NULL UNIQUE, " +
                "card_number TEXT NOT NULL UNIQUE, " +
                "status TEXT NOT NULL DEFAULT 'active', " +
                "issue_date TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY(customer_id) REFERENCES " + TABLE_CUSTOMERS + "(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE " + TABLE_BOOKS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "category_id INTEGER NOT NULL, " +
                "location_id INTEGER NOT NULL, " +
                "title TEXT NOT NULL, " +
                "author TEXT NOT NULL, " +
                "publisher TEXT, " +
                "publish_year INTEGER, " +
                "summary TEXT, " +
                "image_url TEXT, " +
                "pages INTEGER NOT NULL DEFAULT 0, " +
                "total_quantity INTEGER NOT NULL DEFAULT 0 CHECK(total_quantity >= 0), " +
                "available_quantity INTEGER NOT NULL DEFAULT 0 CHECK(available_quantity >= 0 AND available_quantity <= total_quantity), " +
                "FOREIGN KEY(category_id) REFERENCES " + TABLE_CATEGORIES + "(id), " +
                "FOREIGN KEY(location_id) REFERENCES " + TABLE_LOCATIONS + "(id))");

        db.execSQL("CREATE TABLE " + TABLE_BOOK_COPIES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "book_id INTEGER NOT NULL, " +
                "barcode TEXT NOT NULL UNIQUE, " +
                "status TEXT NOT NULL DEFAULT 'available', " +
                "FOREIGN KEY(book_id) REFERENCES " + TABLE_BOOKS + "(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE " + TABLE_RESERVATIONS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "customer_id INTEGER NOT NULL, " +
                "book_id INTEGER NOT NULL, " +
                "book_copy_id INTEGER, " +
                "reservation_date TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "status TEXT NOT NULL DEFAULT 'pending', " +
                "expiration_date TEXT, " +
                "FOREIGN KEY(customer_id) REFERENCES " + TABLE_CUSTOMERS + "(id) ON DELETE CASCADE, " +
                "FOREIGN KEY(book_id) REFERENCES " + TABLE_BOOKS + "(id), " +
                "FOREIGN KEY(book_copy_id) REFERENCES " + TABLE_BOOK_COPIES + "(id))");

        db.execSQL("CREATE TABLE " + TABLE_BORROW_RECORDS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "customer_id INTEGER NOT NULL, " +
                "book_copy_id INTEGER NOT NULL, " +
                "admin_borrow_id INTEGER, " +
                "admin_return_id INTEGER, " +
                "borrow_date TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "due_date TEXT NOT NULL, " +
                "return_date TEXT, " +
                "physical_condition_on_return TEXT, " +
                "status TEXT NOT NULL DEFAULT 'borrowed', " +
                "FOREIGN KEY(customer_id) REFERENCES " + TABLE_CUSTOMERS + "(id), " +
                "FOREIGN KEY(book_copy_id) REFERENCES " + TABLE_BOOK_COPIES + "(id), " +
                "FOREIGN KEY(admin_borrow_id) REFERENCES " + TABLE_ADMIN + "(id), " +
                "FOREIGN KEY(admin_return_id) REFERENCES " + TABLE_ADMIN + "(id))");

        db.execSQL("CREATE TABLE " + TABLE_EXTENSIONS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "borrow_record_id INTEGER NOT NULL, " +
                "request_date TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "extended_days INTEGER NOT NULL DEFAULT 0 CHECK(extended_days >= 0), " +
                "status TEXT NOT NULL DEFAULT 'pending', " +
                "notes TEXT, " +
                "FOREIGN KEY(borrow_record_id) REFERENCES " + TABLE_BORROW_RECORDS + "(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE " + TABLE_FINES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "borrow_record_id INTEGER NOT NULL, " +
                "fine_amount REAL NOT NULL DEFAULT 0 CHECK(fine_amount >= 0), " +
                "fine_reason TEXT NOT NULL, " +
                "status TEXT NOT NULL DEFAULT 'unpaid', " +
                "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "paid_at TEXT, " +
                "FOREIGN KEY(borrow_record_id) REFERENCES " + TABLE_BORROW_RECORDS + "(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE " + TABLE_NOTIFICATIONS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "customer_id INTEGER, " +
                "admin_id INTEGER, " +
                "title TEXT NOT NULL, " +
                "content TEXT NOT NULL, " +
                "is_read INTEGER NOT NULL DEFAULT 0 CHECK(is_read IN (0, 1)), " +
                "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY(customer_id) REFERENCES " + TABLE_CUSTOMERS + "(id) ON DELETE CASCADE, " +
                "FOREIGN KEY(admin_id) REFERENCES " + TABLE_ADMIN + "(id) ON DELETE CASCADE)");

        db.execSQL("CREATE INDEX idx_books_title ON " + TABLE_BOOKS + "(title)");
        db.execSQL("CREATE INDEX idx_books_category ON " + TABLE_BOOKS + "(category_id)");
        db.execSQL("CREATE INDEX idx_copies_book_status ON " + TABLE_BOOK_COPIES + "(book_id, status)");
        db.execSQL("CREATE INDEX idx_reservations_customer_status ON " + TABLE_RESERVATIONS + "(customer_id, status)");
        db.execSQL("CREATE INDEX idx_borrow_customer_status ON " + TABLE_BORROW_RECORDS + "(customer_id, status)");
        db.execSQL("CREATE INDEX idx_fines_status ON " + TABLE_FINES + "(status)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Existing v1 databases get Firebase UID columns. Legacy password columns are not
        // read or written. Clear app data/reinstall during development to recreate
        // the clean v2 schema without legacy password_hash columns.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_ADMIN + " ADD COLUMN firebase_uid TEXT");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_admin_firebase_uid ON " + TABLE_ADMIN + "(firebase_uid)");
            db.execSQL("ALTER TABLE " + TABLE_CUSTOMERS + " ADD COLUMN firebase_uid TEXT");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_firebase_uid ON " + TABLE_CUSTOMERS + "(firebase_uid)");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN image_url TEXT");
        }
    }
}
