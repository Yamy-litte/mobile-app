package com.example.libraryapp.service;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.example.libraryapp.model.Book;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Cloud data access for the two-device demo.
 *
 * Cloud document IDs are stable strings (Firebase UID or generated document IDs);
 * do not use SQLite integer row IDs as globally unique identifiers.
 */
public final class CloudLibraryRepository {
    public static final String COLLECTION_BOOKS = "books";
    public static final String COLLECTION_CUSTOMERS = "customers";
    public static final String COLLECTION_RESERVATIONS = "reservations";
    public static final String COLLECTION_BORROW_RECORDS = "borrowRecords";
    public static final String COLLECTION_NOTIFICATIONS = "notifications";

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    public CloudLibraryRepository() {
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public String currentUid() {
        return auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
    }

    public Task<Void> upsertReaderProfile(String uid, String email, String fullName) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", uid);
        profile.put("email", email);
        profile.put("fullName", fullName);
        profile.put("status", "active");
        profile.put("updatedAt", System.currentTimeMillis());
        return firestore.collection(COLLECTION_CUSTOMERS).document(uid)
                .set(profile, SetOptions.merge());
    }

    /** Stores the stable digital library-card number in the shared reader profile for Admin lookup. */
    public Task<Void> upsertReaderCardNumber(String uid, String cardNumber) {
        if (isBlank(uid) || isBlank(cardNumber)) {
            throw new IllegalArgumentException("Mã thẻ độc giả không hợp lệ.");
        }
        Map<String, Object> update = new HashMap<>();
        update.put("libraryCardNumber", cardNumber);
        update.put("updatedAt", System.currentTimeMillis());
        return firestore.collection(COLLECTION_CUSTOMERS).document(uid)
                .set(update, SetOptions.merge());
    }

    public Task<DocumentReference> submitReservation(String bookCloudId, String bookTitle) {
        return submitReservation(bookCloudId, bookTitle, false);
    }

    /** A preorder remains pending until Admin can allocate an available copy. */
    public Task<DocumentReference> submitReservation(String bookCloudId, String bookTitle, boolean preorder) {
        com.google.firebase.auth.FirebaseUser user = auth.getCurrentUser();
        if (user == null) throw new IllegalStateException("Bạn chưa đăng nhập.");
        String uid = user.getUid();
        String email = user.getEmail() == null ? "" : user.getEmail();

        Query pendingRequests = firestore.collection(COLLECTION_RESERVATIONS)
                .whereEqualTo("customerUid", uid)
                .whereEqualTo("bookId", bookCloudId)
                .whereEqualTo("status", "pending");
        return pendingRequests.get().continueWithTask(queryTask -> {
            if (!queryTask.isSuccessful()) {
                Exception error = queryTask.getException();
                throw error == null ? new IllegalStateException("Không kiểm tra được yêu cầu mượn hiện có.") : error;
            }
            if (!queryTask.getResult().isEmpty()) {
                throw new IllegalStateException("Bạn đã có yêu cầu đang chờ duyệt cho cuốn sách này.");
            }
            return firestore.collection(COLLECTION_CUSTOMERS).document(uid).get()
                    .continueWithTask(profileTask -> {
                        if (!profileTask.isSuccessful()) {
                            Exception error = profileTask.getException();
                            throw error == null ? new IllegalStateException("Không đọc được hồ sơ độc giả.") : error;
                        }
                        DocumentSnapshot profile = profileTask.getResult();
                        Map<String, Object> data = new HashMap<>();
                        data.put("customerUid", uid);
                        data.put("customerEmail", email);
                        data.put("customerName", profile == null ? "" : profile.getString("fullName"));
                        data.put("bookId", bookCloudId);
                        data.put("bookTitle", bookTitle);
                        data.put("status", "pending");
                        data.put("requestType", preorder ? "preorder" : "borrow");
                        data.put("createdAt", System.currentTimeMillis());
                        data.put("updatedAt", System.currentTimeMillis());
                        return firestore.collection(COLLECTION_RESERVATIONS).add(data);
                    });
        });
    }

    public ListenerRegistration listenAvailableBooks(
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        return firestore.collection(COLLECTION_BOOKS)
                .whereEqualTo("active", true)
                .addSnapshotListener(listener);
    }

    public ListenerRegistration listenMyReservations(
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        String uid = currentUid();
        if (uid == null) throw new IllegalStateException("Bạn chưa đăng nhập.");
        // Avoid requiring a composite index just to show the Reader's requests.
        return firestore.collection(COLLECTION_RESERVATIONS)
                .whereEqualTo("customerUid", uid)
                .addSnapshotListener(listener);
    }

    public ListenerRegistration listenPendingReservations(
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        return firestore.collection(COLLECTION_RESERVATIONS)
                .whereEqualTo("status", "pending")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener(listener);
    }

    public Task<Void> setReservationStatus(String reservationId, String status, String adminUid) {
        if (!"approved".equals(status) && !"rejected".equals(status)) {
            throw new IllegalArgumentException("Trạng thái duyệt không hợp lệ.");
        }
        Map<String, Object> update = new HashMap<>();
        update.put("status", status);
        update.put("reviewedByUid", adminUid);
        update.put("reviewedAt", System.currentTimeMillis());
        update.put("updatedAt", System.currentTimeMillis());
        return firestore.collection(COLLECTION_RESERVATIONS).document(reservationId)
                .update(update);
    }

    /** Save this device's FCM token on the signed-in Reader profile. */
    public Task<Void> saveCurrentDeviceToken(String token) {
        String uid = currentUid();
        if (uid == null) throw new IllegalStateException("Bạn chưa đăng nhập.");
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("FCM token không hợp lệ.");
        }
        Map<String, Object> update = new HashMap<>();
        // Required by Firestore rules when this profile document is first created.
        update.put("uid", uid);
        update.put("fcmToken", token);
        update.put("fcmTokenUpdatedAt", System.currentTimeMillis());
        return firestore.collection(COLLECTION_CUSTOMERS).document(uid)
                .set(update, SetOptions.merge());
    }

    /** Cancel only the signed-in Reader's own pending reservation/preorder. */
    public Task<Void> cancelReservation(String reservationId) {
        String uid = currentUid();
        if (uid == null) throw new IllegalStateException("Bạn chưa đăng nhập.");
        if (reservationId == null || reservationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã yêu cầu không hợp lệ.");
        }
        DocumentReference ref = firestore.collection(COLLECTION_RESERVATIONS).document(reservationId);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot request = transaction.get(ref);
            if (!request.exists()) throw new IllegalStateException("Không tìm thấy yêu cầu.");
            if (!uid.equals(request.getString("customerUid"))) {
                throw new SecurityException("Bạn không có quyền hủy yêu cầu này.");
            }
            if (!"pending".equalsIgnoreCase(request.getString("status"))) {
                throw new IllegalStateException("Chỉ có thể hủy yêu cầu đang chờ Admin xác nhận.");
            }
            transaction.update(ref, "status", "cancelled", "updatedAt", System.currentTimeMillis());
            return null;
        });
    }

    public Task<DocumentReference> createNotification(String customerUid, String title, String content) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("customerUid", customerUid);
        notification.put("title", title);
        notification.put("content", content);
        notification.put("isRead", false);
        notification.put("createdAt", System.currentTimeMillis());
        return firestore.collection(COLLECTION_NOTIFICATIONS).add(notification);
    }

    public ListenerRegistration listenMyNotifications(
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        String uid = currentUid();
        if (uid == null) throw new IllegalStateException("Bạn chưa đăng nhập.");
        // Filter by owner only; this query does not need a customerUid + createdAt index.
        return firestore.collection(COLLECTION_NOTIFICATIONS)
                .whereEqualTo("customerUid", uid)
                .addSnapshotListener(listener);
    }

    public Task<Void> markNotificationRead(String notificationId) {
        String uid = currentUid();
        if (uid == null) throw new IllegalStateException("Bạn chưa đăng nhập.");
        if (notificationId == null || notificationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã thông báo không hợp lệ.");
        }
        DocumentReference notificationRef =
                firestore.collection(COLLECTION_NOTIFICATIONS).document(notificationId);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot notification = transaction.get(notificationRef);
            if (!notification.exists()) {
                throw new IllegalStateException("Không tìm thấy thông báo.");
            }
            if (!uid.equals(notification.getString("customerUid"))) {
                throw new SecurityException("Bạn không có quyền cập nhật thông báo này.");
            }
            if (Boolean.TRUE.equals(notification.getBoolean("isRead"))) {
                return null;
            }
            transaction.update(notificationRef, "isRead", true);
            return null;
        });
    }




    /**
     * Seeds the shared demo catalogue without overwriting documents already edited
     * by an Admin. Call only after the Admin role has been verified.
     */
    public Task<Void> seedDemoBooksIfMissing() {
        final String[][] rows = {
                {"1", "Dế Mèn Phiêu Lưu Ký", "Tô Hoài", "Kim Đồng", "2022", "144", "Văn học", "Cuộc phiêu lưu của Dế Mèn và những bài học về tình bạn, lòng dũng cảm.", "8", "6"},
                {"2", "Cho Tôi Xin Một Vé Đi Tuổi Thơ", "Nguyễn Nhật Ánh", "Trẻ", "2021", "208", "Văn học", "Câu chuyện tuổi thơ được kể bằng góc nhìn hóm hỉnh và giàu cảm xúc.", "6", "4"},
                {"3", "Nhà Giả Kim", "Paulo Coelho", "NXB Văn Học", "2020", "228", "Văn học", "Hành trình theo đuổi ước mơ và tìm kiếm kho báu của chính mình.", "10", "7"},
                {"4", "Lược Sử Thời Gian", "Stephen Hawking", "Trẻ", "2019", "256", "Khoa học", "Giới thiệu những câu hỏi lớn về vũ trụ, thời gian và vật lý hiện đại.", "5", "3"},
                {"5", "Vũ Trụ Trong Vỏ Hạt Dẻ", "Stephen Hawking", "Trẻ", "2020", "224", "Khoa học", "Khám phá các ý tưởng nền tảng của vũ trụ học bằng cách diễn giải phổ thông.", "4", "2"},
                {"6", "Clean Code", "Robert C. Martin", "Prentice Hall", "2008", "464", "Công nghệ", "Các nguyên tắc viết mã nguồn rõ ràng, dễ bảo trì và dễ kiểm thử.", "5", "3"},
                {"7", "Đắc Nhân Tâm", "Dale Carnegie", "NXB Tổng hợp", "2022", "320", "Kỹ năng sống", "Những nguyên tắc giao tiếp và xây dựng mối quan hệ tích cực.", "12", "9"},
                {"8", "Atomic Habits", "James Clear", "NXB Thế Giới", "2020", "320", "Kỹ năng sống", "Phương pháp tạo thói quen tốt bằng những thay đổi nhỏ và bền vững.", "8", "5"}
        };

        // One collection read replaces eight separate document reads whenever Admin opens the screen.
        return firestore.collection(COLLECTION_BOOKS).get().continueWithTask(queryTask -> {
            if (!queryTask.isSuccessful()) {
                Exception error = queryTask.getException();
                throw error == null
                        ? new IllegalStateException("Không đọc được danh mục sách.")
                        : error;
            }

            java.util.Set<String> existingIds = new java.util.HashSet<>();
            for (DocumentSnapshot document : queryTask.getResult().getDocuments()) {
                existingIds.add(document.getId());
            }

            com.google.firebase.firestore.WriteBatch batch = firestore.batch();
            boolean hasChanges = false;
            Map<String, DocumentSnapshot> existingById = new HashMap<>();
            for (DocumentSnapshot document : queryTask.getResult().getDocuments()) {
                existingById.put(document.getId(), document);
            }

            for (String[] item : rows) {
                DocumentSnapshot existing = existingById.get(item[0]);
                DocumentReference bookRef = firestore.collection(COLLECTION_BOOKS).document(item[0]);
                Map<String, Object> book = new HashMap<>();

                // Repair identity/display fields on older demo documents without changing
                // quantities that an Admin may already have edited.
                if (existing == null || !existing.contains("localBookId")) book.put("localBookId", item[0]);
                if (existing == null || isBlank(existing.getString("title"))) book.put("title", item[1]);
                if (existing == null || isBlank(existing.getString("author"))) book.put("author", item[2]);
                if (existing == null || isBlank(existing.getString("publisher"))) book.put("publisher", item[3]);
                if (existing == null || existing.getLong("publishYear") == null) book.put("publishYear", Integer.parseInt(item[4]));
                if (existing == null || existing.getLong("pages") == null) book.put("pages", Integer.parseInt(item[5]));
                if (existing == null || isBlank(existing.getString("category"))) book.put("category", item[6]);
                if (existing == null || isBlank(existing.getString("summary"))) book.put("summary", item[7]);
                if (existing == null || existing.getLong("totalQuantity") == null) book.put("totalQuantity", Integer.parseInt(item[8]));
                if (existing == null || existing.getLong("availableQuantity") == null) book.put("availableQuantity", Integer.parseInt(item[9]));
                if (existing == null || existing.getBoolean("active") == null) book.put("active", true);

                if (!book.isEmpty()) {
                    book.put("updatedAt", System.currentTimeMillis());
                    batch.set(bookRef, book, SetOptions.merge());
                    hasChanges = true;
                }
            }

            return hasChanges
                    ? batch.commit()
                    : com.google.android.gms.tasks.Tasks.forResult(null);
        });
    }


    /**
     * Sync local SQLite books to the shared catalog without overwriting existing cloud inventory.
     * SQLite IDs remain stable document IDs so current reservations keep their references.
     */
    public Task<Void> syncLocalBooks(List<Book> localBooks) {
        if (localBooks == null || localBooks.isEmpty()) return com.google.android.gms.tasks.Tasks.forResult(null);
        return firestore.collection(COLLECTION_BOOKS).get().continueWithTask(queryTask -> {
            if (!queryTask.isSuccessful()) {
                Exception error = queryTask.getException();
                throw error == null ? new IllegalStateException("Không đọc được danh mục sách trên Firestore.") : error;
            }
            Map<String, DocumentSnapshot> existingById = new HashMap<>();
            for (DocumentSnapshot document : queryTask.getResult().getDocuments()) existingById.put(document.getId(), document);
            com.google.firebase.firestore.WriteBatch batch = firestore.batch();
            boolean hasChanges = false;
            for (Book localBook : localBooks) {
                if (localBook == null || localBook.getId() <= 0 || isBlank(localBook.getTitle())) continue;
                String id = String.valueOf(localBook.getId());
                DocumentSnapshot existing = existingById.get(id);
                DocumentReference ref = firestore.collection(COLLECTION_BOOKS).document(id);
                Map<String, Object> data = new HashMap<>();
                if (existing == null || !existing.contains("localBookId")) data.put("localBookId", id);
                // Admin edits update catalogue metadata, while cloud inventory quantities remain authoritative.
                data.put("title", localBook.getTitle());
                data.put("author", localBook.getAuthor());
                data.put("publisher", localBook.getPublisher());
                data.put("publishYear", localBook.getPublishYear());
                data.put("pages", localBook.getPages());
                data.put("summary", localBook.getSummary());
                data.put("imageUrl", localBook.getImageUrl() == null ? "" : localBook.getImageUrl());
                if (existing == null || existing.getLong("totalQuantity") == null) data.put("totalQuantity", localBook.getTotalQuantity());
                if (existing == null || existing.getLong("availableQuantity") == null) data.put("availableQuantity", localBook.getAvailableQuantity());
                if (existing == null || existing.getBoolean("active") == null) data.put("active", localBook.isActive());
                if (!data.isEmpty()) { data.put("updatedAt", System.currentTimeMillis()); batch.set(ref, data, SetOptions.merge()); hasChanges = true; }
            }
            return hasChanges ? batch.commit() : com.google.android.gms.tasks.Tasks.forResult(null);
        });
    }



    /** Update visibility without changing stock counts or existing reservation references. */
    public Task<Void> setBookActive(String bookCloudId, boolean active) {
        if (bookCloudId == null || bookCloudId.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã sách không hợp lệ.");
        }
        Map<String, Object> update = new HashMap<>();
        update.put("active", active);
        update.put("updatedAt", System.currentTimeMillis());
        return firestore.collection(COLLECTION_BOOKS).document(bookCloudId).set(update, SetOptions.merge());
    }

    /**
     * Reviews a cloud reservation atomically so two Admin devices cannot
     * approve the same request or allocate the same final copy.
     */
    public Task<Void> reviewReservation(String reservationId, boolean approve, String adminUid) {
        final DocumentReference reservationRef =
                firestore.collection(COLLECTION_RESERVATIONS).document(reservationId);
        final DocumentReference notificationRef =
                firestore.collection(COLLECTION_NOTIFICATIONS).document();
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot reservation = transaction.get(reservationRef);
            if (!reservation.exists()) {
                throw new IllegalStateException("Không tìm thấy yêu cầu mượn.");
            }
            if (!"pending".equals(reservation.getString("status"))) {
                throw new IllegalStateException("Yêu cầu này đã được xử lý.");
            }
            String customerUid = reservation.getString("customerUid");
            String bookId = reservation.getString("bookId");
            String bookTitle = reservation.getString("bookTitle");
            long now = System.currentTimeMillis();

            if (approve) {
                if (bookId == null || customerUid == null) {
                    throw new IllegalStateException("Yêu cầu thiếu thông tin độc giả hoặc sách.");
                }
                DocumentReference bookRef = firestore.collection(COLLECTION_BOOKS).document(bookId);
                DocumentSnapshot book = transaction.get(bookRef);
                if (!book.exists()) {
                    throw new IllegalStateException("Sách chưa được đồng bộ lên hệ thống chung.");
                }
                Long available = book.getLong("availableQuantity");
                if (available == null || available <= 0) {
                    throw new IllegalStateException("Sách đã hết bản có thể mượn.");
                }
                DocumentReference borrowRef = firestore.collection(COLLECTION_BORROW_RECORDS).document();
                Map<String, Object> borrow = new HashMap<>();
                borrow.put("customerUid", customerUid);
                borrow.put("bookId", bookId);
                borrow.put("bookTitle", bookTitle);
                borrow.put("reservationId", reservationId);
                borrow.put("adminUid", adminUid);
                borrow.put("status", "borrowed");
                borrow.put("borrowedAt", now);
                borrow.put("dueAt", now + 14L * 24L * 60L * 60L * 1000L);
                transaction.set(borrowRef, borrow);
                transaction.update(bookRef, "availableQuantity", available - 1L);
            }

            String status = approve ? "approved" : "rejected";
            Map<String, Object> reservationUpdate = new HashMap<>();
            reservationUpdate.put("status", status);
            reservationUpdate.put("reviewedByUid", adminUid);
            reservationUpdate.put("reviewedAt", now);
            reservationUpdate.put("updatedAt", now);
            transaction.update(reservationRef, reservationUpdate);

            Map<String, Object> notification = new HashMap<>();
            notification.put("customerUid", customerUid);
            notification.put("title", approve ? "Yêu cầu mượn đã được duyệt" : "Yêu cầu mượn bị từ chối");
            notification.put("content", "Yêu cầu mượn sách “" + (bookTitle == null ? "sách" : bookTitle)
                    + "” " + (approve ? "đã được duyệt. Hạn trả dự kiến sau 14 ngày."
                    : "đã bị Admin từ chối."));
            notification.put("isRead", false);
            notification.put("createdAt", now);
            notificationRef.set(notification);
            return null;
        });
    }

    /** Extend an active cloud loan by seven days, only while it is not overdue or returned. */
    public Task<Void> renewBorrowRecord(String borrowRecordId) {
        if (isBlank(borrowRecordId)) throw new IllegalArgumentException("Mã phiếu mượn không hợp lệ.");
        DocumentReference ref = firestore.collection(COLLECTION_BORROW_RECORDS).document(borrowRecordId);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot record = transaction.get(ref);
            if (!record.exists()) throw new IllegalStateException("Không tìm thấy phiếu mượn.");
            String status = record.getString("status");
            if (!"borrowed".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Chỉ có thể gia hạn sách đang mượn.");
            }
            Long dueAt = record.getLong("dueAt");
            long now = System.currentTimeMillis();
            if (dueAt == null || dueAt < now) {
                throw new IllegalStateException("Sách đã quá hạn hoặc chưa có hạn trả, không thể gia hạn trực tuyến.");
            }
            Long renewals = record.getLong("renewalCount");
            if (renewals != null && renewals >= 1L) {
                throw new IllegalStateException("Mỗi phiếu mượn chỉ được gia hạn một lần trong bản demo.");
            }
            Map<String, Object> update = new HashMap<>();
            update.put("dueAt", dueAt + 7L * 24L * 60L * 60L * 1000L);
            update.put("renewalCount", renewals == null ? 1L : renewals + 1L);
            update.put("renewedAt", now);
            transaction.update(ref, update);
            return null;
        });
    }

    public ListenerRegistration listenMyBorrowRecords(
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        String uid = currentUid();
        if (uid == null) throw new IllegalStateException("Bạn chưa đăng nhập.");
        return firestore.collection(COLLECTION_BORROW_RECORDS)
                .whereEqualTo("customerUid", uid)
                .addSnapshotListener(listener);
    }


    /** Resolve by title first, then let the caller verify the author. */
    public Task<com.google.firebase.firestore.QuerySnapshot> findBookByTitleAndAuthor(String title, String author) {
        return firestore.collection(COLLECTION_BOOKS)
                .whereEqualTo("title", title)
                .limit(10)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        Exception error = task.getException();
                        throw error == null ? new IllegalStateException("Không đọc được danh mục sách trên Firestore.") : error;
                    }
                    return task.getResult();
                });
    }

    public ListenerRegistration listenBookByTitleAndAuthor(
            String title,
            String author,
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        return firestore.collection(COLLECTION_BOOKS)
                .whereEqualTo("title", title)
                .limit(10)
                .addSnapshotListener(listener);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public Task<DocumentSnapshot> getBook(String cloudBookId) {
        return firestore.collection(COLLECTION_BOOKS).document(cloudBookId).get();
    }

    public DocumentReference bookDocument(String cloudBookId) {
        return firestore.collection(COLLECTION_BOOKS).document(cloudBookId);
    }
}
