package com.example.mylibrary.dao;

import com.example.mylibrary.models.Notification;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class FirestoreNotificationDao implements NotificationDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "notifications";

    @Override
    public void addNotification(Notification notification, DataCallback<Notification> callback) {
        String id = notification.getId() != null ? notification.getId() : db.collection(COLLECTION).document().getId();
        notification.setId(id);
        notification.setCreatedAt(System.currentTimeMillis());
        db.collection(COLLECTION).document(id).set(notification)
                .addOnSuccessListener(unused -> callback.onSuccess(notification))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getNotificationById(String id, DataCallback<Notification> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Notification notification = doc.toObject(Notification.class);
                        if (notification != null) {
                            notification.setId(doc.getId());
                            callback.onSuccess(notification);
                        } else {
                            callback.onError(new Exception("Notification is null"));
                        }
                    } else {
                        callback.onError(new Exception("Notification not found"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getNotificationsByCustomerId(String customerId, DataCallback<List<Notification>> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("customerId", customerId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Notification> notifications = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            Notification notification = doc.toObject(Notification.class);
                            if (notification != null) {
                                notification.setId(doc.getId());
                                notifications.add(notification);
                            }
                        }
                    }
                    callback.onSuccess(notifications);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getNotificationsByAdminId(String adminId, DataCallback<List<Notification>> callback) {
        db.collection(COLLECTION)
                .whereEqualTo("adminId", adminId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Notification> notifications = new ArrayList<>();
                    if (snapshot != null) {
                        for (var doc : snapshot.getDocuments()) {
                            Notification notification = doc.toObject(Notification.class);
                            if (notification != null) {
                                notification.setId(doc.getId());
                                notifications.add(notification);
                            }
                        }
                    }
                    callback.onSuccess(notifications);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void markAsRead(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id).update("isRead", 1)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void deleteNotification(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id).delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
