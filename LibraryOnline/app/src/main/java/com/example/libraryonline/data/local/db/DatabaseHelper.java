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
    public static final int DATABASE_VERSION = 5;

    public DatabaseHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
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
            seedSampleBooks(db);
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
        if (oldVersion < 5) {
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
            seedSampleBooks(db);
            // Backfill IDs for demo books inserted during the upgrade too.
            db.execSQL("UPDATE BookTitles SET categoryId = " +
                    "(SELECT categoryId FROM Categories WHERE Categories.name = BookTitles.category) " +
                    "WHERE categoryId IS NULL");
        }
    }

    private static void seedSampleBooks(SQLiteDatabase db) {
        String now = "datetime('now')";
        String[][] books = {
            {"BOOK001","Sapiens: Lược sử loài người","Yuval Noah Harari","9780062315007","Lịch sử","Harper","2015","Hành trình nhìn lại lịch sử loài người từ thời đồ đá đến thế giới hiện đại.","https://covers.openlibrary.org/b/isbn/9780062315007-L.jpg","4","4"},
            {"BOOK002","Atomic Habits: Thay đổi tí hon, hiệu quả bất ngờ","James Clear","9780735211292","Tâm lý","Avery","2018","Phương pháp xây dựng thói quen tốt bằng những thay đổi nhỏ và bền vững.","https://covers.openlibrary.org/b/isbn/9780735211292-L.jpg","5","4"},
            {"BOOK003","Đắc Nhân Tâm","Dale Carnegie","9780671027032","Tâm lý","Simon & Schuster","1998","Những nguyên tắc giao tiếp và xây dựng quan hệ giữa người với người.","https://covers.openlibrary.org/b/isbn/9780671027032-L.jpg","6","5"},
            {"BOOK004","1984","George Orwell","9780451524935","Văn học","Signet Classics","1950","Tiểu thuyết phản địa đàng kinh điển về quyền lực, giám sát và tự do cá nhân.","https://covers.openlibrary.org/b/isbn/9780451524935-L.jpg","3","2"},
            {"BOOK005","Giết con chim nhại","Harper Lee","9780061120084","Văn học","Harper Perennial","2006","Tác phẩm kinh điển về công lý, lòng trắc ẩn và tuổi thơ ở miền Nam nước Mỹ.","https://covers.openlibrary.org/b/isbn/9780061120084-L.jpg","4","4"},
            {"BOOK006","The Great Gatsby","F. Scott Fitzgerald","9780743273565","Văn học","Scribner","2004","Bức chân dung nước Mỹ thập niên 1920 qua giấc mơ và bi kịch của Jay Gatsby.","https://covers.openlibrary.org/b/isbn/9780743273565-L.jpg","3","3"},
            {"BOOK007","Pride and Prejudice","Jane Austen","9780141439518","Văn học","Penguin Classics","2002","Tiểu thuyết về tình yêu, định kiến xã hội và những lựa chọn cá nhân.","https://covers.openlibrary.org/b/isbn/9780141439518-L.jpg","2","1"},
            {"BOOK008","The Psychology of Money","Morgan Housel","9780857197689","Kinh tế","Harriman House","2020","Những bài học về hành vi, rủi ro và cách con người đưa ra quyết định tài chính.","https://covers.openlibrary.org/b/isbn/9780857197689-L.jpg","4","4"},
            {"BOOK009","Clean Code","Robert C. Martin","9780132350884","Công nghệ","Prentice Hall","2008","Các nguyên tắc thực hành để viết mã nguồn dễ đọc, dễ bảo trì và có chất lượng.","https://covers.openlibrary.org/b/isbn/9780132350884-L.jpg","2","2"},
            {"BOOK010","A Brief History of Time","Stephen Hawking","9780553380163","Khoa học","Bantam","1998","Giới thiệu những câu hỏi lớn của vũ trụ học như thời gian, hố đen và nguồn gốc vũ trụ.","https://covers.openlibrary.org/b/isbn/9780553380163-L.jpg","3","3"}
        };
        for (String[] b : books) {
            String categoryId = com.example.libraryonline.data.local.dao.CategoryDao.idForName(b[4]);
            db.execSQL("INSERT OR IGNORE INTO Categories(categoryId,name) VALUES(?,?)",
                    new Object[]{categoryId, b[4]});
            db.execSQL("INSERT OR IGNORE INTO BookTitles " +
                    "(bookTitleId,title,author,isbn,categoryId,category,publisher,publishYear,description,coverUrl,totalCopies,availableCopies,isHidden,createdAt,updatedAt) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0," + now + "," + now + ")",
                    new Object[]{b[0],b[1],b[2],b[3],categoryId,b[4],b[5],Integer.parseInt(b[6]),b[7],b[8],Integer.parseInt(b[9]),Integer.parseInt(b[10])});
            int copies = Integer.parseInt(b[9]);
            for (int i = 1; i <= copies; i++) {
                String copyId = b[0] + "_COPY_" + String.format(java.util.Locale.ROOT, "%02d", i);
                String barcode = "BC-" + b[0] + "-" + String.format(java.util.Locale.ROOT, "%02d", i);
                String copyStatus = i <= Integer.parseInt(b[10]) ? "SanSang" : "DaMuon";
                db.execSQL("INSERT OR IGNORE INTO BookCopies " +
                        "(copyId,bookTitleId,barcode,shelfLocation,copyStatus,isHidden,createdAt,updatedAt) " +
                        "VALUES (?,?,?,?,?,0," + now + "," + now + ")",
                        new Object[]{copyId,b[0],barcode,"Kệ " + b[4],copyStatus});
            }
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
                "categoryId TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL UNIQUE, " +
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
