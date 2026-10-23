package com.example.libraryonline.data.remote.firebase;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Online requests use Firestore as the cross-device source of truth.
 * This class never writes a synthetic SQLite loan receipt at approval time:
 * checkout at the counter is a separate, verified business operation.
 */
public final class OnlineLoanRequestRepository {
    public static final String REQUESTS = "OnlineLoanRequests";
    public static final String NOTIFICATIONS = "OnlineLoanNotifications";
    public static final String PENDING = "Pending";
    public static final String APPROVED = "Approved";
    public static final String REJECTED = "Rejected";
    public static final String EXPIRED = "Expired";
    public static final String COLLECTED = "Collected";
    public static final String RETURNED = "Returned";
    public static final String CANCELLED = "Cancelled";
    private static final Executor DIRECT = Runnable::run;
    private static final long HOLD_MS = 24L * 60L * 60L * 1000L;

    private final FirebaseFirestore db;

    public OnlineLoanRequestRepository() { this(FirebaseFirestore.getInstance()); }
    public OnlineLoanRequestRepository(FirebaseFirestore db) { this.db = db; }

    public static String requestId(String uid, String bookId) {
        if (uid == null || uid.trim().isEmpty() || uid.contains("/")
                || bookId == null || bookId.trim().isEmpty() || bookId.contains("/"))
            throw new IllegalArgumentException("Mã độc giả/đầu sách không hợp lệ.");
        String id = uid + "_" + bookId;
        if (id.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1400)
            throw new IllegalArgumentException("Mã yêu cầu quá dài.");
        return id;
    }

    public static String label(String status) {
        if (APPROVED.equals(status)) return "Đã duyệt · Chờ đến lấy sách (24 giờ)";
        if (REJECTED.equals(status)) return "Đã từ chối";
        if (EXPIRED.equals(status)) return "Đã hết thời gian đến lấy";
        if (COLLECTED.equals(status)) return "Đã nhận sách · Đang mượn";
        if (RETURNED.equals(status)) return "Đã hoàn trả sách";
        if (CANCELLED.equals(status)) return "Bạn đã hủy yêu cầu";
        return "Chờ Admin duyệt";
    }

    public Task<Void> submit(String uid, String bookId, int loanDays) {
        if (loanDays < 1 || loanDays > 60)
            return Tasks.forException(new IllegalArgumentException("Số ngày mượn không hợp lệ."));
        FirebaseUser auth = FirebaseAuth.getInstance().getCurrentUser();
        if (auth == null || !auth.getUid().equals(uid))
            return Tasks.forException(new IllegalStateException("Phiên đăng nhập không hợp lệ."));
        final DocumentReference requestRef = db.collection(REQUESTS).document(requestId(uid, bookId));
        final DocumentReference titleRef = db.collection("BookTitles").document(bookId);
        final DocumentReference userRef = db.collection("Users").document(uid);
        final DocumentReference configRef = db.collection("SystemConfigs").document("default");

        // The old deployed Rules may deny get(requestRef) when the document
        // does not yet exist. First discover this Reader's requests with a
        // userId-constrained query. Existing requests are read again inside
        // the transaction; a new request is created without reading the
        // missing document. Strict Firestore Rules still reject duplicate
        // writes/races and validate all fields on create.
        return db.collection(REQUESTS).whereEqualTo("userId", uid).get(Source.SERVER)
                .onSuccessTask(DIRECT, matching -> {
                    boolean found = false;
                    for (DocumentSnapshot doc : matching.getDocuments()) {
                        if (requestRef.getId().equals(doc.getId())) {
                            found = true;
                            break;
                        }
                    }
                    final boolean readPrevious = found;
                    return db.runTransaction(tx -> {
                        // Firestore requires every transaction read to precede its writes.
                        DocumentSnapshot user = tx.get(userRef);
                        DocumentSnapshot title = tx.get(titleRef);
                        DocumentSnapshot config = tx.get(configRef);
                        DocumentSnapshot previous = readPrevious ? tx.get(requestRef) : null;
                        if (!user.exists() || !"Reader".equals(user.getString("role"))
                                || !"HoatDong".equals(user.getString("cardStatus")))
                            throw new IllegalArgumentException(
                                    "Thẻ độc giả chưa hoạt động hoặc đang bị khóa.");
                        int maxDays = config.exists() && config.getLong("maxLoanDays") != null
                                ? config.getLong("maxLoanDays").intValue() : 14;
                        if (loanDays > maxDays)
                            throw new IllegalArgumentException(
                                    "Vượt số ngày mượn tối đa: " + maxDays);
                        if (!title.exists() || Boolean.TRUE.equals(title.getBoolean("isHidden"))
                                || title.getLong("availableCopies") == null
                                || title.getLong("availableCopies") < 1)
                            throw new IllegalArgumentException(
                                    "Đầu sách đã ẩn hoặc hết cuốn có thể đăng ký mượn Online.");
                        Map<String, Object> changes = new HashMap<>();
                        changes.put("loanDays", loanDays);
                        changes.put("status", PENDING);
                        changes.put("updatedAt", FieldValue.serverTimestamp());
                        changes.put("createdAt", FieldValue.serverTimestamp());
                        if (!readPrevious) {
                            changes.put("requestId", requestRef.getId());
                            changes.put("userId", uid);
                            changes.put("bookTitleId", bookId);
                            changes.put("bookTitle", title.getString("title"));
                            changes.put("attempt", 1);
                            tx.set(requestRef, changes);
                        } else if (previous != null && previous.exists()
                                && (REJECTED.equals(previous.getString("status"))
                                || EXPIRED.equals(previous.getString("status"))
                                || RETURNED.equals(previous.getString("status"))
                                || CANCELLED.equals(previous.getString("status")))) {
                            long attempt = previous.getLong("attempt") == null
                                    ? 1 : previous.getLong("attempt");
                            changes.put("attempt", attempt + 1);
                            changes.put("copyId", FieldValue.delete());
                            changes.put("holdUntil", FieldValue.delete());
                            changes.put("reviewedBy", FieldValue.delete());
                            changes.put("reviewedAt", FieldValue.delete());
                            changes.put("rejectionReason", FieldValue.delete());
                            changes.put("notificationId", FieldValue.delete());
                            changes.put("collectedAt", FieldValue.delete());
                            changes.put("collectedBy", FieldValue.delete());
                            changes.put("dueAt", FieldValue.delete());
                            changes.put("returnedAt", FieldValue.delete());
                            changes.put("returnedBy", FieldValue.delete());
                            changes.put("receiptId", FieldValue.delete());
                            tx.update(requestRef, changes);
                        } else {
                            throw new IllegalArgumentException(
                                    "Đã có yêu cầu cho đầu sách này. Hãy tải lại để xem trạng thái.");
                        }
                        return (Void) null;
                    });
                });
    }

    /** Reader can withdraw an unreviewed request; no copy is held at this stage. */
    public Task<Void> cancelPending(String uid, String bookId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !uid.equals(user.getUid()))
            return Tasks.forException(new IllegalStateException("Phiên đăng nhập không hợp lệ."));
        DocumentReference ref = db.collection(REQUESTS).document(requestId(uid, bookId));
        return db.runTransaction(tx -> {
            DocumentSnapshot current = tx.get(ref);
            if (!current.exists() || !uid.equals(current.getString("userId"))
                    || !PENDING.equals(current.getString("status")))
                throw new IllegalArgumentException("Chỉ hủy được yêu cầu đang chờ Admin duyệt.");
            tx.update(ref, "status", CANCELLED, "updatedAt", FieldValue.serverTimestamp());
            return (Void) null;
        });
    }

    public Task<Void> approve(String requestId, String adminUid) {
        final DocumentReference requestRef = db.collection(REQUESTS).document(requestId);
        // Query is a candidate discovery only; the transaction rechecks the selected copy.
        return requestRef.get(Source.SERVER).onSuccessTask(DIRECT, request -> {
            if (!request.exists() || !PENDING.equals(request.getString("status")))
                return Tasks.forException(new IllegalArgumentException("Yêu cầu đã được xử lý."));
            String titleId = request.getString("bookTitleId");
            if (titleId == null)
                return Tasks.forException(new IllegalArgumentException("Thiếu đầu sách."));
            return db.collection("BookCopies").whereEqualTo("bookTitleId", titleId)
                    .get(Source.SERVER).onSuccessTask(DIRECT, copies -> {
                        DocumentSnapshot candidate = null;
                        for (DocumentSnapshot copy : copies.getDocuments()) {
                            if ("SanSang".equals(copy.getString("copyStatus"))
                                    && !Boolean.TRUE.equals(copy.getBoolean("isHidden"))
                                    && copy.get("priorityUserId") == null) {
                                candidate = copy;
                                break;
                            }
                        }
                        if (candidate == null)
                            return Tasks.forException(new IllegalArgumentException(
                                    "Không còn cuốn sẵn sàng. Chưa duyệt yêu cầu."));
                        final DocumentReference copyRef = candidate.getReference();
                        final DocumentReference titleRef =
                                db.collection("BookTitles").document(titleId);
                        final DocumentReference userRef =
                                db.collection("Users").document(request.getString("userId"));
                        return db.runTransaction(tx -> {
                            DocumentSnapshot current = tx.get(requestRef);
                            DocumentSnapshot title = tx.get(titleRef);
                            DocumentSnapshot copy = tx.get(copyRef);
                            DocumentSnapshot user = tx.get(userRef);
                            if (!PENDING.equals(current.getString("status")))
                                throw new IllegalArgumentException("Yêu cầu không còn chờ duyệt.");
                            if (!"Reader".equals(user.getString("role"))
                                    || !"HoatDong".equals(user.getString("cardStatus")))
                                throw new IllegalArgumentException("Thẻ độc giả không còn hoạt động.");
                            if (!title.exists() || Boolean.TRUE.equals(title.getBoolean("isHidden"))
                                    || title.getLong("availableCopies") == null
                                    || title.getLong("availableCopies") < 1
                                    || !"SanSang".equals(copy.getString("copyStatus"))
                                    || Boolean.TRUE.equals(copy.getBoolean("isHidden"))
                                    || copy.get("priorityUserId") != null)
                                throw new IllegalArgumentException("Sách vừa thay đổi. Hãy tải lại.");
                            String reader = current.getString("userId");
                            long attempt = current.getLong("attempt") == null
                                    ? 1 : current.getLong("attempt");
                            Date deadline = new Date(System.currentTimeMillis() + HOLD_MS);
                            Timestamp holdUntil = new Timestamp(deadline);
                            Map<String, Object> accepted = new HashMap<>();
                            accepted.put("status", APPROVED);
                            accepted.put("copyId", copyRef.getId());
                            accepted.put("holdUntil", holdUntil);
                            accepted.put("reviewedBy", adminUid);
                            accepted.put("notificationId",
                                    notification(requestRef.getId(), attempt, APPROVED).getId());
                            accepted.put("reviewedAt", FieldValue.serverTimestamp());
                            accepted.put("updatedAt", FieldValue.serverTimestamp());
                            tx.update(requestRef, accepted);
                            tx.update(copyRef, "copyStatus", "ChoLay",
                                    "priorityUserId", reader, "updatedAt", FieldValue.serverTimestamp());
                            tx.update(titleRef, "availableCopies", title.getLong("availableCopies") - 1,
                                    "updatedAt", FieldValue.serverTimestamp());
                            tx.set(notification(requestRef.getId(), attempt, APPROVED),
                                    notice(requestRef.getId(), reader, APPROVED, attempt,
                                            "Yêu cầu mượn sách đã được duyệt",
                                            "Cuốn " + current.getString("bookTitle")
                                                    + " đã được giữ. Vui lòng đến thư viện trong 24 giờ."));
                            return (Void) null;
                        });
                    });
        });
    }

    public Task<Void> reject(String requestId, String adminUid, String reason) {
        if (reason == null || reason.trim().length() < 5 || reason.trim().length() > 500)
            return Tasks.forException(
                    new IllegalArgumentException("Lý do từ chối phải có từ 5 đến 500 ký tự."));
        final DocumentReference requestRef = db.collection(REQUESTS).document(requestId);
        return db.runTransaction(tx -> {
            DocumentSnapshot request = tx.get(requestRef);
            if (!request.exists() || !PENDING.equals(request.getString("status")))
                throw new IllegalArgumentException("Yêu cầu đã được xử lý.");
            String reader = request.getString("userId");
            long attempt = request.getLong("attempt") == null ? 1 : request.getLong("attempt");
            Map<String, Object> update = new HashMap<>();
            update.put("status", REJECTED);
            update.put("rejectionReason", reason.trim());
            update.put("reviewedBy", adminUid);
            update.put("notificationId",
                    notification(requestId, attempt, REJECTED).getId());
            update.put("reviewedAt", FieldValue.serverTimestamp());
            update.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(requestRef, update);
            tx.set(notification(requestId, attempt, REJECTED),
                    notice(requestId, reader, REJECTED, attempt,
                            "Yêu cầu mượn sách chưa được duyệt",
                            request.getString("bookTitle") + ": " + reason.trim()));
            return (Void) null;
        });
    }

    /**
     * Expire an approved hold as a single transaction. Called when an Admin opens
     * the requests screen: it is NOT a background scheduler while all apps are closed.
     */
    public Task<Void> expire(String requestId) {
        DocumentReference requestRef = db.collection(REQUESTS).document(requestId);
        return db.runTransaction(tx -> {
            DocumentSnapshot request = tx.get(requestRef);
            if (!request.exists() || !APPROVED.equals(request.getString("status")))
                return (Void) null;
            Timestamp until = request.getTimestamp("holdUntil");
            if (until == null || until.toDate().after(new Date())) return (Void) null;
            String copyId = request.getString("copyId");
            String titleId = request.getString("bookTitleId");
            DocumentReference copyRef = db.collection("BookCopies").document(copyId);
            DocumentReference titleRef = db.collection("BookTitles").document(titleId);
            DocumentSnapshot copy = tx.get(copyRef);
            DocumentSnapshot title = tx.get(titleRef);
            if (!"ChoLay".equals(copy.getString("copyStatus"))
                    || !request.getString("userId").equals(copy.getString("priorityUserId")))
                throw new IllegalArgumentException("Sách đã thay đổi; cần đối soát trước khi hết hạn.");
            if (!title.exists() || title.getLong("availableCopies") == null
                    || title.getLong("totalCopies") == null
                    || title.getLong("availableCopies") >= title.getLong("totalCopies"))
                throw new IllegalArgumentException("Tồn kho không khớp; không tự động chỉnh sửa.");
            long attempt = request.getLong("attempt") == null ? 1 : request.getLong("attempt");
            tx.update(requestRef, "status", EXPIRED, "updatedAt", FieldValue.serverTimestamp(),
                    "notificationId", notification(requestId, attempt, EXPIRED).getId());
            tx.update(copyRef, "copyStatus", "SanSang", "priorityUserId", null,
                    "updatedAt", FieldValue.serverTimestamp());
            tx.update(titleRef, "availableCopies", title.getLong("availableCopies") + 1,
                    "updatedAt", FieldValue.serverTimestamp());
            tx.set(notification(requestId, attempt, EXPIRED),
                    notice(requestId, request.getString("userId"), EXPIRED, attempt,
                            "Yêu cầu mượn Online đã hết hạn",
                            "Bạn chưa đến nhận sách trong 24 giờ. Cuốn sách đã được trả về kho."));
            return (Void) null;
        });
    }

    /**
     * Handover is a separate online transaction, never implicit at approval.
     * Scan/type both real barcodes at the desk before the physical book is handed over.
     * Firestore is authoritative; a local SQLite receipt is not synthesized.
     */
    public Task<Void> collect(String requestId, String adminUid,
                              String readerBarcode, String copyBarcode) {
        if (readerBarcode == null || readerBarcode.trim().isEmpty()
                || copyBarcode == null || copyBarcode.trim().isEmpty())
            return Tasks.forException(new IllegalArgumentException("Cần xác minh mã thẻ và mã cuốn."));
        DocumentReference requestRef = db.collection(REQUESTS).document(requestId);
        return requestRef.get(Source.SERVER).onSuccessTask(DIRECT, request -> {
            if (!request.exists() || !APPROVED.equals(request.getString("status")))
                return Tasks.forException(new IllegalArgumentException("Đơn không còn ở trạng thái chờ lấy."));
            String userId = request.getString("userId"), copyId = request.getString("copyId");
            if (userId == null || copyId == null)
                return Tasks.forException(new IllegalArgumentException("Đơn không đủ thông tin để giao."));
            DocumentReference userRef = db.collection("Users").document(userId);
            DocumentReference copyRef = db.collection("BookCopies").document(copyId);
            return db.runTransaction(tx -> {
                DocumentSnapshot current = tx.get(requestRef);
                DocumentSnapshot user = tx.get(userRef);
                DocumentSnapshot copy = tx.get(copyRef);
                if (!current.exists() || !APPROVED.equals(current.getString("status")))
                    throw new IllegalArgumentException("Đơn đã được xử lý. Hãy tải lại.");
                Timestamp deadline = current.getTimestamp("holdUntil");
                long now = System.currentTimeMillis();
                if (deadline == null || now >= deadline.toDate().getTime())
                    throw new IllegalArgumentException("Đơn đã quá hạn nhận. Cần giải phóng sách.");
                if (!user.exists() || !"Reader".equals(user.getString("role"))
                        || !"HoatDong".equals(user.getString("cardStatus"))
                        || !readerBarcode.trim().equals(user.getString("barcode")))
                    throw new IllegalArgumentException("Thẻ độc giả không khớp hoặc không còn hiệu lực.");
                String expiry = user.getString("cardExpiryDate");
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                        java.util.Locale.ROOT).format(new Date(now));
                if (expiry != null && !expiry.trim().isEmpty() && expiry.compareTo(today) < 0)
                    throw new IllegalArgumentException("Thẻ độc giả đã hết hạn.");
                if (!copy.exists() || !"ChoLay".equals(copy.getString("copyStatus"))
                        || Boolean.TRUE.equals(copy.getBoolean("isHidden"))
                        || !userId.equals(copy.getString("priorityUserId"))
                        || !copyId.equals(current.getString("copyId"))
                        || !current.getString("bookTitleId").equals(copy.getString("bookTitleId"))
                        || !copyBarcode.trim().equals(copy.getString("barcode")))
                    throw new IllegalArgumentException("Mã cuốn hoặc quyền giữ sách không khớp.");
                Long days = current.getLong("loanDays");
                if (days == null || days < 1 || days > 60)
                    throw new IllegalArgumentException("Thời hạn mượn không hợp lệ.");
                long attempt = current.getLong("attempt") == null ? 1 : current.getLong("attempt");
                String receiptId = requestId + "_v" + attempt;
                DocumentReference receiptRef =
                        db.collection("OnlineLoanReceipts").document(receiptId);
                if (tx.get(receiptRef).exists())
                    throw new IllegalArgumentException("Phiếu giao sách đã tồn tại.");
                Timestamp dueAt = new Timestamp(new Date(now + days * 86_400_000L));
                Map<String, Object> collected = new HashMap<>();
                collected.put("status", COLLECTED);
                collected.put("receiptId", receiptId);
                collected.put("collectedBy", adminUid);
                collected.put("collectedAt", FieldValue.serverTimestamp());
                collected.put("dueAt", dueAt);
                collected.put("updatedAt", FieldValue.serverTimestamp());
                collected.put("notificationId", notification(requestId, attempt, COLLECTED).getId());
                tx.update(requestRef, collected);
                tx.update(copyRef, "copyStatus", "DaMuon", "priorityUserId", null,
                        "updatedAt", FieldValue.serverTimestamp());
                Map<String, Object> receipt = new HashMap<>();
                receipt.put("requestId", requestId);
                receipt.put("userId", userId);
                receipt.put("bookTitleId", current.getString("bookTitleId"));
                receipt.put("bookTitle", current.getString("bookTitle"));
                receipt.put("copyId", copyId);
                receipt.put("loanDays", days);
                receipt.put("attempt", attempt);
                receipt.put("status", "Open");
                receipt.put("collectedBy", adminUid);
                receipt.put("collectedAt", FieldValue.serverTimestamp());
                receipt.put("dueAt", dueAt);
                receipt.put("returnedAt", null);
                receipt.put("returnedBy", null);
                tx.set(receiptRef, receipt);
                tx.set(notification(requestId, attempt, COLLECTED),
                        notice(requestId, userId, COLLECTED, attempt, "Đã nhận sách Online",
                                "Đã giao " + current.getString("bookTitle")
                                        + ". Hạn trả: " + new java.text.SimpleDateFormat(
                                        "dd/MM/yyyy HH:mm", new java.util.Locale("vi", "VN"))
                                        .format(dueAt.toDate()) + "."));
                return (Void) null;
            });
        });
    }

    /** Return a book previously checked out Online, with both real barcodes verified. */
    public Task<Void> returnOnline(String requestId, String adminUid,
                                   String readerBarcode, String copyBarcode) {
        if (readerBarcode == null || readerBarcode.trim().isEmpty()
                || copyBarcode == null || copyBarcode.trim().isEmpty())
            return Tasks.forException(new IllegalArgumentException("Cần xác minh mã thẻ và mã cuốn."));
        DocumentReference requestRef = db.collection(REQUESTS).document(requestId);
        return requestRef.get(Source.SERVER).onSuccessTask(DIRECT, request -> {
            if (!request.exists() || !COLLECTED.equals(request.getString("status")))
                return Tasks.forException(new IllegalArgumentException("Đơn không có sách đang mượn."));
            String userId = request.getString("userId"), copyId = request.getString("copyId");
            String titleId = request.getString("bookTitleId");
            if (userId == null || copyId == null || titleId == null)
                return Tasks.forException(new IllegalArgumentException("Thiếu thông tin cuốn sách."));
            DocumentReference userRef = db.collection("Users").document(userId);
            DocumentReference copyRef = db.collection("BookCopies").document(copyId);
            DocumentReference titleRef = db.collection("BookTitles").document(titleId);
            return db.runTransaction(tx -> {
                DocumentSnapshot current = tx.get(requestRef);
                DocumentSnapshot user = tx.get(userRef);
                DocumentSnapshot copy = tx.get(copyRef);
                DocumentSnapshot title = tx.get(titleRef);
                if (!current.exists() || !COLLECTED.equals(current.getString("status")))
                    throw new IllegalArgumentException("Đơn đã trả hoặc thay đổi. Hãy tải lại.");
                String receiptId = current.getString("receiptId");
                if (receiptId == null)
                    throw new IllegalArgumentException("Thiếu phiếu giao Online để đối soát.");
                DocumentReference receiptRef =
                        db.collection("OnlineLoanReceipts").document(receiptId);
                DocumentSnapshot receipt = tx.get(receiptRef);
                if (!receipt.exists() || !"Open".equals(receipt.getString("status"))
                        || !requestId.equals(receipt.getString("requestId"))
                        || !copyId.equals(receipt.getString("copyId")))
                    throw new IllegalArgumentException("Phiếu mượn Online không khớp.");
                if (!user.exists() || !readerBarcode.trim().equals(user.getString("barcode")))
                    throw new IllegalArgumentException("Mã thẻ độc giả không trùng hồ sơ.");
                if (!copy.exists() || !"DaMuon".equals(copy.getString("copyStatus"))
                        || Boolean.TRUE.equals(copy.getBoolean("isHidden"))
                        || !titleId.equals(copy.getString("bookTitleId"))
                        || !copyId.equals(current.getString("copyId"))
                        || !copyBarcode.trim().equals(copy.getString("barcode")))
                    throw new IllegalArgumentException(
                            "Mã cuốn không khớp hoặc sách đã ẩn. Cần đối soát trước khi trả.");
                if (!title.exists() || Boolean.TRUE.equals(title.getBoolean("isHidden"))
                        || title.getLong("availableCopies") == null
                        || title.getLong("totalCopies") == null
                        || title.getLong("availableCopies") >= title.getLong("totalCopies"))
                    throw new IllegalArgumentException(
                            "Tồn kho không khớp hoặc đầu sách đã ẩn. Không tự động ghi trả.");
                long attempt = current.getLong("attempt") == null ? 1 : current.getLong("attempt");
                tx.update(requestRef, "status", RETURNED, "returnedBy", adminUid,
                        "returnedAt", FieldValue.serverTimestamp(),
                        "updatedAt", FieldValue.serverTimestamp(), "notificationId",
                        notification(requestId, attempt, RETURNED).getId());
                tx.update(copyRef, "copyStatus", "SanSang", "priorityUserId", null,
                        "updatedAt", FieldValue.serverTimestamp());
                tx.update(titleRef, "availableCopies", title.getLong("availableCopies") + 1,
                        "updatedAt", FieldValue.serverTimestamp());
                tx.update(receiptRef, "status", RETURNED,
                        "returnedAt", FieldValue.serverTimestamp(),
                        "returnedBy", adminUid);
                tx.set(notification(requestId, attempt, RETURNED),
                        notice(requestId, userId, RETURNED, attempt,
                                "Thư viện đã nhận trả sách Online",
                                "Đã xác nhận trả " + current.getString("bookTitle")
                                        + ". Nếu trả quá hạn, vui lòng liên hệ thủ thư để đối soát phí."));
                return (Void) null;
            });
        });
    }

    public Task<Void> markNotificationRead(String notificationId) {
        return db.collection(NOTIFICATIONS).document(notificationId)
                .update("readAt", FieldValue.serverTimestamp());
    }

    private DocumentReference notification(String requestId, long attempt, String status) {
        return db.collection(NOTIFICATIONS)
                .document(requestId + "_v" + attempt + "_" + status);
    }

    private Map<String, Object> notice(String requestId, String userId, String status,
                                        long attempt, String title, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", requestId);
        data.put("userId", userId);
        data.put("status", status);
        data.put("attempt", attempt);
        data.put("title", title);
        // Notification Rules cap the body at 500 characters; avoid rolling back
        // an otherwise valid stock transaction because of an unusually long title.
        data.put("message", message.length() > 500 ? message.substring(0, 500) : message);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("readAt", null);
        return data;
    }
}
