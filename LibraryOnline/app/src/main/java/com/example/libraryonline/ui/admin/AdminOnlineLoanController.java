package com.example.libraryonline.ui.admin;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.example.libraryonline.R;
import com.example.libraryonline.ui.notifications.OnlineLoanAlertCenter;
import com.example.libraryonline.data.remote.firebase.OnlineLoanRequestRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
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
    // The Firestore listener may stay open without receiving a new snapshot at holdUntil.
    // Check held copies again while this Admin screen is active; this is not a
    // background/closed-app scheduler.
    private final Handler expiryHandler = new Handler(Looper.getMainLooper());
    private final List<DocumentSnapshot> knownApprovedHolds = new ArrayList<>();
    private final Runnable expiryPulse = new Runnable() {
        @Override public void run() {
            String uid = adminUid;
            if (!current(uid) || knownApprovedHolds.isEmpty()) return;
            for (DocumentSnapshot hold : new ArrayList<>(knownApprovedHolds))
                checkExpiry(hold, uid);
            expiryHandler.postDelayed(this, 60_000L);
        }
    };
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
        expiryHandler.removeCallbacks(expiryPulse);
        knownApprovedHolds.clear();
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

    private String date(Timestamp stamp) {
        if (stamp == null) return "Đang đồng bộ";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"))
                .format(stamp.toDate());
    }

    private void render(QuerySnapshot snapshot) {
        LinearLayout container = activity.findViewById(R.id.containerAdminOnlineRequests);
        container.removeAllViews();
        List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
        // Compatible with Android API 23 (List.sort and Comparator.comparing need API 24).
        Collections.sort(docs, (left, right) -> {
            Timestamp leftCreated = left.getTimestamp("createdAt");
            Timestamp rightCreated = right.getTimestamp("createdAt");
            long leftTime = leftCreated == null ? 0L : leftCreated.toDate().getTime();
            long rightTime = rightCreated == null ? 0L : rightCreated.toDate().getTime();
            return Long.compare(rightTime, leftTime);
        });
        // Never schedule expiry from cached-only snapshots: a server decision
        // may already have delivered or released the book.
        if (!snapshot.getMetadata().isFromCache()) {
            expiryHandler.removeCallbacks(expiryPulse);
            knownApprovedHolds.clear();
            for (DocumentSnapshot doc : docs) {
                if (OnlineLoanRequestRepository.APPROVED.equals(doc.getString("status"))
                        && doc.getTimestamp("holdUntil") != null)
                    knownApprovedHolds.add(doc);
            }
            if (!knownApprovedHolds.isEmpty())
                expiryHandler.postDelayed(expiryPulse, 60_000L);
        }
        int pending = 0, approved = 0, collected = 0;
        for (DocumentSnapshot doc : docs) {
            if (OnlineLoanRequestRepository.PENDING.equals(doc.getString("status"))) pending++;
            if (OnlineLoanRequestRepository.APPROVED.equals(doc.getString("status"))) approved++;
            if (OnlineLoanRequestRepository.COLLECTED.equals(doc.getString("status"))) collected++;
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
                .setText(count + " đang chờ duyệt · " + approved + " chờ lấy · "
                        + collected + " đang mượn"
                        + (snapshot.getMetadata().isFromCache()
                                ? " · Dữ liệu đã lưu, chờ kết nối" : ""));
        if (previousPending >= 0 && pending > previousPending
                && !snapshot.getMetadata().isFromCache())
            Toast.makeText(activity,
                    "Có " + (pending - previousPending) + " yêu cầu mượn Online mới.",
                    Toast.LENGTH_LONG).show();
        previousPending = pending;

        if (pending == 0 && approved == 0 && collected == 0) {
            TextView empty = (TextView) activity.getLayoutInflater().inflate(
                    R.layout.view_ui_text_secondary, container, false);
            empty.setText("Hiện không có yêu cầu cần xử lý.");
            container.addView(empty);
        }
        String uid = adminUid;
        for (DocumentSnapshot doc : docs) {
            String state = doc.getString("status");
            if (!OnlineLoanRequestRepository.PENDING.equals(state)
                    && !OnlineLoanRequestRepository.APPROVED.equals(state)
                    && !OnlineLoanRequestRepository.COLLECTED.equals(state)) continue;
            View item = activity.getLayoutInflater().inflate(
                    R.layout.item_admin_online_request, container, false);
            container.addView(item);
            String reader = doc.getString("userId");
            String title = doc.getString("bookTitle");
            ((TextView) item.findViewById(R.id.adminOnlineItemTitle))
                    .setText(title == null ? "Đầu sách" : title);
            TextView who = item.findViewById(R.id.adminOnlineItemReader);
            who.setText("Độc giả: " + (reader == null ? "—" : reader));
            if (reader != null) {
                db.collection("Users").document(reader).get().addOnSuccessListener(user -> {
                    if (!current(uid) || item.getParent() == null || !user.exists()) return;
                    String fullName = user.getString("fullName");
                    who.setText("Độc giả: " + (fullName == null ? reader : fullName));
                });
            }
            Long days = doc.getLong("loanDays");
            ((TextView) item.findViewById(R.id.adminOnlineItemDays))
                    .setText("Số ngày mượn: " + (days == null ? "—" : days));
            if (OnlineLoanRequestRepository.APPROVED.equals(state)) {
                TextView approvedNotice = item.findViewById(R.id.adminOnlineItemApproved);
                approvedNotice.setText("Đã duyệt · Chờ Reader đến lấy trước "
                        + date(doc.getTimestamp("holdUntil")));
                approvedNotice.setVisibility(View.VISIBLE);
                Timestamp until = doc.getTimestamp("holdUntil");
                if (until != null && !until.toDate().after(new Date())) {
                    Button release = item.findViewById(R.id.adminOnlineItemRelease);
                    release.setVisibility(View.VISIBLE);
                    release.setEnabled(!snapshot.getMetadata().isFromCache());
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
                bindOnlineHandover(item, doc, uid, snapshot.getMetadata().isFromCache(), false);
                continue;
            }
            if (OnlineLoanRequestRepository.COLLECTED.equals(state)) {
                TextView loanState = item.findViewById(R.id.adminOnlineItemLoanState);
                loanState.setVisibility(View.VISIBLE);
                loanState.setText("Đã giao: " + date(doc.getTimestamp("collectedAt"))
                        + " · Hạn trả: " + date(doc.getTimestamp("dueAt")));
                bindOnlineHandover(item, doc, uid, snapshot.getMetadata().isFromCache(), true);
                continue;
            }
            TextView sent = item.findViewById(R.id.adminOnlineItemSent);
            sent.setText("Gửi: " + date(doc.getTimestamp("createdAt")));
            sent.setVisibility(View.VISIBLE);
            item.findViewById(R.id.adminOnlineItemActions).setVisibility(View.VISIBLE);
            Button approve = item.findViewById(R.id.adminOnlineItemApprove);
            Button reject = item.findViewById(R.id.adminOnlineItemReject);
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
                View dialogView = activity.getLayoutInflater().inflate(
                        R.layout.dialog_admin_online_reject, null, false);
                EditText reason = dialogView.findViewById(R.id.adminOnlineRejectReason);
                AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setTitle("Từ chối yêu cầu")
                        .setView(dialogView)
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

    /** Only the XML fields are presentation; this controller only verifies data and binds actions. */
    private void bindOnlineHandover(View item, DocumentSnapshot request, String uid,
                                     boolean cached, boolean returning) {
        View panel = item.findViewById(R.id.adminOnlineHandoverPanel);
        panel.setVisibility(View.VISIBLE);
        EditText readerBarcode = item.findViewById(R.id.adminOnlineReaderBarcode);
        EditText copyBarcode = item.findViewById(R.id.adminOnlineCopyBarcode);
        Button action = item.findViewById(returning
                ? R.id.adminOnlineItemReturn : R.id.adminOnlineItemCollect);
        action.setVisibility(View.VISIBLE);
        action.setEnabled(!cached);
        action.setOnClickListener(v -> {
            if (!current(uid) || !action.isEnabled()) return;
            String reader = readerBarcode.getText().toString().trim();
            String copy = copyBarcode.getText().toString().trim();
            if (reader.isEmpty() || copy.isEmpty()) {
                Toast.makeText(activity, "Quét/nhập đủ mã thẻ và mã cuốn sách.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            showConfirmation(returning ? "Xác nhận trả sách Online" : "Xác nhận giao sách Online",
                    returning ? "Đã nhận lại đúng cuốn sách và xác minh thẻ độc giả?"
                            : "Đã đối chiếu mã thẻ và mã cuốn thực tế trước khi giao sách?",
                    () -> {
                        if (!current(uid)) return;
                        action.setEnabled(false);
                        com.google.android.gms.tasks.Task<Void> task = returning
                                ? repository.returnOnline(request.getId(), uid, reader, copy)
                                : repository.collect(request.getId(), uid, reader, copy);
                        task.addOnCompleteListener(activity, result -> {
                            if (!current(uid)) return;
                            if (!result.isSuccessful()) {
                                action.setEnabled(true);
                                Toast.makeText(activity, "Không thể xác nhận: "
                                        + (result.getException() == null
                                          ? "Cần tải lại dữ liệu." : result.getException().getMessage()),
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(activity, returning
                                                ? "Đã xác nhận trả sách Online trên Firestore."
                                                : "Đã giao sách Online và ghi nhận hạn trả.",
                                        Toast.LENGTH_LONG).show();
                                // Firestore's listener redraws the new state.
                            }
                        });
                    });
        });
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
                // Retry on the next minute tick after a transient network/rules error.
                // The repository transaction still guards against double release.
                expiryAttempted.remove(doc.getId());
                Exception error = task.getException();
                Toast.makeText(activity,
                        "Chưa giải phóng được yêu cầu hết hạn: "
                                + (error == null ? "Cần đối soát." : error.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
            // Successful expiry remains idempotent and is reflected by the listener.
        });
    }
}
