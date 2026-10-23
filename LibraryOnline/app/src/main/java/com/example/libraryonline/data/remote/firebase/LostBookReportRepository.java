package com.example.libraryonline.data.remote.firebase;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.Map;

/** Three-step Lost Book workflow. No payment is recorded before in-person confirmation. */
public final class LostBookReportRepository {
    public static final String COLLECTION = "LostBookReports";
    public static final String PENDING = "Pending", PROCESSING = "Processing",
            COMPLETED = "Completed";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private static Task<Void> error(String message) {
        return Tasks.forException(new IllegalArgumentException(message));
    }

    /** One immutable report per Online receipt. Reader cannot directly lock a card. */
    public Task<Void> report(String readerUid, String receiptId, String reason,
                             String commitment) {
        String auth = FirebaseAuth.getInstance().getUid();
        if (auth == null || !auth.equals(readerUid)) return error("Phiên Reader không hợp lệ.");
        if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 500)
            return error("Lý do mất sách từ 10 đến 500 ký tự.");
        if (commitment == null || commitment.trim().length() < 10
                || commitment.trim().length() > 500)
            return error("Cam kết bồi thường từ 10 đến 500 ký tự.");
        DocumentReference receiptRef = db.collection("OnlineLoanReceipts").document(receiptId);
        DocumentReference reportRef = db.collection(COLLECTION).document(receiptId);
        return db.runTransaction(tx -> {
            DocumentSnapshot receipt = tx.get(receiptRef);
            DocumentSnapshot previous = tx.get(reportRef);
            if (!receipt.exists() || !"Open".equals(receipt.getString("status"))
                    || !readerUid.equals(receipt.getString("userId")))
                throw new IllegalArgumentException("Không có phiếu mượn Online đang mở.");
            if (previous.exists())
                throw new IllegalArgumentException("Cuốn sách này đã được báo mất.");
            Map<String, Object> row = new HashMap<>();
            row.put("receiptId", receiptId);
            row.put("requestId", receipt.getString("requestId"));
            row.put("userId", readerUid);
            row.put("bookTitleId", receipt.getString("bookTitleId"));
            row.put("bookTitle", receipt.getString("bookTitle"));
            row.put("copyId", receipt.getString("copyId"));
            row.put("reason", reason.trim());
            row.put("commitment", commitment.trim());
            row.put("status", PENDING);
            row.put("reportedAt", FieldValue.serverTimestamp());
            row.put("updatedAt", FieldValue.serverTimestamp());
            tx.set(reportRef, row);
            return (Void) null;
        });
    }

    /** Admin explicitly acknowledges the report, then only NEW borrowing is blocked. */
    public Task<Void> accept(String reportId, String adminUid) {
        if (adminUid == null || !adminUid.equals(FirebaseAuth.getInstance().getUid()))
            return error("Phiên Admin không hợp lệ.");
        DocumentReference reportRef = db.collection(COLLECTION).document(reportId);
        return reportRef.get(Source.SERVER).onSuccessTask(snapshot -> {
            if (!snapshot.exists() || !PENDING.equals(snapshot.getString("status")))
                return error("Báo mất đã được xử lý hoặc không tồn tại.");
            String readerUid = snapshot.getString("userId");
            if (readerUid == null) return error("Thiếu độc giả.");
            DocumentReference userRef = db.collection("Users").document(readerUid);
            return db.runTransaction(tx -> {
                DocumentSnapshot report = tx.get(reportRef);
                DocumentSnapshot user = tx.get(userRef);
                if (!report.exists() || !PENDING.equals(report.getString("status"))
                        || !user.exists() || !"Reader".equals(user.getString("role")))
                    throw new IllegalArgumentException("Thông tin báo mất/độc giả đã thay đổi.");
                long locks = user.getLong("lostBookBorrowLocks") == null
                        ? 0 : user.getLong("lostBookBorrowLocks");
                if (locks < 0 || locks > 10000)
                    throw new IllegalArgumentException("Bộ đếm khóa mất sách không hợp lệ.");
                tx.update(reportRef, "status", PROCESSING, "acceptedBy", adminUid,
                        "acceptedAt", FieldValue.serverTimestamp(),
                        "updatedAt", FieldValue.serverTimestamp());
                tx.update(userRef, "lostBookBorrowLocks", locks + 1,
                        "updatedAt", FieldValue.serverTimestamp());
                return (Void) null;
            });
        });
    }

    /**
     * After staff physically receives money OR a replacement copy, close one lost
     * loan and retire its original barcode. No automatic fine/receipt is invented.
     */
    public Task<Void> complete(String reportId, String adminUid, String method) {
        if (!"Cash".equals(method) && !"Replacement".equals(method))
            return error("Phải xác nhận đã nhận tiền hoặc sách thay thế thực tế.");
        if (adminUid == null || !adminUid.equals(FirebaseAuth.getInstance().getUid()))
            return error("Phiên Admin không hợp lệ.");
        DocumentReference reportRef = db.collection(COLLECTION).document(reportId);
        return reportRef.get(Source.SERVER).onSuccessTask(snapshot -> {
            if (!snapshot.exists() || !PROCESSING.equals(snapshot.getString("status")))
                return error("Báo mất chưa được xác nhận xử lý hoặc đã hoàn tất.");
            String userId = snapshot.getString("userId");
            String requestId = snapshot.getString("requestId");
            String receiptId = snapshot.getString("receiptId");
            String copyId = snapshot.getString("copyId");
            String titleId = snapshot.getString("bookTitleId");
            if (userId == null || requestId == null || receiptId == null
                    || copyId == null || titleId == null) return error("Báo mất thiếu dữ liệu.");
            DocumentReference userRef = db.collection("Users").document(userId);
            DocumentReference requestRef = db.collection("OnlineLoanRequests").document(requestId);
            DocumentReference receiptRef = db.collection("OnlineLoanReceipts").document(receiptId);
            DocumentReference copyRef = db.collection("BookCopies").document(copyId);
            DocumentReference titleRef = db.collection("BookTitles").document(titleId);
            return db.runTransaction(tx -> {
                DocumentSnapshot report = tx.get(reportRef);
                DocumentSnapshot user = tx.get(userRef);
                DocumentSnapshot request = tx.get(requestRef);
                DocumentSnapshot receipt = tx.get(receiptRef);
                DocumentSnapshot copy = tx.get(copyRef);
                DocumentSnapshot title = tx.get(titleRef);
                Long locks = user.getLong("lostBookBorrowLocks");
                Long total = title.getLong("totalCopies");
                Long available = title.getLong("availableCopies");
                long reserved = title.getLong("reservedOnlineCopies") == null
                        ? 0 : title.getLong("reservedOnlineCopies");
                if (!PROCESSING.equals(report.getString("status"))
                        || !receipt.exists() || !"Open".equals(receipt.getString("status"))
                        || !request.exists() || !"Collected".equals(request.getString("status"))
                        || !receiptId.equals(request.getString("receiptId"))
                        || !copyId.equals(receipt.getString("copyId"))
                        || !copyId.equals(request.getString("copyId"))
                        || !copy.exists() || !"DaMuon".equals(copy.getString("copyStatus"))
                        || !titleId.equals(copy.getString("bookTitleId"))
                        || total == null || available == null
                        || total < 1 || total - 1 < available + reserved
                        || locks == null || locks < 1)
                    throw new IllegalArgumentException("Phiếu, tồn kho hoặc khóa mượn đã thay đổi.");
                tx.update(reportRef, "status", COMPLETED, "compensationMethod", method,
                        "completedBy", adminUid, "completedAt", FieldValue.serverTimestamp(),
                        "updatedAt", FieldValue.serverTimestamp());
                tx.update(requestRef, "status", "Lost",
                        "lostAt", FieldValue.serverTimestamp(),
                        "updatedAt", FieldValue.serverTimestamp());
                tx.update(receiptRef, "status", "Lost",
                        "lostAt", FieldValue.serverTimestamp(), "lostBy", adminUid);
                tx.update(copyRef, "copyStatus", "DaMat", "priorityUserId", null,
                        "updatedAt", FieldValue.serverTimestamp());
                // The copy was unavailable since checkout; do NOT decrement availableCopies again.
                tx.update(titleRef, "totalCopies", total - 1,
                        "updatedAt", FieldValue.serverTimestamp());
                // Does not touch cardStatus: manual/late-payment locks survive.
                tx.update(userRef, "lostBookBorrowLocks", locks - 1,
                        "updatedAt", FieldValue.serverTimestamp());
                return (Void) null;
            });
        });
    }
}
