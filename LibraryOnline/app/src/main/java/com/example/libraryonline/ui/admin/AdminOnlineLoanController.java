package com.example.libraryonline.ui.admin;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.libraryonline.R;
import com.example.libraryonline.ui.notifications.OnlineLoanAlertCenter;
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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Admin inbox. Firestore is the shared source for Reader → Admin review. */
final class AdminOnlineLoanController {
    private final AdminMainActivity activity;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final OnlineLoanRequestRepository repository = new OnlineLoanRequestRepository();
    private ListenerRegistration listener;
    private final List<AlertDialog> dialogs = new ArrayList<>();
    private final Set<String> expiryAttempted = new HashSet<>();
    private String adminUid;
    private int previousPending = -1;

    AdminOnlineLoanController(AdminMainActivity activity) { this.activity = activity; }

    void start(String uid) {
        stop();
        if (uid == null || uid.isEmpty()) return;
        adminUid = uid;
        activity.findViewById(R.id.buttonAdminEnableOnlineAlerts)
                .setOnClickListener(v -> OnlineLoanAlertCenter.requestOptIn(activity));
        ((TextView) activity.findViewById(R.id.textAdminOnlineCount))
                .setText(R.string.online_borrow_loading);
        listener = db.collection(OnlineLoanRequestRepository.REQUESTS)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (!current(uid)) return;
                    if (error != null) {
                        ((TextView) activity.findViewById(R.id.textAdminOnlineCount)).setText(
                                "Lỗi tải yêu cầu mượn Online: " + error.getMessage());
                        return;
                    }
                    if (snapshot != null) render(snapshot);
                });
    }

    void stop() {
        adminUid = null;
        previousPending = -1;
        expiryAttempted.clear();
        if (listener != null) { listener.remove(); listener = null; }
        for (AlertDialog dialog : new ArrayList<>(dialogs)) dialog.dismiss();
        dialogs.clear();
        ((LinearLayout) activity.findViewById(R.id.containerAdminOnlineRequests))
                .removeAllViews();
    }

    private boolean current(String uid) {
        return uid != null && uid.equals(adminUid) && activity.isSessionReady()
                && !activity.isDestroyed() && !activity.isFinishing();
    }

    private int dp(int n) {
        return Math.round(n * activity.getResources().getDisplayMetrics().density);
    }

    private TextView label(String value, boolean heading) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(heading ? 16 : 13);
        text.setTextColor(ContextCompat.getColor(activity,
                heading ? R.color.admin_screen_ink : R.color.admin_screen_muted));
        if (heading) text.setTypeface(null, Typeface.BOLD);
        text.setPadding(0, dp(4), 0, dp(5));
        return text;
    }

    private LinearLayout card(LinearLayout container) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(16));
        card.setBackgroundResource(R.drawable.bg_admin_screen_card);
        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2);
        spacing.bottomMargin = dp(12);
        container.addView(card, spacing);
        return card;
    }

    private Button button(String value) {
        Button b = new Button(activity);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextColor(ContextCompat.getColor(activity, R.color.white));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.admin_screen_primary)));
        return b;
    }

    private String date(Timestamp stamp) {
        if (stamp == null) return "Đang đồng bộ";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"))
                .format(stamp.toDate());
    }

    private void render(QuerySnapshot snapshot) {
        LinearLayout container = activity.findViewById(R.id.containerAdminOnlineRequests);
        container.removeAllViews();
        List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
        docs.sort(Comparator.comparing((DocumentSnapshot doc) -> {
            Timestamp stamp = doc.getTimestamp("createdAt");
            return stamp == null ? 0L : stamp.toDate().getTime();
        }).reversed());
        int pending = 0, approved = 0;
        for (DocumentSnapshot doc : docs) {
            if (OnlineLoanRequestRepository.PENDING.equals(doc.getString("status"))) pending++;
            if (OnlineLoanRequestRepository.APPROVED.equals(doc.getString("status"))) approved++;
        }
        if (!snapshot.getMetadata().isFromCache()) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (DocumentSnapshot d : docs)
                if (OnlineLoanRequestRepository.PENDING.equals(d.getString("status")))
                    ids.add(d.getId() + "_v" + d.getLong("attempt"));
            OnlineLoanAlertCenter.registerSnapshot(activity, adminUid, true, ids);
            for (DocumentSnapshot d : docs)
                if (OnlineLoanRequestRepository.PENDING.equals(d.getString("status")))
                    OnlineLoanAlertCenter.notifyNew(activity, adminUid, true,
                            d.getId() + "_v" + d.getLong("attempt"),
                            "Yêu cầu mượn Online cần duyệt",
                            (d.getString("bookTitle") == null ? "Một đầu sách" : d.getString("bookTitle"))
                                    + " · Chờ Admin xem xét.");
        }
        final int count = pending;
        ((TextView) activity.findViewById(R.id.textAdminOnlineCount))
                .setText(count + " đang chờ duyệt · " + approved + " đã giữ sách"
                        + (snapshot.getMetadata().isFromCache()
                                ? " · Dữ liệu đã lưu, chờ kết nối" : ""));
        if (previousPending >= 0 && pending > previousPending
                && !snapshot.getMetadata().isFromCache())
            Toast.makeText(activity,
                    "Có " + (pending - previousPending) + " yêu cầu mượn Online mới.",
                    Toast.LENGTH_LONG).show();
        previousPending = pending;

        if (pending == 0 && approved == 0)
            container.addView(label("Hiện không có yêu cầu cần xử lý.", false));
        String uid = adminUid;
        for (DocumentSnapshot doc : docs) {
            String state = doc.getString("status");
            if (!OnlineLoanRequestRepository.PENDING.equals(state)
                    && !OnlineLoanRequestRepository.APPROVED.equals(state)) continue;
            LinearLayout item = card(container);
            String reader = doc.getString("userId");
            String title = doc.getString("bookTitle");
            item.addView(label(title == null ? "Đầu sách" : title, true));
            TextView who = label("Độc giả: " + (reader == null ? "—" : reader), false);
            item.addView(who);
            if (reader != null) {
                db.collection("Users").document(reader).get().addOnSuccessListener(user -> {
                    if (!current(uid) || item.getParent() == null || !user.exists()) return;
                    String fullName = user.getString("fullName");
                    who.setText("Độc giả: " + (fullName == null ? reader : fullName));
                });
            }
            Long days = doc.getLong("loanDays");
            item.addView(label("Số ngày mượn: " + (days == null ? "—" : days), false));
            if (OnlineLoanRequestRepository.APPROVED.equals(state)) {
                item.addView(label("Đã duyệt · Chờ Reader đến lấy trước "
                        + date(doc.getTimestamp("holdUntil")), true));
                Timestamp until = doc.getTimestamp("holdUntil");
                if (until != null && !until.toDate().after(new Date())) {
                    Button release = button("Hết 24 giờ · Kiểm tra & trả cuốn về kho");
                    release.setEnabled(!snapshot.getMetadata().isFromCache());
                    item.addView(release);
                    release.setOnClickListener(v -> {
                        if (!current(uid) || !release.isEnabled()) return;
                        release.setEnabled(false);
                        repository.expire(doc.getId()).addOnCompleteListener(activity, task -> {
                            if (!current(uid)) return;
                            if (!task.isSuccessful()) {
                                release.setEnabled(true);
                                Toast.makeText(activity,
                                        "Không thể trả kho: " + (task.getException() == null
                                                ? "Cần đối soát." : task.getException().getMessage()),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    });
                }
                if (!snapshot.getMetadata().isFromCache()) checkExpiry(doc, uid);
                continue;
            }
            item.addView(label("Gửi: " + date(doc.getTimestamp("createdAt")), false));
            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button approve = button(activity.getString(R.string.online_borrow_review_approve));
            Button reject = button(activity.getString(R.string.online_borrow_review_reject));
            LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(50), 1);
            actions.addView(approve, half);
            actions.addView(reject, new LinearLayout.LayoutParams(0, dp(50), 1));
            item.addView(actions);
            boolean canDecide = !snapshot.getMetadata().isFromCache();
            approve.setEnabled(canDecide);
            reject.setEnabled(canDecide);
            approve.setOnClickListener(v -> {
                if (!current(uid)) return;
                showConfirmation("Duyệt yêu cầu mượn",
                        "Giữ một cuốn sách sẵn sàng trong 24 giờ. Reader sẽ nhận thông báo.",
                        () -> perform(doc.getId(), uid, approve, reject, true, null));
            });
            reject.setOnClickListener(v -> {
                if (!current(uid)) return;
                EditText reason = new EditText(activity);
                reason.setSingleLine(false);
                reason.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                reason.setHint("Nhập lý do từ chối (ít nhất 5 ký tự)");
                reason.setPadding(dp(16), dp(8), dp(16), dp(8));
                AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setTitle("Từ chối yêu cầu")
                        .setView(reason)
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Từ chối", (d, which) -> {
                            String value = reason.getText().toString().trim();
                            if (value.length() < 5 || value.length() > 300) {
                                Toast.makeText(activity, "Lý do từ 5 đến 300 ký tự.",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            perform(doc.getId(), uid, approve, reject, false, value);
                        }).create();
                show(dialog);
            });
        }
    }

    private void showConfirmation(String title, String body, Runnable action) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(body)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận", (d, which) -> action.run())
                .create();
        show(dialog);
    }

    private void show(AlertDialog dialog) {
        if (!current(adminUid)) return;
        dialogs.add(dialog);
        dialog.setOnDismissListener(d -> dialogs.remove(dialog));
        dialog.show();
    }

    private void perform(String requestId, String uid, Button approve, Button reject,
                         boolean accepting, String reason) {
        if (!current(uid)) return;
        approve.setEnabled(false);
        reject.setEnabled(false);
        com.google.android.gms.tasks.Task<Void> operation = accepting
                ? repository.approve(requestId, uid)
                : repository.reject(requestId, uid, reason);
        operation.addOnCompleteListener(activity, done -> {
            if (!current(uid)) return;
            if (!done.isSuccessful()) {
                approve.setEnabled(true);
                reject.setEnabled(true);
                Exception error = done.getException();
                Toast.makeText(activity, "Không xử lý được: "
                        + (error == null ? "Hãy tải lại." : error.getMessage()),
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(activity,
                        accepting ? "Đã duyệt và giữ sách; Reader đã được thông báo."
                                : "Đã từ chối; Reader đã được thông báo.",
                        Toast.LENGTH_LONG).show();
            }
            // A successful write is reflected by the Firestore request listener.
        });
    }

    private void checkExpiry(DocumentSnapshot doc, String uid) {
        Timestamp hold = doc.getTimestamp("holdUntil");
        if (hold == null || hold.toDate().after(new Date())
                || !expiryAttempted.add(doc.getId())) return;
        repository.expire(doc.getId()).addOnCompleteListener(activity, task -> {
            if (!current(uid)) return;
            if (!task.isSuccessful()) {
                Exception error = task.getException();
                Toast.makeText(activity,
                        "Chưa giải phóng được yêu cầu hết hạn: "
                                + (error == null ? "Cần đối soát." : error.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
            // Avoid repeated retries/stock mutations during the same session.
        });
    }
}
