package com.example.mylibrary.repository;

import com.example.mylibrary.dao.NotificationDao;
import com.example.mylibrary.dao.DataCallback;
import com.example.mylibrary.dao.FirestoreNotificationDao;
import com.example.mylibrary.models.Notification;
import java.util.List;

public class NotificationRepository {
    private final NotificationDao notificationDao = new FirestoreNotificationDao();

    public void addNotification(Notification notification, DataCallback<Notification> callback) {
        notificationDao.addNotification(notification, callback);
    }

    public void getNotificationsByCustomerId(String customerId, DataCallback<List<Notification>> callback) {
        notificationDao.getNotificationsByCustomerId(customerId, callback);
    }

    public void markAsRead(String id, DataCallback<Void> callback) {
        notificationDao.markAsRead(id, callback);
    }
}
