package com.example.libraryapp.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Inserts deterministic demo data once, so Reader screens can be tested immediately. */
public final class SampleDataSeeder {
    private SampleDataSeeder() {}

    public static void seed(Context context) {
        LibraryDbHelper helper = new LibraryDbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            insertCategory(db, "Văn học", "Tiểu thuyết và tác phẩm văn học.");
            insertCategory(db, "Khoa học", "Khoa học tự nhiên và khám phá.");
            insertCategory(db, "Công nghệ", "Công nghệ thông tin và kỹ thuật.");
            insertCategory(db, "Kỹ năng sống", "Phát triển bản thân và kỹ năng.");
            insertCategory(db, "Lịch sử", "Lịch sử Việt Nam và thế giới.");
            insertCategory(db, "Thiếu nhi", "Sách dành cho bạn đọc nhỏ tuổi.");

            insertLocation(db, "A", "1", "A-01");
            insertLocation(db, "A", "1", "A-02");
            insertLocation(db, "A", "2", "A-11");

            if (count(db, LibraryDbHelper.TABLE_BOOKS) == 0) {
                addBook(db, "Dế Mèn Phiêu Lưu Ký", "Tô Hoài", "Kim Đồng", 2022, "Hành trình trưởng thành của chú Dế Mèn qua nhiều cuộc phiêu lưu.", 144, 1, "A-01", 8, 6);
                addBook(db, "Cho Tôi Xin Một Vé Đi Tuổi Thơ", "Nguyễn Nhật Ánh", "Trẻ", 2021, "Câu chuyện trong trẻo về tuổi thơ và góc nhìn của trẻ nhỏ.", 208, 1, "A-01", 6, 4);
                addBook(db, "Nhà Giả Kim", "Paulo Coelho", "NXB Văn Học", 2020, "Cuộc hành trình theo đuổi ước mơ và khám phá bản thân.", 228, 1, "A-01", 10, 7);
                addBook(db, "Lược Sử Thời Gian", "Stephen Hawking", "Trẻ", 2019, "Giới thiệu các câu hỏi lớn về vũ trụ và thời gian.", 256, 2, "A-02", 5, 3);
                addBook(db, "Vũ Trụ Trong Vỏ Hạt Dẻ", "Stephen Hawking", "Trẻ", 2020, "Khám phá những ý tưởng nền tảng của vật lý hiện đại.", 224, 2, "A-02", 4, 2);
                addBook(db, "Clean Code", "Robert C. Martin", "Prentice Hall", 2008, "Các nguyên tắc viết mã nguồn rõ ràng và dễ bảo trì.", 464, 3, "A-02", 5, 3);
                addBook(db, "Đắc Nhân Tâm", "Dale Carnegie", "NXB Tổng hợp", 2022, "Những nguyên tắc giao tiếp và xây dựng quan hệ tích cực.", 320, 4, "A-11", 12, 9);
                addBook(db, "Atomic Habits", "James Clear", "Avery", 2018, "Phương pháp xây dựng thói quen tốt từ những thay đổi nhỏ.", 320, 4, "A-11", 7, 5);
                addBook(db, "Đại Việt Sử Ký Toàn Thư", "Ngô Sĩ Liên", "Văn Học", 2017, "Tác phẩm biên niên ghi chép lịch sử Việt Nam.", 680, 5, "A-11", 3, 1);
                addBook(db, "Hoàng Tử Bé", "Antoine de Saint-Exupéry", "NXB Hội Nhà Văn", 2021, "Câu chuyện giàu ý nghĩa về tình bạn và cách nhìn thế giới.", 120, 6, "A-01", 9, 8);
                addBook(db, "Harry Potter và Hòn Đá Phù Thủy", "J. K. Rowling", "Trẻ", 2021, "Cuốn mở đầu về thế giới phép thuật của Harry Potter.", 400, 6, "A-01", 6, 0);
                addBook(db, "Nhập Môn Lập Trình Java", "Nguyễn Văn Ba", "Giáo dục", 2023, "Tài liệu thực hành các kiến thức Java cơ bản.", 312, 3, "A-02", 5, 4);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            helper.close();
        }
    }

    private static long count(SQLiteDatabase db, String table) {
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }

    private static void insertCategory(SQLiteDatabase db, String name, String description) {
        ContentValues v = new ContentValues(); v.put("category_name", name); v.put("description", description);
        db.insertWithOnConflict(LibraryDbHelper.TABLE_CATEGORIES, null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void insertLocation(SQLiteDatabase db, String building, String floor, String shelf) {
        ContentValues v = new ContentValues(); v.put("building", building); v.put("floor", floor); v.put("shelf_number", shelf);
        db.insertWithOnConflict(LibraryDbHelper.TABLE_LOCATIONS, null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static int idBy(SQLiteDatabase db, String table, String column, String value) {
        try (Cursor c = db.query(table, new String[]{"id"}, column + "=?", new String[]{value}, null, null, null)) {
            return c.moveToFirst() ? c.getInt(0) : -1;
        }
    }

    private static void addBook(SQLiteDatabase db, String title, String author, String publisher, int year,
            String summary, int pages, int categoryId, String shelf, int total, int available) {
        int locationId;
        try (Cursor c = db.query(LibraryDbHelper.TABLE_LOCATIONS, new String[]{"id"},
                "shelf_number=?", new String[]{shelf}, null, null, null)) {
            if (!c.moveToFirst()) return;
            locationId = c.getInt(0);
        }
        ContentValues b = new ContentValues();
        b.put("category_id", categoryId); b.put("location_id", locationId); b.put("title", title);
        b.put("author", author); b.put("publisher", publisher); b.put("publish_year", year);
        b.put("summary", summary); b.put("pages", pages); b.put("total_quantity", total); b.put("available_quantity", available);
        long bookId = db.insert(LibraryDbHelper.TABLE_BOOKS, null, b);
        if (bookId < 0) return;
        for (int i = 1; i <= total; i++) {
            ContentValues copy = new ContentValues();
            copy.put("book_id", bookId);
            copy.put("barcode", String.format(java.util.Locale.US, "LIB-%04d-%02d", bookId, i));
            copy.put("status", i <= available ? "available" : "borrowed");
            db.insert(LibraryDbHelper.TABLE_BOOK_COPIES, null, copy);
        }
    }
}
