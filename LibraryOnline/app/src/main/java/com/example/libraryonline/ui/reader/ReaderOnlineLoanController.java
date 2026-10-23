package com.example.libraryonline.ui.reader;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
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
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;

/** Real-time per-reader Online requests and delivered in-app notifications. */
final class ReaderOnlineLoanController {
    private final ReaderMainActivity activity;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final OnlineLoanRequestRepository repository = new OnlineLoanRequestRepository();
    private ListenerRegistration requestsListener, notificationsListener, receiptsListener;
    private String authorizedUid;
    static final String BORROWED = "borrowed", PICKUP = "pickup",
            PENDING_MODE = "pending", HISTORY = "history";
    private String loanMode = BORROWED;
    private QuerySnapshot lastRequests;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Map<TextView, Timestamp> activeCountdowns = new HashMap<>();
    private final Runnable countdownPulse = new Runnable() {
        @Override public void run() {
            if (authorizedUid == null || !current(authorizedUid)) return;
            for (Map.Entry<TextView, Timestamp> hold : activeCountdowns.entrySet())
                hold.getKey().setText(remainingText(hold.getValue()));
            if (!activeCountdowns.isEmpty()) clockHandler.postDelayed(this, 60_000L);
        }
    };

    ReaderOnlineLoanController(ReaderMainActivity activity) {
        this.activity = activity;
        bindLoanTabs();
    }

    private void bindLoanTabs() {
        activity.findViewById(R.id.readerLoansTabBorrowed)
                .setOnClickListener(v -> setMode(BORROWED));
        activity.findViewById(R.id.readerLoansTabPickup)
                .setOnClickListener(v -> setMode(PICKUP));
        activity.findViewById(R.id.readerLoansTabPending)
                .setOnClickListener(v -> setMode(PENDING_MODE));
        activity.findViewById(R.id.readerLoansTabHistory)
                .setOnClickListener(v -> setMode(HISTORY));
        updateLoanTabPresentation();
    }

    String selectedMode() { return loanMode; }

    void setMode(String mode) {
        if (!BORROWED.equals(mode) && !PICKUP.equals(mode)
                && !PENDING_MODE.equals(mode) && !HISTORY.equals(mode)) mode = BORROWED;
        loanMode = mode;
        updateLoanTabPresentation();
        if (lastRequests != null && authorizedUid != null && current(authorizedUid))
            displayRequests(lastRequests);
    }

    private void updateLoanTabPresentation() {
        activity.findViewById(R.id.readerLoansTabBorrowed).setSelected(BORROWED.equals(loanMode));
        activity.findViewById(R.id.readerLoansTabPickup).setSelected(PICKUP.equals(loanMode));
        activity.findViewById(R.id.readerLoansTabPending).setSelected(PENDING_MODE.equals(loanMode));
        activity.findViewById(R.id.readerLoansTabHistory).setSelected(HISTORY.equals(loanMode));
        activity.findViewById(R.id.readerLoansLocalPanel).setVisibility(
                BORROWED.equals(loanMode) ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.readerLoansHistoryPanel).setVisibility(
                HISTORY.equals(loanMode) ? View.VISIBLE : View.GONE);
        TextView heading = activity.findViewById(R.id.readerLoansSectionHeading);
        TextView explanation = activity.findViewById(R.id.readerLoansSectionDescription);
        if (BORROWED.equals(loanMode)) {
            heading.setText("Sách đang mượn Online");
            explanation.setText("Đã nhận tại quầy · Hạn trả bắt đầu khi thủ thư giao sách.");
        } else if (PICKUP.equals(loanMode)) {
            heading.setText("Đã duyệt · Chờ đến nhận");
            explanation.setText("Thư viện đã giữ suất theo đầu sách. Quét nhận cuốn tại quầy trước hạn.");
        } else if (PENDING_MODE.equals(loanMode)) {
            heading.setText("Yêu cầu đang chờ duyệt");
            explanation.setText("Theo dõi phản hồi của thủ thư. Bạn có thể hủy đơn chưa duyệt.");
        } else {
            heading.setText("Yêu cầu đã kết thúc");
            explanation.setText("Đơn đã trả, từ chối, hủy hoặc hết hạn; bên dưới là lịch sử giao/trả Online.");
        }
    }

    private boolean visibleInMode(String status) {
        if (BORROWED.equals(loanMode))
            return OnlineLoanRequestRepository.COLLECTED.equals(status);
        if (PICKUP.equals(loanMode))
            return OnlineLoanRequestRepository.APPROVED.equals(status);
        if (PENDING_MODE.equals(loanMode))
            return OnlineLoanRequestRepository.PENDING.equals(status);
        return OnlineLoanRequestRepository.RETURNED.equals(status)
                || OnlineLoanRequestRepository.REJECTED.equals(status)
                || OnlineLoanRequestRepository.EXPIRED.equals(status)
                || OnlineLoanRequestRepository.CANCELLED.equals(status);
    }

    void start(String uid) {
        stop();
        if (uid == null || uid.isEmpty()) return;
        authorizedUid = uid;
        updateLoanTabPresentation();
        ((TextView) activity.findViewById(R.id.textReaderOnlineSummary))
                .setText(R.string.online_borrow_loading);
        ((TextView) activity.findViewById(R.id.textReaderOnlineNotificationsSummary))
                .setText(R.string.online_borrow_loading);
        message(R.id.textReaderOnlineReceiptsSummary, "Đang tải lịch sử giao sách Online…");
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
        receiptsListener = firestore.collection("OnlineLoanReceipts")
                .whereEqualTo("userId", uid)
                .addSnapshotListener(MetadataChanges.INCLUDE, (result, error) -> {
                    if (!current(uid)) return;
                    if (error != null) {
                        message(R.id.textReaderOnlineReceiptsSummary,
                                "Không tải được lịch sử giao sách Online. Hãy thử lại.");
                        return;
                    }
                    if (result != null) displayReceipts(result);
                });
    }

    void stop() {
        authorizedUid = null;
        lastRequests = null;
        clockHandler.removeCallbacks(countdownPulse);
        activeCountdowns.clear();
        if (requestsListener != null) { requestsListener.remove(); requestsListener = null; }
        if (notificationsListener != null) {
            notificationsListener.remove();
            notificationsListener = null;
        }
        if (receiptsListener != null) {
            receiptsListener.remove();
            receiptsListener = null;
        }
        ((LinearLayout) activity.findViewById(R.id.containerReaderOnlineRequests))
                .removeAllViews();
        ((LinearLayout) activity.findViewById(R.id.containerReaderOnlineNotifications))
                .removeAllViews();
        ((LinearLayout) activity.findViewById(R.id.containerReaderOnlineReceipts))
                .removeAllViews();
    }

    private boolean current(String uid) {
        return uid.equals(authorizedUid) && activity.isSessionReady()
                && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void message(int id, String value) {
        ((TextView) activity.findViewById(id)).setText(value);
    }

    private String time(Timestamp timestamp) {
        if (timestamp == null) return "Chưa đồng bộ";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi","VN"))
                .format(timestamp.toDate());
    }

    private static String remainingText(Timestamp until) {
        if (until == null) return "Đang xác minh hạn nhận tại thư viện";
        long millis = until.toDate().getTime() - System.currentTimeMillis();
        if (millis <= 0)
            return "Đã quá giờ nhận. Chờ thư viện xác nhận hết hạn và trả cuốn về kho.";
        long mins = (millis + 59_999L) / 60_000L;
        return "Còn " + (mins / 60) + " giờ " + (mins % 60) + " phút để nhận sách";
    }

    private List<DocumentSnapshot> recent(QuerySnapshot snap) {
        List<DocumentSnapshot> sorted = new ArrayList<>(snap.getDocuments());
        // Keep descending chronological order without Java 8 collection APIs added in Android 24.
        Collections.sort(sorted, (left, right) -> {
            Timestamp leftCreated = left.getTimestamp("createdAt");
            Timestamp rightCreated = right.getTimestamp("createdAt");
            long leftTime = leftCreated == null ? 0L : leftCreated.toDate().getTime();
            long rightTime = rightCreated == null ? 0L : rightCreated.toDate().getTime();
            return Long.compare(rightTime, leftTime);
        });
        return sorted;
    }

    private void displayRequests(QuerySnapshot snapshot) {
        LinearLayout container = activity.findViewById(R.id.containerReaderOnlineRequests);
        container.removeAllViews();
        clockHandler.removeCallbacks(countdownPulse);
        activeCountdowns.clear();
        lastRequests = snapshot;
        List<DocumentSnapshot> records = recent(snapshot);
        int matching = 0, pending = 0, pickup = 0, borrowed = 0, completed = 0;
        for (DocumentSnapshot doc : records) {
            String state = doc.getString("status");
            if (visibleInMode(state)) matching++;
            if (OnlineLoanRequestRepository.PENDING.equals(state)) pending++;
            else if (OnlineLoanRequestRepository.APPROVED.equals(state)) pickup++;
            else if (OnlineLoanRequestRepository.COLLECTED.equals(state)) borrowed++;
            else completed++;
        }
        ((TextView) activity.findViewById(R.id.readerLoansTabBorrowed))
                .setText("Đang mượn (" + borrowed + ")");
        ((TextView) activity.findViewById(R.id.readerLoansTabPickup))
                .setText("Chờ lấy (" + pickup + ")");
        ((TextView) activity.findViewById(R.id.readerLoansTabPending))
                .setText("Chờ duyệt (" + pending + ")");
        ((TextView) activity.findViewById(R.id.readerLoansTabHistory))
                .setText("Lịch sử (" + completed + ")");
        message(R.id.textReaderOnlineSummary, matching == 0
                ? (BORROWED.equals(loanMode) ? "Bạn chưa có sách Online đang mượn."
                    : PICKUP.equals(loanMode) ? "Không có yêu cầu nào đang chờ nhận sách."
                    : PENDING_MODE.equals(loanMode) ? "Không có yêu cầu chờ duyệt."
                    : "Không có yêu cầu đã kết thúc.")
                : matching + " yêu cầu trong mục này"
                  + (snapshot.getMetadata().isFromCache()
                    ? " · Dữ liệu đã lưu, đang kiểm tra máy chủ" : ""));
        for (DocumentSnapshot doc : records) {
            if (!visibleInMode(doc.getString("status"))) continue;
            View item = activity.getLayoutInflater().inflate(
                    R.layout.item_reader_online_request, container, false);
            container.addView(item);
            String title = doc.getString("bookTitle");
            String status = doc.getString("status");
            ((TextView) item.findViewById(R.id.readerOnlineItemTitle))
                    .setText(title == null ? "Đầu sách" : title);
            ((TextView) item.findViewById(R.id.readerOnlineItemStatus))
                    .setText(OnlineLoanRequestRepository.label(status));
            Long days = doc.getLong("loanDays");
            ((TextView) item.findViewById(R.id.readerOnlineItemDays))
                    .setText("Thời hạn mượn yêu cầu: " + (days == null ? "—" : days) + " ngày");
            if (OnlineLoanRequestRepository.PENDING.equals(status)) {
                android.widget.Button cancel = item.findViewById(R.id.readerOnlineItemCancel);
                cancel.setVisibility(View.VISIBLE);
                cancel.setEnabled(!snapshot.getMetadata().isFromCache()
                        && !snapshot.getMetadata().hasPendingWrites());
                cancel.setOnClickListener(v -> {
                    if (!current(authorizedUid) || !cancel.isEnabled()) return;
                    String uid = authorizedUid;
                    new androidx.appcompat.app.AlertDialog.Builder(activity)
                            .setTitle("Hủy yêu cầu mượn Online?")
                            .setMessage("Yêu cầu đang chờ Admin duyệt sẽ được hủy. Không ảnh hưởng tồn kho.")
                            .setNegativeButton("Giữ yêu cầu", null)
                            .setPositiveButton("Hủy yêu cầu", (dialog, which) -> {
                                if (!current(uid)) return;
                                cancel.setEnabled(false);
                                repository.cancelPending(uid, doc.getString("bookTitleId"))
                                        .addOnCompleteListener(activity, task -> {
                                            if (!current(uid)) return;
                                            if (!task.isSuccessful()) {
                                                cancel.setEnabled(true);
                                                Toast.makeText(activity, "Không thể hủy: "
                                                        + (task.getException() == null
                                                          ? "Đơn đã thay đổi." : task.getException().getMessage()),
                                                        Toast.LENGTH_LONG).show();
                                            }
                                        });
                            }).show();
                });
            }
            if (OnlineLoanRequestRepository.APPROVED.equals(status)) {
                Timestamp hold = doc.getTimestamp("holdUntil");
                item.findViewById(R.id.readerOnlineItemApproved).setVisibility(View.VISIBLE);
                TextView copy = item.findViewById(R.id.readerOnlineItemCopy);
                // Approved means one title-level borrowing slot; no physical copy
                // has been assigned yet. Legacy approved holds may still show copyId.
                String heldCopyId = doc.getString("copyId");
                if (heldCopyId == null) {
                    copy.setVisibility(View.VISIBLE);
                    copy.setText("Đã giữ suất mượn · Mã cuốn sẽ xác định khi nhận tại quầy");
                } else {
                    copy.setVisibility(View.VISIBLE);
                    copy.setText("Mã cuốn đã giữ (đơn cũ): " + heldCopyId);
                }
                TextView holdTime = item.findViewById(R.id.readerOnlineItemHold);
                holdTime.setVisibility(View.VISIBLE);
                holdTime.setText("Nhận trước: " + time(hold));
                TextView countdown = item.findViewById(R.id.readerOnlineItemCountdown);
                countdown.setVisibility(View.VISIBLE);
                countdown.setText(remainingText(hold));
                item.findViewById(R.id.readerOnlineItemHelp).setVisibility(View.VISIBLE);
                if (hold != null) activeCountdowns.put(countdown, hold);
            }
            if (OnlineLoanRequestRepository.COLLECTED.equals(status)
                    || OnlineLoanRequestRepository.RETURNED.equals(status)) {
                TextView checkout = item.findViewById(R.id.readerOnlineItemCheckout);
                checkout.setVisibility(View.VISIBLE);
                checkout.setText("Đã nhận sách tại quầy: " + time(doc.getTimestamp("collectedAt")));
                TextView due = item.findViewById(R.id.readerOnlineItemDue);
                due.setVisibility(View.VISIBLE);
                due.setText("Hạn trả: " + time(doc.getTimestamp("dueAt")));
                if (OnlineLoanRequestRepository.RETURNED.equals(status)) {
                    TextView returned = item.findViewById(R.id.readerOnlineItemReturned);
                    returned.setVisibility(View.VISIBLE);
                    returned.setText("Đã trả: " + time(doc.getTimestamp("returnedAt")));
                }
            }
            if (OnlineLoanRequestRepository.REJECTED.equals(status)
                    && doc.getString("rejectionReason") != null) {
                TextView rejection = item.findViewById(R.id.readerOnlineItemReject);
                rejection.setText("Lý do: " + doc.getString("rejectionReason"));
                rejection.setVisibility(View.VISIBLE);
            }
            ((TextView) item.findViewById(R.id.readerOnlineItemSent))
                    .setText("Gửi: " + time(doc.getTimestamp("createdAt")));
        }
        if (activity.notificationSettings() != null)
            activity.notificationSettings().updateOnlineRequests(records);
        if (!activeCountdowns.isEmpty() && current(authorizedUid))
            clockHandler.postDelayed(countdownPulse, 60_000L);
    }

    private void displayReceipts(QuerySnapshot snapshot) {
        LinearLayout container = activity.findViewById(R.id.containerReaderOnlineReceipts);
        container.removeAllViews();
        List<DocumentSnapshot> receipts = new ArrayList<>(snapshot.getDocuments());
        Collections.sort(receipts, (a, b) -> {
            Timestamp left = a.getTimestamp("collectedAt"), right = b.getTimestamp("collectedAt");
            return Long.compare(right == null ? 0 : right.toDate().getTime(),
                    left == null ? 0 : left.toDate().getTime());
        });
        int completed = 0;
        for (DocumentSnapshot receipt : receipts)
            if (OnlineLoanRequestRepository.RETURNED.equals(receipt.getString("status")))
                completed++;
        message(R.id.textReaderOnlineReceiptsSummary, completed == 0
                ? "Chưa có lượt mượn Online nào đã trả."
                : completed + " lượt mượn Online đã hoàn tất"
                  + (snapshot.getMetadata().isFromCache() ? " · Dữ liệu đã lưu" : ""));
        for (DocumentSnapshot receipt : receipts) {
            // An open receipt is already visible under 'Đang mượn'.
            if (!OnlineLoanRequestRepository.RETURNED.equals(receipt.getString("status")))
                continue;
            View card = activity.getLayoutInflater().inflate(
                    R.layout.item_reader_online_receipt, container, false);
            container.addView(card);
            String name = receipt.getString("bookTitle");
            ((TextView) card.findViewById(R.id.readerOnlineReceiptTitle))
                    .setText(name == null ? "Đầu sách" : name);
            ((TextView) card.findViewById(R.id.readerOnlineReceiptCopy))
                    .setText("Mã cuốn: " + receipt.getString("copyId"));
            ((TextView) card.findViewById(R.id.readerOnlineReceiptDates))
                    .setText("Nhận: " + time(receipt.getTimestamp("collectedAt"))
                            + "\nHạn trả: " + time(receipt.getTimestamp("dueAt")));
            boolean returned = OnlineLoanRequestRepository.RETURNED.equals(
                    receipt.getString("status"));
            ((TextView) card.findViewById(R.id.readerOnlineReceiptStatus))
                    .setText(returned ? "Đã trả: " + time(receipt.getTimestamp("returnedAt"))
                            : "Đang mượn · Chưa ghi nhận trả");
        }
    }

    private void displayNotifications(QuerySnapshot snapshot) {
        LinearLayout container = activity.findViewById(R.id.containerReaderOnlineNotifications);
        container.removeAllViews();
        List<DocumentSnapshot> notifications = recent(snapshot);
        if (!snapshot.getMetadata().isFromCache()) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (DocumentSnapshot d : notifications) ids.add(d.getId());
            OnlineLoanAlertCenter.registerSnapshot(activity, authorizedUid, false, ids);
            for (DocumentSnapshot d : notifications)
                if (d.get("readAt") == null)
                    OnlineLoanAlertCenter.notifyNew(activity, authorizedUid, false,
                            d.getId(), d.getString("title"), d.getString("message"));
        }
        int unread = 0;
        for (DocumentSnapshot doc : notifications)
            if (doc.get("readAt") == null) unread++;
        if (activity.notificationSettings() != null)
            activity.notificationSettings().setOnlineUnread(unread);
        message(R.id.textReaderOnlineNotificationsSummary,
                notifications.isEmpty() ? "Chưa có thông báo mượn Online."
                : notifications.size() + " thông báo · " + unread + " chưa đọc"
                + (snapshot.getMetadata().isFromCache() ? " · Bản đã lưu" : ""));
        for (DocumentSnapshot notification : notifications) {
            View item = activity.getLayoutInflater().inflate(
                    R.layout.item_reader_online_notification, container, false);
            container.addView(item);
            ((TextView) item.findViewById(R.id.readerOnlineNotTitle)).setText(
                    notification.getString("title") == null
                            ? "Thông báo thư viện" : notification.getString("title"));
            ((TextView) item.findViewById(R.id.readerOnlineNotMessage)).setText(
                    notification.getString("message") == null
                            ? "" : notification.getString("message"));
            ((TextView) item.findViewById(R.id.readerOnlineNotDate)).setText(
                    time(notification.getTimestamp("createdAt")));
            item.setAlpha(notification.get("readAt") == null ? 1f : 0.75f);
            if (notification.get("readAt") == null) {
                TextView mark = item.findViewById(R.id.readerOnlineNotRead);
                mark.setVisibility(View.VISIBLE);
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
                            });
                });
            } else item.findViewById(R.id.readerOnlineNotDone).setVisibility(View.VISIBLE);
        }
    }
}
