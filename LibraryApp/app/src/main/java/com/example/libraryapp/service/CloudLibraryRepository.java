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
        String uid = currentUid();
        if (uid == null) throw new IllegalStateException("Bạn chưa đăng nhập.");
        Map<String, Object> data = new HashMap<>();
        data.put("customerUid", uid);
        data.put("bookId", bookCloudId);
        data.put("bookTitle", bookTitle);
        data.put("status", "pending");
        data.put("createdAt", System.currentTimeMillis());
        data.put("updatedAt", System.currentTimeMillis());
        return firestore.collection(COLLECTION_RESERVATIONS).add(data);
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

    public Task<Void> markNotificationRead(String notificationId) {
        return firestore.collection(COLLECTION_NOTIFICATIONS).document(notificationId)
                .update("isRead", true);
    }

    public Task<DocumentSnapshot> getBook(String cloudBookId) {
        return firestore.collection(COLLECTION_BOOKS).document(cloudBookId).get();
    }

    public DocumentReference bookDocument(String cloudBookId) {
        return firestore.collection(COLLECTION_BOOKS).document(cloudBookId);
    }
}
