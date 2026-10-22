package com.example.libraryonline.ui.notifications;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import com.example.libraryonline.R;
import com.example.libraryonline.data.model.LibraryNotification;
import com.example.libraryonline.data.repository.LibraryNotificationRepository;
import com.example.libraryonline.ui.reader.ReaderHomeView;
import com.example.libraryonline.ui.reader.ReaderMainActivity;
import com.example.libraryonline.ui.reader.ReaderNavigation;
import com.example.libraryonline.ui.reader.ReaderNotificationSettingsController;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Device alarms are retained without a network connection once requests have been synced. */
public final class ReaderReminderReceiver extends BroadcastReceiver {
    private static final String ACTION = "com.example.libraryonline.REMINDER";
    private static final String CHANNEL = "reader_reminders";
    private static final String STORE = "reader_alarm_registry";

    public static final class Reminder {
        public final String id, title, message;
        public final long atMillis;
        public Reminder(String id, long atMillis, String title, String message) {
            this.id = id; this.atMillis = atMillis; this.title = title; this.message = message;
        }
    }

    private static PendingIntent pending(Context context, String uid, String id,
                                         String title, String message, int flags) {
        Intent intent = new Intent(context, ReaderReminderReceiver.class).setAction(ACTION)
                .setData(Uri.parse("libraryonline://reminder/" + Uri.encode(uid + "/" + id)));
        intent.putExtra("uid", uid).putExtra("id", id).putExtra("title", title).putExtra("body", message);
        return PendingIntent.getBroadcast(context, 0, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void cancelAll(Context context, String uid) {
        if (uid == null) return;
        SharedPreferences store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        Set<String> ids = new HashSet<>(store.getStringSet(uid, new HashSet<>()));
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (String id : ids) {
            PendingIntent p = pending(context, uid, id, "", "", PendingIntent.FLAG_NO_CREATE);
            if (p != null) { if (alarms != null) alarms.cancel(p); p.cancel(); }
        }
        store.edit().remove(uid).apply();
    }

    public static void replaceAll(Context context, String uid, List<Reminder> reminders, boolean enabled) {
        cancelAll(context, uid);
        if (!enabled) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Set<String> ids = new HashSet<>();
        for (Reminder r : reminders) {
            if (r.atMillis <= System.currentTimeMillis()) continue;
            PendingIntent p = pending(context, uid, r.id, r.title, r.message, PendingIntent.FLAG_UPDATE_CURRENT);
            if (Build.VERSION.SDK_INT >= 23)
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.atMillis, p);
            else alarms.set(AlarmManager.RTC_WAKEUP, r.atMillis, p);
            ids.add(r.id);
        }
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
                .putStringSet(uid, ids).apply();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        String uid = intent.getStringExtra("uid"), id = intent.getStringExtra("id");
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (uid == null || id == null || user == null || !uid.equals(user.getUid())
                || !ReaderNotificationSettingsController.alertsAllowed(context, uid)
                || !context.getSharedPreferences("reader_notification_preferences_v1",
                    Context.MODE_PRIVATE).getBoolean("offline_" + uid, true)) return;
        String title = intent.getStringExtra("title"), body = intent.getStringExtra("body");
        if (title == null) title = "Nhắc lịch thư viện";
        if (body == null) body = "";
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26)
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "Nhắc hạn mượn và nhận sách", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, ReaderMainActivity.class)
                .putExtra(ReaderNavigation.EXTRA_TAB, ReaderHomeView.NOTIFICATIONS)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openApp = PendingIntent.getActivity(context, 9, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder n = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        n.setSmallIcon(R.drawable.mylibrary_logo).setContentTitle(title)
                .setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(openApp).setAutoCancel(true);
        manager.notify((uid + "|" + id).hashCode() & 0x7fffffff, n.build());
        try (LibraryNotificationRepository repo = new LibraryNotificationRepository(context)) {
            LibraryNotification row = new LibraryNotification();
            row.notificationId = "reminder_" + uid + "_" + id;
            row.userId = uid; row.notificationType = "LocalReminder";
            row.title = title; row.message = body;
            row.scheduledAt = row.sentAt =
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(new Date());
            row.deliveryStatus = "Sent";
            repo.create(row);
        } catch (Exception ignored) { /* Duplicate deliveries must not duplicate history. */ }
    }
}
