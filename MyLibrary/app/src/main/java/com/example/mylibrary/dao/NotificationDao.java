package com.example.mylibrary.dao;

import com.example.mylibrary.models.Notification;
import java.util.List;

public interface NotificationDao {
    void addNotification(Notification notification, DataCallback<Notification> callback);
    void getNotificationsByCustomerId(String customerId, DataCallback<List<Notification>> callback);
    void markAsRead(String id, DataCallback<Void> callback);
}
