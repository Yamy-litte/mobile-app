package com.example.libraryonline.data.local.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Creates and upgrades the local SQLite database for LibraryOnline.
 * Passwords are not stored here; Firebase Authentication handles sign-in.
 */
public final class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "library_online.db";
    public static final int DATABASE_VERSION = 7;

    public DatabaseHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // v7 rebuilds only the Categories cache table, retaining every row and ID.
        // SQLite requires this pragma before the upgrade transaction starts.
        boolean upgradingCategoryCache = db.getVersion() > 0 && db.getVersion() < 7;
        db.setForeignKeyConstraintsEnabled(!upgradingCategoryCache);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            createUsers(db);
            createAdmins(db);
            createRegistrationRequests(db);
            createCategories(db);
            createBookTitles(db);
            createBookCopies(db);
            createLoanReceipts(db);
            createLoanDetails(db);
            createReservations(db);
            createPenalties(db);
            createPenaltyPayments(db);
            createSystemConfigs(db);
            createNotifications(db);
            createCategoryIntegrityTriggers(db);
            createIndexes(db);
            insertDefaultConfig(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Upgrade existing installs in place. Never delete user, loan, or catalogue data.
        if (oldVersion < 6) {
            createCategories(db);
            if (!columnExists(db, "BookTitles", "categoryId")) {
                db.execSQL("ALTER TABLE BookTitles ADD COLUMN categoryId TEXT");
            }
            // Convert legacy category names to stable IDs and ensure parent rows exist first.
            try (android.database.Cursor cursor = db.query(
                    "BookTitles", new String[]{"bookTitleId", "category"}, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    String bookId = cursor.getString(0);
                    String categoryName = cursor.getString(1);
                    if (categoryName == null || categoryName.trim().isEmpty()) categoryName = "Khác";
                    String categoryId = com.example.libraryonline.data.local.dao.CategoryDao.idForName(categoryName);
                    db.execSQL("INSERT OR IGNORE INTO Categories(categoryId,name) VALUES(?,?)",
                            new Object[]{categoryId, categoryName});
                    db.execSQL("UPDATE BookTitles SET categoryId=? WHERE bookTitleId=?",
                            new Object[]{categoryId, bookId});
                }
            }
            createCategoryIntegrityTriggers(db);
            db.execSQL("UPDATE BookTitles SET categoryId = " +
                    "(SELECT categoryId FROM Categories WHERE Categories.name = BookTitles.category) " +
                    "WHERE categoryId IS NULL");
        }
        if (oldVersion < 7) upgradeCategoryCache(db);
    }

    /** Keep category IDs authoritative; display names are not unique in Firestore. */
    private static void upgradeCategoryCache(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS trg_booktitles_category_insert");
        db.execSQL("DROP TRIGGER IF EXISTS trg_booktitles_category_update");
        db.execSQL("CREATE TABLE Categories_v7 (" +
                "categoryId TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, " +
                "isActive INTEGER NOT NULL DEFAULT 1 CHECK(isActive IN (0,1)), " +
                "createdAt TEXT NOT NULL DEFAULT (datetime('now')), " +
                "updatedAt TEXT NOT NULL DEFAULT (datetime('now')))");
        db.execSQL("INSERT INTO Categories_v7(categoryId,name,isActive,createdAt,updatedAt) " +
                "SELECT categoryId,name,isActive,createdAt,updatedAt FROM Categories");
        // Only the copied table is replaced; no book/category business rows are discarded.
        db.execSQL("DROP TABLE Categories");
        db.execSQL("ALTER TABLE Categories_v7 RENAME TO Categories");
        createCategoryIntegrityTriggers(db);
        try (android.database.Cursor cursor = db.rawQuery("PRAGMA foreign_key_check", null)) {
            if (cursor.moveToFirst()) throw new IllegalStateException("Invalid references in category cache upgrade");
        }
    }

    private static boolean columnExists(SQLiteDatabase db, String table, String column) {
        try (android.database.Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && column.equalsIgnoreCase(cursor.getString(nameIndex))) return true;
            }
        }
        return false;
    }

    private static void createUsers(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE Users (" +
                "userId TEXT PRIMARY KEY NOT NULL, " +
                "fullName TEXT NOT NULL, dateOfBirth TEXT, " +
                "email TEXT NOT NULL COLLATE NOCASE UNIQUE, phone TEXT, " +
                "barcode TEXT UNIQUE, role TEXT NOT NULL DEFAULT 'Reader' " +
                "CHECK(role IN ('Reader','Admin')), " +
                "cardStatus TEXT NOT NULL DEFAULT 'ChoDuyet' " +
                "CHECK(cardStatus IN ('ChoDuyet','HoatDong','BiKhoa','HetHan')), " +
                "cardExpiryDate TEXT, isDeleted INTEGER NOT NULL DEFAULT 0 " +
                "CHECK(isDeleted IN (0,1)), createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)");
    }

    private static void createAdmins(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE Admins (" +
                "adminId TEXT PRIMARY KEY NOT NULL, " +
                "userId TEXT NOT NULL UNIQUE, employeeCode TEXT NOT NULL UNIQUE, " +
                "adminStatus TEXT NOT NULL DEFAULT 'Active' " +
                "CHECK(adminStatus IN ('Active','Inactive')), " +
                "createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, " +
                "FOREIGN KEY(userId) REFERENCES Users(userId) ON UPDATE CASCADE ON DELETE RESTRICT)");
    }

    private static void createRegistrationRequests(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE RegistrationRequests (" +
                "requestId TEXT PRIMARY KEY NOT NULL, fullName TEXT NOT NULL, " +
                "dateOfBirth TEXT NOT NULL, email TEXT NOT NULL COLLATE NOCASE, " +
                "phone TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'Pending' " +
                "CHECK(status IN ('Pending','Approved','Rejected')), rejectionReason TEXT, " +
                "reviewedBy TEXT, reviewedAt TEXT, createdAt TEXT NOT NULL, " +
                "FOREIGN KEY(reviewedBy) REFERENCES Admins(adminId) ON UPDATE CASCADE ON DELETE SET NULL)");
    }

    private static void createCategories(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS Categories (" +
                "categoryId TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, " +
                "isActive INTEGER NOT NULL DEFAULT 1 CHECK(isActive IN (0,1)), " +
                "createdAt TEXT NOT NULL DEFAULT (datetime('now')), " +
                "updatedAt TEXT NOT NULL DEFAULT (datetime('now')))");
        String[] names = {"Văn học", "Khoa học", "Lịch sử", "Tâm lý", "Kinh tế", "Công nghệ", "Khác"};
        for (String name : names) {
            String id = com.example.libraryonline.data.local.dao.CategoryDao.idForName(name);
            db.execSQL("INSERT OR IGNORE INTO Categories(categoryId,name) VALUES(?,?)",
                    new Object[]{id, name});
        }
    }

    private static void createCategoryIntegrityTriggers(SQLiteDatabase db) {
        db.execSQL("CREATE TRIGGER IF NOT EXISTS trg_booktitles_category_insert " +
                "BEFORE INSERT ON BookTitles WHEN NEW.categoryId IS NOT NULL AND " +
                "NOT EXISTS (SELECT 1 FROM Categories WHERE categoryId = NEW.categoryId) " +
                "BEGIN SELECT RAISE(ABORT, 'Invalid BookTitles.categoryId'); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS trg_booktitles_category_update " +
                "BEFORE UPDATE OF categoryId ON BookTitles WHEN NEW.categoryId IS NOT NULL AND " +
                "NOT EXISTS (SELECT 1 FROM Categories WHERE categoryId = NEW.categoryId) " +
                "BEGIN SELECT RAISE(ABORT, 'Invalid BookTitles.categoryId'); END");
    }

    private static void createBookTitles(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE BookTitles (" +
                "bookTitleId TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, author TEXT, " +
                "isbn TEXT, categoryId TEXT, category TEXT, publisher TEXT, publishYear INTEGER, description TEXT, " +
                "coverUrl TEXT, totalCopies INTEGER NOT NULL DEFAULT 0 CHECK(totalCopies >= 0), " +
                "availableCopies INTEGER NOT NULL DEFAULT 0 CHECK(availableCopies >= 0), " +
                "isHidden INTEGER NOT NULL DEFAULT 0 CHECK(isHidden IN (0,1)), " +
                "createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, FOREIGN KEY(categoryId) REFERENCES Categories(categoryId) ON UPDATE CASCADE ON DELETE RESTRICT)");
    }

    private static void createBookCopies(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE BookCopies (" +
                "copyId TEXT PRIMARY KEY NOT NULL, bookTitleId TEXT NOT NULL, " +
                "barcode TEXT NOT NULL UNIQUE, shelfLocation TEXT, " +
                "copyStatus TEXT NOT NULL DEFAULT 'SanSang' " +
                "CHECK(copyStatus IN ('SanSang','ChoLay','DaMuon','DaDatTruoc','DangSuaChua','ThanhLy','DaMat')), " +
                "priorityUserId TEXT, isHidden INTEGER NOT NULL DEFAULT 0 CHECK(isHidden IN (0,1)), " +
                "createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, " +
                "FOREIGN KEY(bookTitleId) REFERENCES BookTitles(bookTitleId) ON UPDATE CASCADE ON DELETE RESTRICT, " +
                "FOREIGN KEY(priorityUserId) REFERENCES Users(userId) ON UPDATE CASCADE ON DELETE SET NULL)");
    }

    private static void createLoanReceipts(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE LoanReceipts (" +
                "loanReceiptId TEXT PRIMARY KEY NOT NULL, userId TEXT NOT NULL, adminId TEXT, " +
                "loanType TEXT NOT NULL CHECK(loanType IN ('TrucTiep','Online')), " +
                "receiptStatus TEXT NOT NULL DEFAULT 'Open' CHECK(receiptStatus IN ('Open','Completed','Cancelled')), " +
                "createdAt TEXT NOT NULL, checkoutAt TEXT, closedAt TEXT, " +
                "FOREIGN KEY(userId) REFERENCES Users(userId) ON UPDATE CASCADE ON DELETE RESTRICT, " +
                "FOREIGN KEY(adminId) REFERENCES Admins(adminId) ON UPDATE CASCADE ON DELETE SET NULL)");
    }

    private static void createLoanDetails(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE LoanDetails (" +
                "loanDetailId TEXT PRIMARY KEY NOT NULL, loanReceiptId TEXT NOT NULL, copyId TEXT NOT NULL, " +
                "borrowDate TEXT, dueDate TEXT, returnDate TEXT, " +
                "detailStatus TEXT NOT NULL DEFAULT 'DangMuon' " +
                "CHECK(detailStatus IN ('DangMuon','DaTra','QuaHan','Mat')), " +
                "extensionCount INTEGER NOT NULL DEFAULT 0 CHECK(extensionCount >= 0), " +
                "extended INTEGER NOT NULL DEFAULT 0 CHECK(extended IN (0,1)), " +
                "originalDueDate TEXT, createdAt TEXT NOT NULL, " +
                "FOREIGN KEY(loanReceiptId) REFERENCES LoanReceipts(loanReceiptId) ON UPDATE CASCADE ON DELETE RESTRICT, " +
                "FOREIGN KEY(copyId) REFERENCES BookCopies(copyId) ON UPDATE CASCADE ON DELETE RESTRICT)");
    }

    private static void createReservations(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE Reservations (" +
                "reservationId TEXT PRIMARY KEY NOT NULL, userId TEXT NOT NULL, bookTitleId TEXT NOT NULL, " +
                "copyId TEXT, reservationType TEXT NOT NULL CHECK(reservationType IN ('Online','DatTruoc')), " +
                "reservationStatus TEXT NOT NULL, queuePosition INTEGER, " +
                "requestedLoanDays INTEGER NOT NULL DEFAULT 14 CHECK(requestedLoanDays > 0), " +
                "createdAt TEXT NOT NULL, holdUntil TEXT, priorityUntil TEXT, notifiedAt TEXT, cancelledAt TEXT, " +
                "FOREIGN KEY(userId) REFERENCES Users(userId) ON UPDATE CASCADE ON DELETE RESTRICT, " +
                "FOREIGN KEY(bookTitleId) REFERENCES BookTitles(bookTitleId) ON UPDATE CASCADE ON DELETE RESTRICT, " +
                "FOREIGN KEY(copyId) REFERENCES BookCopies(copyId) ON UPDATE CASCADE ON DELETE SET NULL)");
    }

    private static void createPenalties(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE Penalties (" +
                "penaltyId TEXT PRIMARY KEY NOT NULL, userId TEXT NOT NULL, loanDetailId TEXT, copyId TEXT NOT NULL, " +
                "penaltyType TEXT NOT NULL CHECK(penaltyType IN ('LateReturn','LostBook')), " +
                "daysLate INTEGER NOT NULL DEFAULT 0 CHECK(daysLate >= 0), " +
                "amountDue INTEGER NOT NULL CHECK(amountDue >= 0), amountPaid INTEGER NOT NULL DEFAULT 0 " +
                "CHECK(amountPaid >= 0), " +
                "penaltyStatus TEXT NOT NULL DEFAULT 'Unpaid' CHECK(penaltyStatus IN ('Unpaid','Partial','Paid')), " +
                "createdAt TEXT NOT NULL, paidAt TEXT, " +
                "FOREIGN KEY(userId) REFERENCES Users(userId) ON UPDATE CASCADE ON DELETE RESTRICT, " +
                "FOREIGN KEY(loanDetailId) REFERENCES LoanDetails(loanDetailId) ON UPDATE CASCADE ON DELETE SET NULL, " +
                "FOREIGN KEY(copyId) REFERENCES BookCopies(copyId) ON UPDATE CASCADE ON DELETE RESTRICT)");
    }

    private static void createPenaltyPayments(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE PenaltyPayments (" +
                "paymentId TEXT PRIMARY KEY NOT NULL, penaltyId TEXT NOT NULL, receivedBy TEXT, " +
                "amountReceived INTEGER NOT NULL CHECK(amountReceived > 0), paymentMethod TEXT NOT NULL, " +
                "paymentStatus TEXT NOT NULL DEFAULT 'Confirmed' CHECK(paymentStatus IN ('Confirmed','Cancelled')), " +
                "receivedAt TEXT NOT NULL, note TEXT, " +
                "FOREIGN KEY(penaltyId) REFERENCES Penalties(penaltyId) ON UPDATE CASCADE ON DELETE RESTRICT, " +
                "FOREIGN KEY(receivedBy) REFERENCES Admins(adminId) ON UPDATE CASCADE ON DELETE SET NULL)");
    }

    private static void createSystemConfigs(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE SystemConfigs (" +
                "configId TEXT PRIMARY KEY NOT NULL, " +
                "maxBooksPerUser INTEGER NOT NULL CHECK(maxBooksPerUser > 0), " +
                "maxLoanDays INTEGER NOT NULL CHECK(maxLoanDays > 0), " +
                "onlineHoldHours INTEGER NOT NULL CHECK(onlineHoldHours > 0), " +
                "reservationHoldHours INTEGER NOT NULL CHECK(reservationHoldHours > 0), " +
                "lateFeePerDay INTEGER NOT NULL CHECK(lateFeePerDay >= 0), " +
                "lostBookMultiplier INTEGER NOT NULL CHECK(lostBookMultiplier > 0), " +
                "updatedBy TEXT, updatedAt TEXT NOT NULL, " +
                "FOREIGN KEY(updatedBy) REFERENCES Admins(adminId) ON UPDATE CASCADE ON DELETE SET NULL)");
    }

    private static void createNotifications(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE Notifications (" +
                "notificationId TEXT PRIMARY KEY NOT NULL, userId TEXT NOT NULL, " +
                "notificationType TEXT NOT NULL, title TEXT NOT NULL, message TEXT NOT NULL, " +
                "relatedEntityType TEXT, relatedEntityId TEXT, scheduledAt TEXT, sentAt TEXT, readAt TEXT, " +
                "deliveryStatus TEXT NOT NULL DEFAULT 'Pending' " +
                "CHECK(deliveryStatus IN ('Pending','Sent','Failed','Read')), " +
                "FOREIGN KEY(userId) REFERENCES Users(userId) ON UPDATE CASCADE ON DELETE RESTRICT)");
    }

    private static void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX idx_registration_status ON RegistrationRequests(status)");
        db.execSQL("CREATE INDEX idx_booktitles_search ON BookTitles(title, author, category)");
        db.execSQL("CREATE INDEX idx_bookcopies_title_status ON BookCopies(bookTitleId, copyStatus)");
        db.execSQL("CREATE INDEX idx_loanreceipts_user_status ON LoanReceipts(userId, receiptStatus)");
        db.execSQL("CREATE INDEX idx_loandetails_copy_status ON LoanDetails(copyId, detailStatus)");
        db.execSQL("CREATE UNIQUE INDEX idx_one_active_loan_per_copy ON LoanDetails(copyId) " +
                "WHERE returnDate IS NULL AND detailStatus IN ('DangMuon','QuaHan')");
        db.execSQL("CREATE INDEX idx_reservations_queue ON Reservations(bookTitleId, reservationStatus, queuePosition)");
        db.execSQL("CREATE INDEX idx_penalties_user_status ON Penalties(userId, penaltyStatus)");
        db.execSQL("CREATE INDEX idx_payments_penalty_status ON PenaltyPayments(penaltyId, paymentStatus)");
        db.execSQL("CREATE INDEX idx_notifications_user_status ON Notifications(userId, deliveryStatus)");
    }

    private static void insertDefaultConfig(SQLiteDatabase db) {
        db.execSQL("INSERT INTO SystemConfigs " +
                "(configId, maxBooksPerUser, maxLoanDays, onlineHoldHours, reservationHoldHours, " +
                "lateFeePerDay, lostBookMultiplier, updatedBy, updatedAt) " +
                "VALUES ('default', 5, 14, 24, 48, 5000, 2, NULL, datetime('now'))");
    }
}
