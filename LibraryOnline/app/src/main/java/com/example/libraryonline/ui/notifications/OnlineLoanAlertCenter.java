package com.example.libraryonline.ui.notifications;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import com.example.libraryonline.R;
import com.example.libraryonline.ui.admin.AdminHomeView;
import com.example.libraryonline.ui.admin.AdminMainActivity;
import com.example.libraryonline.ui.admin.AdminNavigation;
import com.example.libraryonline.ui.reader.ReaderHomeView;
import com.example.libraryonline.ui.reader.ReaderMainActivity;
import com.example.libraryonline.ui.reader.ReaderNavigation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

/** Optional Android alerts while a signed-in Firestore listener is active.
 * Does not provide background FCM delivery when the application is closed.
 */
public final class OnlineLoanAlertCenter {
    private static final String CHANNEL = "library_online_loans_v1";
    private static final String PREF = "online_loan_alert_seen_v1";
    private OnlineLoanAlertCenter() {}

    public static boolean enabled(Context context) {
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null
                && androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
                && (Build.VERSION.SDK_INT < 33
                    || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED);
    }

    /** Trigger only from a user-tapped button, never automatically at login. */
    public static void requestOptIn(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7461);
        } else {
            Toast.makeText(activity, enabled(activity)
                    ? "Đã bật thông báo khi ứng dụng đang hoạt động."
                    : "Thông báo đang bị tắt trong cài đặt Android.",
                    Toast.LENGTH_LONG).show();
        }
    }

    public static void registerSnapshot(
            Activity activity, String uid, boolean admin, Collection<String> eventIds) {
        if (uid == null || uid.isEmpty() || eventIds == null) return;
        SharedPreferences prefs = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String scope = scope(uid, admin);
        if (prefs.getBoolean(scope + "_initialized", false)) return;
        SharedPreferences.Editor edits = prefs.edit();
        // First authoritative snapshot is a baseline; never flood historical alerts.
        for (String id : eventIds)
            if (id != null && !id.isEmpty()) edits.putBoolean(scope + "_" + hash(id), true);
        edits.putBoolean(scope + "_initialized", true).apply();
    }

    public static void notifyNew(
            Activity activity, String uid, boolean admin, String eventId,
            String title, String body) {
        if (uid == null || uid.isEmpty() || eventId == null || eventId.isEmpty()) return;
        SharedPreferences prefs = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String scope = scope(uid, admin);
        if (!prefs.getBoolean(scope + "_initialized", false)) return;
        String key = scope + "_" + hash(eventId);
        if (prefs.getBoolean(key, false)) return;
        // Persist first: repeat snapshots or changed lifecycle states cannot alert twice.
        prefs.edit().putBoolean(key, true).apply();
        if (!enabled(activity)) return;
        NotificationManager manager = (NotificationManager)
                activity.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Yêu cầu mượn sách Online", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
        Intent intent = admin ? new Intent(activity, AdminMainActivity.class)
                : new Intent(activity, ReaderMainActivity.class);
        if (admin) intent.putExtra(AdminNavigation.EXTRA_TAB, AdminHomeView.REQUESTS);
        else intent.putExtra(ReaderNavigation.EXTRA_TAB, ReaderHomeView.NOTIFICATIONS);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int id = hash(uid + "|" + eventId).hashCode() & 0x7fffffff;
        PendingIntent target = PendingIntent.getActivity(activity, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(activity, CHANNEL)
                : new Notification.Builder(activity);
        String heading = title == null || title.trim().isEmpty() ? "Library Online" : title;
        String details = body == null ? "" : body;
        builder.setSmallIcon(R.drawable.mylibrary_logo)
                .setContentTitle(heading).setContentText(details)
                .setStyle(new Notification.BigTextStyle().bigText(details))
                .setAutoCancel(true).setContentIntent(target);
        manager.notify(id, builder.build());
    }

    private static String scope(String uid, boolean admin) {
        return (admin ? "a_" : "r_") + hash(uid);
    }

    private static String hash(String raw) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                int b = bytes[i] & 0xff;
                out.append(Character.forDigit(b >>> 4, 16));
                out.append(Character.forDigit(b & 15, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
