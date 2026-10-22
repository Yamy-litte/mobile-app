package com.example.libraryonline.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.model.BookCopy;
import com.example.libraryonline.data.model.User;
import com.example.libraryonline.domain.calculator.PenaltyCalculator;
import com.example.libraryonline.domain.validator.LoanValidator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Transactional local-first circulation operations. Cloud synchronization is intentionally
 * not implied here; callers must not report a remote write until the sync layer confirms it.
 */
public final class CirculationRepository implements AutoCloseable {
    private final DatabaseHelper helper;

    public CirculationRepository(Context context) {
        helper = new DatabaseHelper(context.getApplicationContext());
    }

    public OperationResult borrow(String readerBarcode, String copyBarcode, int requestedDays,
                                  String adminAuthUid) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            User reader = findReaderByBarcode(db, readerBarcode);
            if (reader == null) throw new IllegalArgumentException("Không tìm thấy thẻ độc giả.");
            if (reader.cardExpiryDate != null && !reader.cardExpiryDate.trim().isEmpty()
                    && today().compareTo(reader.cardExpiryDate.trim()) > 0) {
                throw new IllegalArgumentException("Thẻ độc giả đã hết hạn.");
            }
            String adminId = findActiveAdminId(db, adminAuthUid);
            int maxCopies, maxDays;
            try (Cursor c = db.rawQuery("SELECT maxBooksPerUser,maxLoanDays FROM SystemConfigs ORDER BY updatedAt DESC LIMIT 1", null)) {
                if (!c.moveToFirst()) throw new IllegalStateException("Chưa có cấu hình mượn sách.");
                maxCopies = c.getInt(0);
                maxDays = c.getInt(1);
            }

            BookCopy copy = findCopyByBarcode(db, copyBarcode);
            if (copy == null) throw new IllegalArgumentException("Không tìm thấy mã cuốn sách.");

            int activeCount = scalarInt(db,
                    "SELECT COUNT(*) FROM LoanDetails d JOIN LoanReceipts r ON r.loanReceiptId=d.loanReceiptId " +
                    "WHERE r.userId=? AND d.returnDate IS NULL AND d.detailStatus IN ('DangMuon','QuaHan')",
                    new String[]{reader.userId});
            activeCount += scalarInt(db,
                    "SELECT COUNT(*) FROM Reservations WHERE userId=? AND reservationType='Online' " +
                    "AND reservationStatus NOT IN ('Cancelled','Expired','Completed','Received') " +
                    "AND (copyId IS NULL OR copyId<>?)",
                    new String[]{reader.userId, copy.copyId});

            boolean hasOverdue = scalarInt(db,
                    "SELECT COUNT(*) FROM LoanDetails d JOIN LoanReceipts r ON r.loanReceiptId=d.loanReceiptId " +
                    "WHERE r.userId=? AND d.returnDate IS NULL AND d.dueDate < ?",
                    new String[]{reader.userId, today()}) > 0;

            ArrayList<String> activeTitles = activeTitleIds(db, reader.userId, copy.copyId);
            String reason = LoanValidator.validateBorrow(reader, activeCount, maxCopies, requestedDays,
                    maxDays, hasOverdue, activeTitles, copy.bookTitleId);
            if (reason != null) throw new IllegalArgumentException(reason);
            reason = LoanValidator.validateCopyCheckout(copy, reader.userId);
            if (reason != null) throw new IllegalArgumentException(reason);

            String now = timestamp();
            String receiptId = UUID.randomUUID().toString();
            String detailId = UUID.randomUUID().toString();
            String dueDate = addDays(today(), requestedDays);

            ContentValues receipt = new ContentValues();
            receipt.put("loanReceiptId", receiptId);
            receipt.put("userId", reader.userId);
            receipt.put("adminId", adminId);
            receipt.put("loanType", "TrucTiep");
            receipt.put("receiptStatus", "Open");
            receipt.put("createdAt", now);
            receipt.put("checkoutAt", now);
            receipt.putNull("closedAt");
            db.insertOrThrow("LoanReceipts", null, receipt);

            ContentValues detail = new ContentValues();
            detail.put("loanDetailId", detailId);
            detail.put("loanReceiptId", receiptId);
            detail.put("copyId", copy.copyId);
            detail.put("borrowDate", today());
            detail.put("dueDate", dueDate);
            detail.putNull("returnDate");
            detail.put("detailStatus", "DangMuon");
            detail.put("extensionCount", 0);
            detail.put("extended", 0);
            detail.putNull("originalDueDate");
            detail.put("createdAt", now);
            db.insertOrThrow("LoanDetails", null, detail);

            ContentValues copyUpdate = new ContentValues();
            copyUpdate.put("copyStatus", "DaMuon");
            copyUpdate.putNull("priorityUserId");
            copyUpdate.put("updatedAt", now);
            db.update("BookCopies", copyUpdate, "copyId=?", new String[]{copy.copyId});

            db.execSQL("UPDATE Reservations SET reservationStatus='Completed', cancelledAt=NULL " +
                    "WHERE userId=? AND copyId=? AND reservationType='Online' " +
                    "AND reservationStatus NOT IN ('Cancelled','Expired','Completed')",
                    new Object[]{reader.userId, copy.copyId});

            db.setTransactionSuccessful();
            return new OperationResult("Đã ghi nhận mượn sách. Hạn trả: " + dueDate, null, dueDate, 0L);
        } finally {
            db.endTransaction();
        }
    }

    public OperationResult returnCopy(String readerBarcode, String copyBarcode, String adminAuthUid) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            User reader = findReaderByBarcode(db, readerBarcode);
            if (reader == null) throw new IllegalArgumentException("Không tìm thấy thẻ độc giả.");
            String adminId = findActiveAdminId(db, adminAuthUid);
            BookCopy copy = findCopyByBarcode(db, copyBarcode);
            if (copy == null) throw new IllegalArgumentException("Không tìm thấy mã cuốn sách.");

            String detailId = null, dueDate = null, receiptId = null;
            try (Cursor c = db.rawQuery(
                    "SELECT d.loanDetailId,d.dueDate,d.loanReceiptId FROM LoanDetails d " +
                    "JOIN LoanReceipts r ON r.loanReceiptId=d.loanReceiptId " +
                    "WHERE r.userId=? AND d.copyId=? AND d.returnDate IS NULL " +
                    "AND d.detailStatus IN ('DangMuon','QuaHan') ORDER BY d.createdAt DESC LIMIT 1",
                    new String[]{reader.userId, copy.copyId})) {
                if (c.moveToFirst()) {
                    detailId = c.getString(0);
                    dueDate = c.getString(1);
                    receiptId = c.getString(2);
                }
            }
            if (detailId == null) throw new IllegalArgumentException("Độc giả này không có cuốn sách đang mượn với mã đã nhập.");

            String now = timestamp();
            String returned = today();
            int lateDays = PenaltyCalculator.lateDays(dueDate, returned);
            int feePerDay = scalarInt(db, "SELECT lateFeePerDay FROM SystemConfigs ORDER BY updatedAt DESC LIMIT 1", null);
            long fine = Math.multiplyExact((long) lateDays, (long) feePerDay);

            ContentValues detailUpdate = new ContentValues();
            detailUpdate.put("returnDate", returned);
            detailUpdate.put("detailStatus", "DaTra");
            int changedDetails = db.update("LoanDetails", detailUpdate,
                    "loanDetailId=? AND returnDate IS NULL", new String[]{detailId});
            if (changedDetails != 1) throw new IllegalStateException("Chi tiết mượn đã được xử lý bởi giao dịch khác.");
            int remainingDetails = scalarInt(db,
                    "SELECT COUNT(*) FROM LoanDetails WHERE loanReceiptId=? AND returnDate IS NULL AND detailStatus IN ('DangMuon','QuaHan')",
                    new String[]{receiptId});
            if (remainingDetails == 0) {
                ContentValues receiptUpdate = new ContentValues();
                receiptUpdate.put("receiptStatus", "Completed");
                receiptUpdate.put("closedAt", now);
                db.update("LoanReceipts", receiptUpdate, "loanReceiptId=?", new String[]{receiptId});
            }

            ContentValues copyUpdate = new ContentValues();
            copyUpdate.put("copyStatus", copy.isHidden ? "DangSuaChua" : "SanSang");
            copyUpdate.putNull("priorityUserId");
            copyUpdate.put("updatedAt", now);
            db.update("BookCopies", copyUpdate, "copyId=?", new String[]{copy.copyId});

            String penaltyId = null;
            if (fine > 0) {
                penaltyId = UUID.randomUUID().toString();
                ContentValues penalty = new ContentValues();
                penalty.put("penaltyId", penaltyId);
                penalty.put("userId", reader.userId);
                penalty.put("loanDetailId", detailId);
                penalty.put("copyId", copy.copyId);
                penalty.put("penaltyType", "LateReturn");
                penalty.put("daysLate", lateDays);
                penalty.put("amountDue", Math.toIntExact(fine));
                penalty.put("amountPaid", 0);
                penalty.put("penaltyStatus", "Unpaid");
                penalty.put("createdAt", now);
                penalty.putNull("paidAt");
                db.insertOrThrow("Penalties", null, penalty);

                ContentValues lock = new ContentValues();
                lock.put("cardStatus", "BiKhoa");
                lock.put("updatedAt", now);
                db.update("Users", lock, "userId=?", new String[]{reader.userId});
            }

            db.setTransactionSuccessful();
            String message = fine > 0
                    ? "Đã nhận trả sách. Trễ " + lateDays + " ngày, tiền phạt " + fine + " đồng. Mã khoản phạt: " + penaltyId + ". Thẻ đã bị khóa."
                    : "Đã nhận trả sách đúng hạn.";
            return new OperationResult(message, penaltyId, null, fine);
        } finally {
            db.endTransaction();
        }
    }


    /**
     * Collects the remaining balance of one fine as a single cash payment.
     * A paid fine cannot be charged again; the check and write share one SQLite transaction.
     * Card unlocking is deliberately not automatic because the current schema cannot distinguish
     * a fine-related lock from a separate manual administrative lock.
     */
    public OperationResult payPenalty(String penaltyId, String adminAuthUid) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            String adminId = findActiveAdminId(db, adminAuthUid);
            String userId = null;
            int due = 0, paid = 0;
            String status = null;
            try (Cursor c = db.query("Penalties", new String[]{"userId","amountDue","amountPaid","penaltyStatus"},
                    "penaltyId=?", new String[]{penaltyId == null ? "" : penaltyId.trim()},
                    null, null, null, "1")) {
                if (!c.moveToFirst()) throw new IllegalArgumentException("Không tìm thấy khoản phạt.");
                userId = c.getString(0);
                due = c.getInt(1);
                paid = c.getInt(2);
                status = c.getString(3);
            }
            int remaining = due - paid;
            if (remaining <= 0 || "Paid".equals(status)) {
                throw new IllegalArgumentException("Khoản phạt này đã được thanh toán đủ.");
            }
            String now = timestamp();
            ContentValues payment = new ContentValues();
            payment.put("paymentId", UUID.randomUUID().toString());
            payment.put("penaltyId", penaltyId.trim());
            payment.put("receivedBy", adminId);
            payment.put("amountReceived", remaining);
            payment.put("paymentMethod", "Cash");
            payment.put("paymentStatus", "Confirmed");
            payment.put("receivedAt", now);
            payment.put("note", "Thu tiền mặt tại quầy");
            db.insertOrThrow("PenaltyPayments", null, payment);

            ContentValues penaltyUpdate = new ContentValues();
            penaltyUpdate.put("amountPaid", due);
            penaltyUpdate.put("penaltyStatus", "Paid");
            penaltyUpdate.put("paidAt", now);
            db.update("Penalties", penaltyUpdate, "penaltyId=? AND amountPaid<?",
                    new String[]{penaltyId.trim(), String.valueOf(due)});

            int outstanding = scalarInt(db,
                    "SELECT COUNT(*) FROM Penalties WHERE userId=? AND penaltyStatus IN ('Unpaid','Partial')",
                    new String[]{userId});
            db.setTransactionSuccessful();
            String message = outstanding == 0
                    ? "Đã ghi nhận thu " + remaining + " đồng. Không còn khoản phạt chưa thanh toán. Thẻ chưa tự mở khóa vì dữ liệu hiện tại không phân biệt khóa do phạt với khóa thủ công."
                    : "Đã ghi nhận thu " + remaining + " đồng. Độc giả vẫn còn khoản phạt chưa thanh toán.";
            return new OperationResult(message, penaltyId.trim(), null, remaining);
        } finally {
            db.endTransaction();
        }
    }

    private static User findReaderByBarcode(SQLiteDatabase db, String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) return null;
        try (Cursor c = db.query("Users", null, "barcode=?", new String[]{barcode.trim()}, null, null, null, "1")) {
            if (!c.moveToFirst()) return null;
            User user = new User();
            user.userId = c.getString(c.getColumnIndexOrThrow("userId"));
            user.fullName = c.getString(c.getColumnIndexOrThrow("fullName"));
            user.cardStatus = c.getString(c.getColumnIndexOrThrow("cardStatus"));
            user.role = c.getString(c.getColumnIndexOrThrow("role"));
            user.isDeleted = c.getInt(c.getColumnIndexOrThrow("isDeleted")) == 1;
            user.cardExpiryDate = c.getString(c.getColumnIndexOrThrow("cardExpiryDate"));
            return user;
        }
    }

    private static String findActiveAdminId(SQLiteDatabase db, String authUid) {
        try (Cursor c = db.rawQuery("SELECT adminId FROM Admins WHERE userId=? AND adminStatus='Active' LIMIT 1",
                new String[]{authUid == null ? "" : authUid})) {
            if (c.moveToFirst()) return c.getString(0);
        }
        throw new IllegalArgumentException("Tài khoản hiện tại không có hồ sơ Admin đang hoạt động trong SQLite.");
    }

    private static BookCopy findCopyByBarcode(SQLiteDatabase db, String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) return null;
        try (Cursor c = db.rawQuery(
                "SELECT c.copyId,c.bookTitleId,c.barcode,c.copyStatus,c.priorityUserId,c.isHidden,t.isHidden " +
                "FROM BookCopies c JOIN BookTitles t ON t.bookTitleId=c.bookTitleId WHERE c.barcode=? LIMIT 1",
                new String[]{barcode.trim()})) {
            if (!c.moveToFirst()) return null;
            BookCopy copy = new BookCopy();
            copy.copyId = c.getString(0);
            copy.bookTitleId = c.getString(1);
            copy.barcode = c.getString(2);
            copy.copyStatus = c.getString(3);
            copy.priorityUserId = c.getString(4);
            copy.isHidden = c.getInt(5) == 1;
            copy.parentTitleHidden = c.getInt(6) == 1;
            return copy;
        }
    }

    private static ArrayList<String> activeTitleIds(SQLiteDatabase db, String userId, String excludedReservationCopyId) {
        ArrayList<String> ids = new ArrayList<>();
        try (Cursor c = db.rawQuery(
                "SELECT DISTINCT b.bookTitleId FROM LoanDetails d " +
                "JOIN LoanReceipts r ON r.loanReceiptId=d.loanReceiptId " +
                "JOIN BookCopies b ON b.copyId=d.copyId " +
                "WHERE r.userId=? AND d.returnDate IS NULL AND d.detailStatus IN ('DangMuon','QuaHan') " +
                "UNION SELECT DISTINCT bookTitleId FROM Reservations WHERE userId=? " +
                "AND reservationStatus NOT IN ('Cancelled','Expired','Completed') " +
                "AND (copyId IS NULL OR copyId<>?)",
                new String[]{userId, userId, excludedReservationCopyId})) {
            while (c.moveToNext()) ids.add(c.getString(0));
        }
        return ids;
    }

    private static int scalarInt(SQLiteDatabase db, String sql, String[] args) {
        try (Cursor c = db.rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private static String today() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        f.setTimeZone(TimeZone.getDefault());
        return f.format(new Date());
    }

    private static String timestamp() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
        f.setTimeZone(TimeZone.getDefault());
        return f.format(new Date());
    }

    private static String addDays(String date, int days) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        f.setLenient(false);
        f.setTimeZone(TimeZone.getDefault());
        try {
            Date parsed = f.parse(date);
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(parsed);
            cal.add(java.util.Calendar.DAY_OF_YEAR, days);
            return f.format(cal.getTime());
        } catch (java.text.ParseException ex) {
            throw new IllegalArgumentException("Không thể tính hạn trả sách.", ex);
        }
    }

    @Override public void close() { helper.close(); }

    public static final class OperationResult {
        public final String message;
        public final String relatedId;
        public final String dueDate;
        public final long fineVnd;

        OperationResult(String message, String relatedId, String dueDate, long fineVnd) {
            this.message = message;
            this.relatedId = relatedId;
            this.dueDate = dueDate;
            this.fineVnd = fineVnd;
        }
    }
}
