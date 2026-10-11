package com.example.mylibrary.dao;

import com.example.mylibrary.models.Notification;
import java.util.List;

public interface NotificationDao {
    void addNotification(Notification notification, DataCallback<Notification> callback);
    void getNotificationById(String id, DataCallback<Notification> callback);
    void getNotificationsByCustomerId(String customerId, DataCallback<List<Notification>> callback);
    void getNotificationsByAdminId(String adminId, DataCallback<List<Notification>> callback);
    void markAsRead(String id, DataCallback<Void> callback);
    void deleteNotification(String id, DataCallback<Void> callback);
}
