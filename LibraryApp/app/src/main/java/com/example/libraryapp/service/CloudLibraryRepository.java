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

    public Task<DocumentSnapshot> getBook(String cloudBookId) {
        return firestore.collection(COLLECTION_BOOKS).document(cloudBookId).get();
    }

    public DocumentReference bookDocument(String cloudBookId) {
        return firestore.collection(COLLECTION_BOOKS).document(cloudBookId);
    }
}
