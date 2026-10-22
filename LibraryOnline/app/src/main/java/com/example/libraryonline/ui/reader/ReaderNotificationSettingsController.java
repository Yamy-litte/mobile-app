package com.example.libraryonline.ui.reader;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NotificationManagerCompat;
import com.example.libraryonline.R;
import com.example.libraryonline.data.model.LoanDetail;
import com.example.libraryonline.data.remote.firebase.OnlineLoanRequestRepository;
import com.example.libraryonline.ui.notifications.ReaderReminderReceiver;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Settings shared between the Reader's Account and Notifications tabs. */
public final class ReaderNotificationSettingsController {
    private static final String PREF = "reader_notification_preferences_v1";
    private final ReaderMainActivity activity;
    private final SwitchCompat[] master = new SwitchCompat[2], offline = new SwitchCompat[2];
    private final List<ReaderReminderReceiver.Reminder> loans = new ArrayList<>(), holds = new ArrayList<>();
    private String uid;
    private boolean updating;
    private int localUnread, onlineUnread;

    public ReaderNotificationSettingsController(ReaderMainActivity activity) {
        this.activity = activity;
        master[0] = activity.findViewById(R.id.readerAlertsEnabled);
        master[1] = activity.findViewById(R.id.readerSettingsAlertsEnabled);
        offline[0] = activity.findViewById(R.id.readerAlertsOffline);
        offline[1] = activity.findViewById(R.id.readerSettingsAlertsOffline);
        for (SwitchCompat control : master) control.setOnCheckedChangeListener((view, checked) -> {
            if (updating || uid == null) return;
            preferences(activity).edit().putBoolean("master_" + uid, checked).apply();
            if (checked) com.example.libraryonline.ui.notifications.OnlineLoanAlertCenter.requestOptIn(activity);
            refresh();
        });
        for (SwitchCompat control : offline) control.setOnCheckedChangeListener((view, checked) -> {
            if (updating || uid == null) return;
            preferences(activity).edit().putBoolean("offline_" + uid, checked).apply();
            refresh();
        });
        View.OnClickListener openAndroidSettings = view -> activity.startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", activity.getPackageName(), null)));
        activity.findViewById(R.id.readerAlertsSystemPermission).setOnClickListener(openAndroidSettings);
        activity.findViewById(R.id.readerSettingsSystemPermission).setOnClickListener(openAndroidSettings);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean userEnabled(Context context, String uid) {
        return uid != null && preferences(context).getBoolean("master_" + uid, true);
    }

    public static boolean alertsAllowed(Context context, String uid) {
        return userEnabled(context, uid) && NotificationManagerCompat.from(context).areNotificationsEnabled()
                && (Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED);
    }

    public void start(String userId) {
        if (userId == null || userId.trim().isEmpty()) return;
        if (uid != null) stop();
        uid = userId;
        localUnread = onlineUnread = 0;
        refresh();
    }

    public void stop() {
        if (uid != null) ReaderReminderReceiver.cancelAll(activity, uid);
        uid = null;
        loans.clear();
        holds.clear();
        localUnread = onlineUnread = 0;
        ((BottomNavigationView) activity.findViewById(R.id.readerBottomNavigation))
                .removeBadge(R.id.navReaderNotifications);
    }

    public void setLocalUnread(int count) { localUnread = count; badge(); }
    public void setOnlineUnread(int count) { onlineUnread = count; badge(); }

    private void badge() {
        BottomNavigationView nav = activity.findViewById(R.id.readerBottomNavigation);
        int count = localUnread + onlineUnread;
        if (count > 0) nav.getOrCreateBadge(R.id.navReaderNotifications).setNumber(count);
        else nav.removeBadge(R.id.navReaderNotifications);
    }

    public void updateLoans(List<LoanDetail> active) {
        if (uid == null) return;
        loans.clear();
        if (active != null) for (LoanDetail loan : active) {
            long due = dueMillis(loan.dueDate);
            long remind = due - 86_400_000L;
            if (remind <= System.currentTimeMillis()) continue;
            loans.add(new ReaderReminderReceiver.Reminder(
                    "loan_" + loan.loanDetailId, remind, "Ngày mai đến hạn trả sách",
                    "Cuốn " + safe(loan.copyId, "đang mượn") + " đến hạn " + showTime(due)
                            + ". Vui lòng chủ động trả sách đúng hẹn."));
        }
        refresh();
    }

    public void updateOnlineRequests(List<DocumentSnapshot> requests) {
        if (uid == null) return;
        holds.clear();
        if (requests != null) for (DocumentSnapshot request : requests) {
            if (!OnlineLoanRequestRepository.APPROVED.equals(request.getString("status"))) continue;
            Timestamp ts = request.getTimestamp("holdUntil");
            if (ts == null) continue;
            long until = ts.toDate().getTime(), remind = until - 3_600_000L;
            if (remind <= System.currentTimeMillis()) continue;
            holds.add(new ReaderReminderReceiver.Reminder(
                    "hold_" + request.getId(), remind, "Đơn Online sắp hết hạn giữ",
                    safe(request.getString("bookTitle"), "Sách đã duyệt")
                            + ": vui lòng nhận trước " + showTime(until) + "."));
        }
        refresh();
    }

    public void refresh() {
        if (uid == null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !uid.equals(user.getUid())) return;
        boolean enabled = userEnabled(activity, uid),
                local = preferences(activity).getBoolean("offline_" + uid, true),
                allowed = alertsAllowed(activity, uid), schedule = allowed && local;
        updating = true;
        for (SwitchCompat s : master) s.setChecked(enabled);
        for (SwitchCompat s : offline) { s.setChecked(local); s.setEnabled(enabled); }
        updating = false;
        String status = !enabled ? "Đã tắt thông báo trong MyLibrary."
                : !allowed ? "Android đang chặn thông báo. Vui lòng cấp quyền trong cài đặt thiết bị."
                : "Thiết bị đã cho phép nhận thông báo.";
        ((TextView) activity.findViewById(R.id.readerAlertsPermissionStatus)).setText(status);
        ((TextView) activity.findViewById(R.id.readerSettingsPermissionStatus)).setText(status);
        int visibility = enabled && !allowed ? View.VISIBLE : View.GONE;
        activity.findViewById(R.id.readerAlertsSystemPermission).setVisibility(visibility);
        activity.findViewById(R.id.readerSettingsSystemPermission).setVisibility(visibility);
        List<ReaderReminderReceiver.Reminder> upcoming = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ReaderReminderReceiver.Reminder r : loans) if (r.atMillis > now) upcoming.add(r);
        for (ReaderReminderReceiver.Reminder r : holds) if (r.atMillis > now) upcoming.add(r);
        // Collections.sort is available on API 23; List.sort / Comparator.comparingLong require API 24.
        Collections.sort(upcoming, (left, right) -> Long.compare(left.atMillis, right.atMillis));
        ReaderReminderReceiver.replaceAll(activity, uid, upcoming, schedule);
        render(R.id.readerAlertsScheduled, R.id.readerAlertsScheduledSummary, upcoming, schedule);
        render(R.id.readerSettingsScheduled, R.id.readerSettingsScheduledSummary, upcoming, schedule);
    }

    private void render(int containerId, int summaryId,
                        List<ReaderReminderReceiver.Reminder> reminders, boolean scheduled) {
        LinearLayout holder = activity.findViewById(containerId);
        holder.removeAllViews();
        ((TextView) activity.findViewById(summaryId)).setText(
                !scheduled ? "Nhắc cục bộ đã tạm dừng hoặc chưa được cấp quyền."
                : reminders.isEmpty() ? "Hiện chưa có lịch nhắc sắp tới."
                : "Đã lên lịch " + reminders.size() + " lời nhắc trên thiết bị.");
        for (int i = 0; i < Math.min(20, reminders.size()); i++) {
            ReaderReminderReceiver.Reminder r = reminders.get(i);
            View row = activity.getLayoutInflater().inflate(
                    R.layout.item_reader_scheduled_reminder, holder, false);
            ((TextView) row.findViewById(R.id.readerScheduledTitle)).setText(r.title);
            ((TextView) row.findViewById(R.id.readerScheduledTime))
                    .setText(showTime(r.atMillis) + (scheduled ? " · Chưa gửi" : " · Tạm dừng"));
            ((TextView) row.findViewById(R.id.readerScheduledMessage)).setText(r.message);
            holder.addView(row);
        }
    }

    private static long dueMillis(String text) {
        if (text == null) return 0;
        String raw = text.trim();
        String[] formats = { "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd", "dd/MM/yyyy", "yyyy/MM/dd" };
        for (String pattern : formats) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.ROOT);
                parser.setLenient(false);
                Date value = parser.parse(raw);
                if (value != null && ((raw.length() == 10) == !pattern.contains("HH")))
                    return value.getTime() + (raw.length() == 10 ? 32_400_000L : 0L);
            } catch (ParseException ignored) {}
        }
        return 0;
    }

    private static String safe(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s.trim();
    }
    private static String showTime(long millis) {
        return new SimpleDateFormat("dd/MM/yyyy · HH:mm", new Locale("vi", "VN"))
                .format(new Date(millis));
    }
}
