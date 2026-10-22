package com.example.libraryonline.data.remote.firebase;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
        return db.runTransaction(tx -> {
            // All reads occur before any write in this Firestore transaction.
            DocumentSnapshot user = tx.get(userRef);
            DocumentSnapshot title = tx.get(titleRef);
            DocumentSnapshot config = tx.get(configRef);
            DocumentSnapshot previous = tx.get(requestRef);
            if (!user.exists() || !"Reader".equals(user.getString("role"))
                    || !"HoatDong".equals(user.getString("cardStatus")))
                throw new IllegalArgumentException("Thẻ độc giả chưa hoạt động hoặc đang bị khóa.");
            int maxDays = config.exists() && config.getLong("maxLoanDays") != null
                    ? config.getLong("maxLoanDays").intValue() : 14;
            if (loanDays > maxDays)
                throw new IllegalArgumentException("Vượt số ngày mượn tối đa: " + maxDays);
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
            if (!previous.exists()) {
                changes.put("requestId", requestRef.getId());
                changes.put("userId", uid);
                changes.put("bookTitleId", bookId);
                changes.put("bookTitle", title.getString("title"));
                changes.put("attempt", 1);
                tx.set(requestRef, changes);
            } else if (REJECTED.equals(previous.getString("status"))
                    || EXPIRED.equals(previous.getString("status"))) {
                long attempt = previous.getLong("attempt") == null ? 1 : previous.getLong("attempt");
                changes.put("attempt", attempt + 1);
                changes.put("copyId", FieldValue.delete());
                changes.put("holdUntil", FieldValue.delete());
                changes.put("reviewedBy", FieldValue.delete());
                changes.put("reviewedAt", FieldValue.delete());
                changes.put("rejectionReason", FieldValue.delete());
                tx.update(requestRef, changes);
            } else {
                throw new IllegalArgumentException(
                        "Bạn đã có yêu cầu mượn đầu sách này đang chờ hoặc đã duyệt.");
            }
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
            tx.update(requestRef, "status", EXPIRED, "updatedAt", FieldValue.serverTimestamp());
            tx.update(copyRef, "copyStatus", "SanSang", "priorityUserId", null,
                    "updatedAt", FieldValue.serverTimestamp());
            tx.update(titleRef, "availableCopies", title.getLong("availableCopies") + 1,
                    "updatedAt", FieldValue.serverTimestamp());
            long attempt = request.getLong("attempt") == null ? 1 : request.getLong("attempt");
            tx.set(notification(requestId, attempt, EXPIRED),
                    notice(requestId, request.getString("userId"), EXPIRED, attempt,
                            "Yêu cầu mượn Online đã hết hạn",
                            "Bạn chưa đến nhận sách trong 24 giờ. Cuốn sách đã được trả về kho."));
            return (Void) null;
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
        data.put("message", message);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("readAt", null);
        return data;
    }
}
