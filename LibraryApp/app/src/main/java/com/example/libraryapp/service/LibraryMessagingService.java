package com.example.libraryapp.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.example.libraryapp.R;
import com.example.libraryapp.ReaderNotificationsActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/** Receives FCM push messages and displays them while the app is in the foreground. */
public class LibraryMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "library_updates";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            new CloudLibraryRepository().saveCurrentDeviceToken(token)
                    .addOnFailureListener(error -> android.util.Log.e("FCM", "Unable to refresh device token", error));
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        String title = message.getNotification() != null && message.getNotification().getTitle() != null
                ? message.getNotification().getTitle() : message.getData().get("title");
        String body = message.getNotification() != null && message.getNotification().getBody() != null
                ? message.getNotification().getBody() : message.getData().get("body");
        showNotification(title == null ? "MyLibrary" : title, body == null ? "Bạn có thông báo mới." : body);
    }

    private void showNotification(String title, String body) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Thông báo thư viện", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Thông báo về yêu cầu mượn và trạng thái sách");
            manager.createNotificationChannel(channel);
        }
        Intent intent = new Intent(this, ReaderNotificationsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();
        manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), notification);
    }
}
