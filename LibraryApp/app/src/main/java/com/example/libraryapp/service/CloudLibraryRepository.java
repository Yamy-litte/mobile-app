package com.example.libraryapp.service;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
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

    public Task<DocumentReference> submitReservation(String bookCloudId, String bookTitle) {
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
                .orderBy("title", Query.Direction.ASCENDING)
                .addSnapshotListener(listener);
    }

    public ListenerRegistration listenMyReservations(
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        String uid = currentUid();
        if (uid == null) throw new IllegalStateException("Bạn chưa đăng nhập.");
        return firestore.collection(COLLECTION_RESERVATIONS)
                .whereEqualTo("customerUid", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
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
        return firestore.collection(COLLECTION_NOTIFICATIONS)
                .whereEqualTo("customerUid", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener(listener);
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
        java.util.List<Task<?>> operations = new java.util.ArrayList<>();
        for (String[] row : rows) {
            final String[] item = row;
            operations.add(firestore.collection(COLLECTION_BOOKS).document(item[0]).get()
                    .continueWithTask(result -> {
                        if (!result.isSuccessful()) {
                            Exception error = result.getException();
                            throw error == null ? new IllegalStateException("Không đọc được danh mục sách.") : error;
                        }
                        if (result.getResult().exists()) return com.google.android.gms.tasks.Tasks.forResult(null);
                        Map<String, Object> book = new HashMap<>();
                        book.put("localBookId", item[0]);
                        book.put("title", item[1]);
                        book.put("author", item[2]);
                        book.put("publisher", item[3]);
                        book.put("publishYear", Integer.parseInt(item[4]));
                        book.put("pages", Integer.parseInt(item[5]));
                        book.put("category", item[6]);
                        book.put("summary", item[7]);
                        book.put("totalQuantity", Integer.parseInt(item[8]));
                        book.put("availableQuantity", Integer.parseInt(item[9]));
                        book.put("active", true);
                        book.put("updatedAt", System.currentTimeMillis());
                        return firestore.collection(COLLECTION_BOOKS).document(item[0]).set(book);
                    }));
        }
        return com.google.android.gms.tasks.Tasks.whenAll(operations);
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

    public ListenerRegistration listenMyBorrowRecords(
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        String uid = currentUid();
        if (uid == null) throw new IllegalStateException("Bạn chưa đăng nhập.");
        return firestore.collection(COLLECTION_BORROW_RECORDS)
                .whereEqualTo("customerUid", uid)
                .addSnapshotListener(listener);
    }


    /** Resolve the cloud document through the explicit localBookId mapping field. */
    public Task<com.google.firebase.firestore.QuerySnapshot> findBookByLocalId(String localBookId) {
        return firestore.collection(COLLECTION_BOOKS)
                .whereEqualTo("localBookId", localBookId)
                .limit(1)
                .get();
    }

    public ListenerRegistration listenBookByLocalId(
            String localBookId,
            com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        return firestore.collection(COLLECTION_BOOKS)
                .whereEqualTo("localBookId", localBookId)
                .limit(1)
                .addSnapshotListener(listener);
    }

    public Task<DocumentSnapshot> getBook(String cloudBookId) {
        return firestore.collection(COLLECTION_BOOKS).document(cloudBookId).get();
    }

    public DocumentReference bookDocument(String cloudBookId) {
        return firestore.collection(COLLECTION_BOOKS).document(cloudBookId);
    }
}
