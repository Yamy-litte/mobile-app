package com.example.libraryapp.service;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.example.libraryapp.R;
import com.example.libraryapp.ReaderNotificationsActivity;

/** Shows a device-local notification for new Firestore notifications while the Reader app is open. */
public final class LocalLibraryNotification {
    private static final String CHANNEL_ID = "library_updates";

    private LocalLibraryNotification() {}

    public static void show(Context context, String title, String body, String notificationId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Thông báo thư viện", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Thông báo về yêu cầu mượn và trạng thái sách");
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, ReaderNotificationsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, notificationId == null ? 1001 : notificationId.hashCode(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title == null || title.trim().isEmpty() ? "Library Online" : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body == null ? "" : body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat.from(context).notify(
                notificationId == null ? (int) (System.currentTimeMillis() & 0x7fffffff)
                        : Math.abs(notificationId.hashCode()), notification.build());
    }
}
