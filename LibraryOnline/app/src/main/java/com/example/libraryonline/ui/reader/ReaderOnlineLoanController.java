package com.example.libraryonline.ui.reader;

import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.libraryonline.R;
import com.example.libraryonline.data.remote.firebase.OnlineLoanRequestRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Real-time per-reader Online requests and delivered in-app notifications. */
final class ReaderOnlineLoanController {
    private final ReaderMainActivity activity;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final OnlineLoanRequestRepository repository = new OnlineLoanRequestRepository();
    private ListenerRegistration requestsListener, notificationsListener;
    private String authorizedUid;

    ReaderOnlineLoanController(ReaderMainActivity activity) { this.activity = activity; }

    void start(String uid) {
        stop();
        if (uid == null || uid.isEmpty()) return;
        authorizedUid = uid;
        ((TextView) activity.findViewById(R.id.textReaderOnlineSummary))
                .setText(R.string.online_borrow_loading);
        ((TextView) activity.findViewById(R.id.textReaderOnlineNotificationsSummary))
                .setText(R.string.online_borrow_loading);
        requestsListener = firestore.collection(OnlineLoanRequestRepository.REQUESTS)
                .whereEqualTo("userId", uid)
                .addSnapshotListener(MetadataChanges.INCLUDE, (result, error) -> {
                    if (!current(uid)) return;
                    if (error != null) {
                        message(R.id.textReaderOnlineSummary,
                                "Không tải được yêu cầu Online. Kiểm tra kết nối hoặc quyền đọc.");
                        return;
                    }
                    if (result != null) displayRequests(result);
                });
        notificationsListener = firestore.collection(OnlineLoanRequestRepository.NOTIFICATIONS)
                .whereEqualTo("userId", uid)
                .addSnapshotListener(MetadataChanges.INCLUDE, (result, error) -> {
                    if (!current(uid)) return;
                    if (error != null) {
                        message(R.id.textReaderOnlineNotificationsSummary,
                                "Không tải được thông báo Online. Kiểm tra kết nối hoặc quyền đọc.");
                        return;
                    }
                    if (result != null) displayNotifications(result);
                });
    }

    void stop() {
        authorizedUid = null;
        if (requestsListener != null) { requestsListener.remove(); requestsListener = null; }
        if (notificationsListener != null) {
            notificationsListener.remove();
            notificationsListener = null;
        }
        ((LinearLayout) activity.findViewById(R.id.containerReaderOnlineRequests))
                .removeAllViews();
        ((LinearLayout) activity.findViewById(R.id.containerReaderOnlineNotifications))
                .removeAllViews();
    }

    private boolean current(String uid) {
        return uid.equals(authorizedUid) && activity.isSessionReady()
                && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void message(int id, String value) {
        ((TextView) activity.findViewById(id)).setText(value);
    }

    private int dp(int n) {
        return Math.round(n * activity.getResources().getDisplayMetrics().density);
    }

    private TextView line(String value, boolean heading) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextColor(ContextCompat.getColor(activity,
                heading ? R.color.library_text : R.color.library_muted));
        text.setTextSize(heading ? 16 : 13);
        if (heading) text.setTypeface(null, Typeface.BOLD);
        text.setPadding(0, dp(3), 0, dp(4));
        return text;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(16), dp(12), dp(16), dp(14));
        view.setBackgroundResource(R.drawable.bg_reader_search);
        LinearLayout.LayoutParams spacing =
                new LinearLayout.LayoutParams(-1, -2);
        spacing.bottomMargin = dp(10);
        parent.addView(view, spacing);
        return view;
    }

    private String time(Timestamp timestamp) {
        if (timestamp == null) return "Chưa đồng bộ";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi","VN"))
                .format(timestamp.toDate());
    }

    private List<DocumentSnapshot> recent(QuerySnapshot snap) {
        List<DocumentSnapshot> sorted = new ArrayList<>(snap.getDocuments());
        sorted.sort(Comparator.comparing((DocumentSnapshot doc) -> {
            Timestamp ts = doc.getTimestamp("createdAt");
            return ts == null ? 0L : ts.toDate().getTime();
        }).reversed());
        return sorted;
    }

    private void displayRequests(QuerySnapshot snapshot) {
        LinearLayout container = activity.findViewById(R.id.containerReaderOnlineRequests);
        container.removeAllViews();
        List<DocumentSnapshot> records = recent(snapshot);
        message(R.id.textReaderOnlineSummary, records.isEmpty()
                ? "Bạn chưa gửi yêu cầu mượn Online."
                : records.size() + " đầu sách đã yêu cầu"
                  + (snapshot.getMetadata().isFromCache() ? " · Đang hiển thị dữ liệu đã lưu" : ""));
        for (DocumentSnapshot doc : records) {
            LinearLayout item = card(container);
            String title = doc.getString("bookTitle");
            String status = doc.getString("status");
            item.addView(line(title == null ? "Đầu sách" : title, true));
            item.addView(line(OnlineLoanRequestRepository.label(status), false));
            Long days = doc.getLong("loanDays");
            item.addView(line("Thời hạn mượn yêu cầu: " + (days == null ? "—" : days) + " ngày", false));
            if (OnlineLoanRequestRepository.APPROVED.equals(status))
                item.addView(line("Hạn đến nhận: " + time(doc.getTimestamp("holdUntil")), true));
            if (OnlineLoanRequestRepository.REJECTED.equals(status)
                    && doc.getString("rejectionReason") != null)
                item.addView(line("Lý do: " + doc.getString("rejectionReason"), false));
            item.addView(line("Gửi: " + time(doc.getTimestamp("createdAt")), false));
        }
    }

    private void displayNotifications(QuerySnapshot snapshot) {
        LinearLayout container = activity.findViewById(R.id.containerReaderOnlineNotifications);
        container.removeAllViews();
        List<DocumentSnapshot> notifications = recent(snapshot);
        int unread = 0;
        for (DocumentSnapshot doc : notifications)
            if (doc.get("readAt") == null) unread++;
        message(R.id.textReaderOnlineNotificationsSummary,
                notifications.isEmpty() ? "Chưa có thông báo mượn Online."
                : notifications.size() + " thông báo · " + unread + " chưa đọc"
                + (snapshot.getMetadata().isFromCache() ? " · Bản đã lưu" : ""));
        for (DocumentSnapshot notification : notifications) {
            LinearLayout item = card(container);
            item.addView(line(notification.getString("title") == null
                    ? "Thông báo thư viện" : notification.getString("title"), true));
            item.addView(line(notification.getString("message") == null
                    ? "" : notification.getString("message"), false));
            item.addView(line(time(notification.getTimestamp("createdAt")), false));
            if (notification.get("readAt") == null) {
                TextView mark = line(activity.getString(R.string.online_borrow_mark_read), true);
                mark.setTextColor(ContextCompat.getColor(activity, R.color.library_blue));
                item.addView(mark);
                String uid = authorizedUid;
                mark.setOnClickListener(v -> {
                    if (!current(uid) || !mark.isEnabled()) return;
                    mark.setEnabled(false);
                    repository.markNotificationRead(notification.getId())
                            .addOnCompleteListener(activity, done -> {
                                if (!current(uid)) return;
                                if (!done.isSuccessful()) {
                                    mark.setEnabled(true);
                                    Toast.makeText(activity,
                                            "Không đánh dấu được thông báo. Hãy thử lại.",
                                            Toast.LENGTH_LONG).show();
                                }
                                // Successful writes will update the live Firestore listener.
                            });
                });
            } else item.addView(line("Đã đọc", false));
        }
    }
}
