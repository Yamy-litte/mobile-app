package com.example.mylibrary.dao;

import com.example.mylibrary.models.Notification;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirestoreNotificationDao implements NotificationDao {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String COLLECTION = "notifications";

    @Override
    public void addNotification(Notification notification, DataCallback<Notification> callback) {
        db.collection(COLLECTION).add(notification)
                .addOnSuccessListener(doc -> {
                    notification.setId(doc.getId());
                    callback.onSuccess(notification);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void getNotificationsByCustomerId(String customerId, DataCallback<List<Notification>> callback) {
        db.collection(COLLECTION).whereEqualTo("customerId", customerId).get()
                .addOnSuccessListener(query -> {
                    List<Notification> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Notification notif = doc.toObject(Notification.class);
                        notif.setId(doc.getId());
                        list.add(notif);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    @Override
    public void markAsRead(String id, DataCallback<Void> callback) {
        db.collection(COLLECTION).document(id).update("isRead", 1)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
}
