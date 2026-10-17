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
        return upsertReaderProfile(uid, email, fullName, null, null);
    }

    /** Syncs the Reader's profile fields while preserving existing values not supplied by the caller. */
    public Task<Void> upsertReaderProfile(String uid, String email, String fullName,
                                          String phoneNumber, String avatarUrl) {
        if (isBlank(uid)) throw new IllegalArgumentException("UID độc giả không hợp lệ.");
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", uid);
        if (email != null) profile.put("email", email.trim());
        if (fullName != null) profile.put("fullName", fullName.trim());
        if (phoneNumber != null) profile.put("phoneNumber", phoneNumber.trim());
        if (avatarUrl != null) profile.put("avatarUrl", avatarUrl.trim());
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
        // Keep the document identity and barcode value explicit and canonical.
        // This also allows a newly created profile document to pass rules requiring uid.
        update.put("uid", uid);
        update.put("libraryCardNumber", cardNumber.trim());
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


    /** Writes an explicit Admin edit, including quantities, to the matching cloud document. */
    public Task<Void> syncAdminBook(Book book) {
        if (book == null || book.getId() <= 0) {
            throw new IllegalArgumentException("Mã sách không hợp lệ.");
        }
        String id = String.valueOf(book.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("localBookId", id);
        data.put("title", book.getTitle());
        data.put("author", book.getAuthor());
        data.put("publisher", book.getPublisher());
        data.put("publishYear", book.getPublishYear());
        data.put("pages", book.getPages());
        data.put("summary", book.getSummary());
        data.put("imageUrl", book.getImageUrl() == null ? "" : book.getImageUrl());
        data.put("categoryId", book.getCategoryId());
        data.put("locationId", book.getLocationId());
        data.put("totalQuantity", book.getTotalQuantity());
        data.put("availableQuantity", book.getAvailableQuantity());
        data.put("active", book.isActive());
        data.put("updatedAt", System.currentTimeMillis());
        return firestore.collection(COLLECTION_BOOKS).document(id)
                .set(data, SetOptions.merge());
    }

    /** Listen to the complete shared catalogue for Admin inventory refresh. */
    public ListenerRegistration listenAllBooks(
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        return firestore.collection(COLLECTION_BOOKS).addSnapshotListener(listener);
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
                // Keep the local catalogue relationships available to the cloud clients too.
                data.put("categoryId", localBook.getCategoryId());
                data.put("locationId", localBook.getLocationId());
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
        if (isBlank(reservationId) || isBlank(adminUid)) {
            throw new IllegalArgumentException("Thiếu mã yêu cầu hoặc tài khoản Admin.");
        }
        final DocumentReference reservationRef =
                firestore.collection(COLLECTION_RESERVATIONS).document(reservationId);
        final DocumentReference notificationRef =
                firestore.collection(COLLECTION_NOTIFICATIONS).document();
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot request = transaction.get(reservationRef);
            if (!request.exists()) throw new IllegalStateException("Không tìm thấy yêu cầu mượn.");
            if (!"pending".equalsIgnoreCase(request.getString("status"))) {
                throw new IllegalStateException("Yêu cầu này đã được xử lý.");
            }
            String customerUid = request.getString("customerUid");
            if (isBlank(customerUid)) throw new IllegalStateException("Yêu cầu thiếu UID độc giả.");
            long now = System.currentTimeMillis();
            String requestType = request.getString("requestType");
            boolean preorder = "preorder".equalsIgnoreCase(requestType);
            java.util.List<String> bookIds = new java.util.ArrayList<>();
            java.util.List<String> bookTitles = new java.util.ArrayList<>();
            Object rawIds = request.get("bookIds");
            Object rawTitles = request.get("bookTitles");
            if (rawIds instanceof java.util.List) {
                for (Object value : (java.util.List<?>) rawIds) if (value != null) bookIds.add(String.valueOf(value));
                if (rawTitles instanceof java.util.List) {
                    for (Object value : (java.util.List<?>) rawTitles) if (value != null) bookTitles.add(String.valueOf(value));
                }
            } else {
                String id = request.getString("bookId");
                if (!isBlank(id)) bookIds.add(id);
                String title = request.getString("bookTitle");
                if (!isBlank(title)) bookTitles.add(title);
            }
            if (bookIds.isEmpty()) throw new IllegalStateException("Yêu cầu chưa có danh sách sách.");

            java.util.List<DocumentReference> bookRefs = new java.util.ArrayList<>();
            java.util.List<DocumentSnapshot> bookDocs = new java.util.ArrayList<>();
            if (approve && !preorder) {
                for (String bookId : bookIds) {
                    DocumentReference bookRef = firestore.collection(COLLECTION_BOOKS).document(bookId);
                    bookRefs.add(bookRef);
                    bookDocs.add(transaction.get(bookRef));
                }
                for (int i = 0; i < bookDocs.size(); i++) {
                    DocumentSnapshot book = bookDocs.get(i);
                    if (!book.exists()) throw new IllegalStateException("Không tìm thấy sách trong kho.");
                    Long available = book.getLong("availableQuantity");
                    if (available == null || available <= 0L) {
                        throw new IllegalStateException("Một cuốn trong yêu cầu đã hết bản có thể mượn.");
                    }
                }
            }

            if (approve && !preorder) {
                Long pickupAtValue = request.getLong("pickupAt");
                Long dueAtValue = request.getLong("dueAt");
                long pickupAt = pickupAtValue == null ? now : pickupAtValue;
                long dueAt = dueAtValue == null ? pickupAt + 14L * 86400000L : dueAtValue;
                if (dueAt <= pickupAt) throw new IllegalStateException("Ngày trả phải sau ngày nhận.");
                for (int i = 0; i < bookRefs.size(); i++) {
                    DocumentSnapshot book = bookDocs.get(i);
                    long available = book.getLong("availableQuantity");
                    String title = i < bookTitles.size() ? bookTitles.get(i) : book.getString("title");
                    DocumentReference borrowRef = firestore.collection(COLLECTION_BORROW_RECORDS).document();
                    Map<String, Object> borrow = new HashMap<>();
                    borrow.put("customerUid", customerUid);
                    borrow.put("bookId", bookIds.get(i));
                    borrow.put("bookTitle", title == null ? "Sách" : title);
                    borrow.put("reservationId", reservationId);
                    borrow.put("adminUid", adminUid);
                    borrow.put("status", "awaiting_pickup");
                    borrow.put("pickupAt", pickupAt);
                    borrow.put("dueAt", dueAt);
                    borrow.put("borrowedAt", 0L);
                    borrow.put("renewalCount", 0L);
                    borrow.put("createdAt", now);
                    transaction.set(borrowRef, borrow);
                    transaction.update(bookRefs.get(i), "availableQuantity", available - 1L);
                }
            }

            String status = approve ? "approved" : "rejected";
            Map<String, Object> requestUpdate = new HashMap<>();
            requestUpdate.put("status", status);
            requestUpdate.put("reviewedByUid", adminUid);
            requestUpdate.put("reviewedAt", now);
            requestUpdate.put("updatedAt", now);
            transaction.update(reservationRef, requestUpdate);

            Map<String, Object> notification = new HashMap<>();
            notification.put("customerUid", customerUid);
            notification.put("title", approve
                    ? (preorder ? "Đặt trước đã được duyệt" : "Yêu cầu mượn đã được duyệt")
                    : "Yêu cầu mượn bị từ chối");
            notification.put("content", approve
                    ? (preorder
                        ? "Yêu cầu đặt trước đã được ghi nhận. Bạn sẽ được thông báo khi có sách."
                        : "Yêu cầu mượn đã được duyệt. Sách đã được giữ chỗ, vui lòng đến thư viện để nhận.")
                    : "Yêu cầu mượn/đặt trước đã bị Admin từ chối.");
            notification.put("action", preorder ? "preorder_status" : "pickup_books");
            notification.put("reservationId", reservationId);
            notification.put("isRead", false);
            notification.put("createdAt", now);
            transaction.set(notificationRef, notification);
            return null;
        });
    }

    /** Admin confirms that the Reader physically received a book reserved by an approved request. */
    public Task<Void> confirmBorrowPickup(String borrowRecordId, String adminUid) {
        if (isBlank(borrowRecordId) || isBlank(adminUid)) {
            throw new IllegalArgumentException("Thiếu mã phiếu hoặc tài khoản Admin.");
        }
        DocumentReference ref = firestore.collection(COLLECTION_BORROW_RECORDS).document(borrowRecordId);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot record = transaction.get(ref);
            if (!record.exists()) throw new IllegalStateException("Không tìm thấy phiếu mượn.");
            if (!"awaiting_pickup".equalsIgnoreCase(record.getString("status"))) {
                throw new IllegalStateException("Phiếu không ở trạng thái chờ nhận sách.");
            }
            long now = System.currentTimeMillis();
            transaction.update(ref, "status", "borrowed",
                    "borrowedAt", now, "handedOverByUid", adminUid, "updatedAt", now);
            String customerUid = record.getString("customerUid");
            if (!isBlank(customerUid)) {
                DocumentReference notificationRef = firestore.collection(COLLECTION_NOTIFICATIONS).document();
                Map<String, Object> notification = new HashMap<>();
                notification.put("customerUid", customerUid);
                notification.put("title", "Đã nhận sách");
                notification.put("content", "Admin đã xác nhận giao sách và ghi nhận phiếu mượn.");
                notification.put("isRead", false);
                notification.put("createdAt", now);
                transaction.set(notificationRef, notification);
            }
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

    /** Live list of active loans for the Admin Home loan-management view. */
    public ListenerRegistration listenActiveBorrowRecords(
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        return firestore.collection(COLLECTION_BORROW_RECORDS)
                .whereIn("status", java.util.Arrays.asList("borrowed", "awaiting_pickup"))
                .addSnapshotListener(listener);
    }

    public ListenerRegistration listenMyBorrowRecords(
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        String uid = currentUid();
        if (uid == null) throw new IllegalStateException("Bạn chưa đăng nhập.");
        return firestore.collection(COLLECTION_BORROW_RECORDS)
                .whereEqualTo("customerUid", uid)
                .addSnapshotListener(listener);
    }


    /** Finds a Reader profile using the stable barcode printed on the digital library card. */
    public Task<com.google.firebase.firestore.QuerySnapshot> findReaderByCardNumber(String cardNumber) {
        if (isBlank(cardNumber)) throw new IllegalArgumentException("Mã thẻ không hợp lệ.");
        String normalized = cardNumber.trim();
        // First use the canonical digital-card field. If an older profile stored the
        // same card value under a legacy field, try those fields without guessing a UID.
        return firestore.collection(COLLECTION_CUSTOMERS)
                .whereEqualTo("libraryCardNumber", normalized)
                .limit(2)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception error = task.getException();
                        throw error == null ? new IllegalStateException("Không tra cứu được mã thẻ.") : error;
                    }
                    if (!task.getResult().isEmpty()) return com.google.android.gms.tasks.Tasks.forResult(task.getResult());
                    return firestore.collection(COLLECTION_CUSTOMERS)
                            .whereEqualTo("cardNumber", normalized)
                            .limit(2)
                            .get()
                            .continueWithTask(legacyTask -> {
                                if (!legacyTask.isSuccessful()) {
                                    Exception error = legacyTask.getException();
                                    throw error == null ? new IllegalStateException("Không tra cứu được mã thẻ.") : error;
                                }
                                if (!legacyTask.getResult().isEmpty()) return com.google.android.gms.tasks.Tasks.forResult(legacyTask.getResult());
                                return firestore.collection(COLLECTION_CUSTOMERS)
                                        .whereEqualTo("libraryCardNo", normalized)
                                        .limit(2)
                                        .get();
                            });
                });
    }

    /** Loads the scanned Reader's borrowing history without requiring a composite index. */
    public Task<com.google.firebase.firestore.QuerySnapshot> getReaderBorrowRecords(String customerUid) {
        if (isBlank(customerUid)) throw new IllegalArgumentException("Không xác định được độc giả.");
        return firestore.collection(COLLECTION_BORROW_RECORDS)
                .whereEqualTo("customerUid", customerUid)
                .get();
    }

    /**
     * Marks a borrow record as returned and restores one available copy atomically.
     * No inventory change is made unless the record is still actively borrowed.
     */
    public Task<Void> returnBorrowRecord(String borrowRecordId, String adminUid) {
        if (isBlank(borrowRecordId) || isBlank(adminUid)) {
            throw new IllegalArgumentException("Thiếu mã phiếu mượn hoặc tài khoản Admin.");
        }
        DocumentReference borrowRef = firestore.collection(COLLECTION_BORROW_RECORDS)
                .document(borrowRecordId);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot record = transaction.get(borrowRef);
            if (!record.exists()) throw new IllegalStateException("Không tìm thấy phiếu mượn.");
            if (!"borrowed".equalsIgnoreCase(record.getString("status"))) {
                throw new IllegalStateException("Phiếu này đã được trả hoặc không còn hiệu lực.");
            }
            String bookId = record.getString("bookId");
            if (isBlank(bookId)) throw new IllegalStateException("Phiếu mượn thiếu mã sách.");
            DocumentReference bookRef = firestore.collection(COLLECTION_BOOKS).document(bookId);
            DocumentSnapshot book = transaction.get(bookRef);
            if (!book.exists()) throw new IllegalStateException("Không tìm thấy sách để cập nhật tồn kho.");
            Long available = book.getLong("availableQuantity");
            Long total = book.getLong("totalQuantity");
            if (available == null) available = 0L;
            long max = total == null ? Long.MAX_VALUE : Math.max(0L, total);
            long restored = Math.min(max, available + 1L);
            long now = System.currentTimeMillis();

            Map<String, Object> update = new HashMap<>();
            update.put("status", "returned");
            update.put("returnedAt", now);
            update.put("returnedByUid", adminUid);
            update.put("updatedAt", now);
            transaction.update(borrowRef, update);
            transaction.update(bookRef, "availableQuantity", restored);

            String customerUid = record.getString("customerUid");
            if (!isBlank(customerUid)) {
                DocumentReference notificationRef = firestore.collection(COLLECTION_NOTIFICATIONS).document();
                Map<String, Object> notification = new HashMap<>();
                notification.put("customerUid", customerUid);
                notification.put("title", "Đã ghi nhận trả sách");
                notification.put("content", "Admin đã xác nhận trả sách “"
                        + (record.getString("bookTitle") == null ? "sách" : record.getString("bookTitle"))
                        + "”.");
                notification.put("isRead", false);
                notification.put("createdAt", now);
                transaction.set(notificationRef, notification);
            }
            return null;
        });
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
