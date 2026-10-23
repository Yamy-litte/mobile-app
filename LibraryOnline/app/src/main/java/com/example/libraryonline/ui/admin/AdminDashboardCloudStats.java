package com.example.libraryonline.ui.admin;

import android.app.Activity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Read-only dashboard data from the authoritative online-loan receipt history.
 * A request's mutable status must never replace OnlineLoanReceipts history:
 * the same Reader can submit and complete multiple attempts.
 *
 * Direct loans are stored in SQLite and are merged by AdminMainActivity;
 * online receipts are NOT also written to SQLite by the current checkout flow.
 */
final class AdminDashboardCloudStats {
    interface Callback {
        void complete(Summary summary, String error);
    }

    static final class ActivityEvent {
        final long at;
        final String description;

        ActivityEvent(long at, String description) {
            this.at = at;
            this.description = description;
        }
    }

    static final class Summary {
        final int borrowedToday, returnedToday, awaitingPickup, overdue;
        final int[] weekly;
        final List<ActivityEvent> recent;
        final boolean cached;

        Summary(int borrowedToday, int returnedToday, int awaitingPickup, int overdue,
                int[] weekly, List<ActivityEvent> recent, boolean cached) {
            this.borrowedToday = borrowedToday;
            this.returnedToday = returnedToday;
            this.awaitingPickup = awaitingPickup;
            this.overdue = overdue;
            this.weekly = weekly;
            this.recent = recent;
            this.cached = cached;
        }
    }

    private final FirebaseFirestore db;

    AdminDashboardCloudStats(FirebaseFirestore db) {
        this.db = db;
    }

    void load(Activity activity, Calendar clock, Callback callback) {
        Calendar today = (Calendar) clock.clone();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar monday = (Calendar) today.clone();
        monday.add(Calendar.DAY_OF_YEAR,
                -((monday.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        Timestamp startOfWeek = new Timestamp(monday.getTime());

        // Each query uses a single indexed Firestore field; no new composite
        // index or security-rule expansion is needed for an Admin.
        Task<QuerySnapshot> borrowedWeek = db.collection("OnlineLoanReceipts")
                .whereGreaterThanOrEqualTo("collectedAt", startOfWeek)
                .get(Source.DEFAULT);
        Task<QuerySnapshot> returnedWeek = db.collection("OnlineLoanReceipts")
                .whereGreaterThanOrEqualTo("returnedAt", startOfWeek)
                .get(Source.DEFAULT);
        Task<QuerySnapshot> openLoans = db.collection("OnlineLoanReceipts")
                .whereEqualTo("status", "Open").get(Source.DEFAULT);
        Task<QuerySnapshot> approved = db.collection("OnlineLoanRequests")
                .whereEqualTo("status", "Approved").get(Source.DEFAULT);
        Task<QuerySnapshot> lastBorrowed = db.collection("OnlineLoanReceipts")
                .orderBy("collectedAt", Query.Direction.DESCENDING)
                .limit(5).get(Source.DEFAULT);
        Task<QuerySnapshot> lastReturned = db.collection("OnlineLoanReceipts")
                .orderBy("returnedAt", Query.Direction.DESCENDING)
                .limit(5).get(Source.DEFAULT);

        List<Task<QuerySnapshot>> tasks = Arrays.asList(
                borrowedWeek, returnedWeek, openLoans, approved, lastBorrowed, lastReturned);
        Tasks.whenAllComplete(tasks).addOnCompleteListener(activity, done -> {
            for (Task<QuerySnapshot> query : tasks) {
                if (!query.isSuccessful() || query.getResult() == null) {
                    callback.complete(null, "Không đọc được lịch sử mượn Online trên Firebase.");
                    return;
                }
            }
            try {
                long dayStart = today.getTimeInMillis();
                Calendar tomorrow = (Calendar) today.clone();
                tomorrow.add(Calendar.DAY_OF_YEAR, 1);
                long dayEnd = tomorrow.getTimeInMillis();
                long now = clock.getTimeInMillis();
                int[] weekly = new int[7];
                int borrows = 0, returns = 0, overdue = 0, waiting = 0;
                long mondayMs = monday.getTimeInMillis();

                for (DocumentSnapshot receipt : borrowedWeek.getResult().getDocuments()) {
                    long at = time(receipt, "collectedAt");
                    if (at >= dayStart && at < dayEnd) borrows++;
                    if (at >= mondayMs && at <= now) {
                        Calendar day = (Calendar) monday.clone();
                        day.setTimeInMillis(at);
                        int index = (day.get(Calendar.DAY_OF_WEEK) + 5) % 7;
                        weekly[index]++;
                    }
                }
                for (DocumentSnapshot receipt : returnedWeek.getResult().getDocuments()) {
                    long at = time(receipt, "returnedAt");
                    if (at >= dayStart && at < dayEnd) returns++;
                }
                for (DocumentSnapshot receipt : openLoans.getResult().getDocuments()) {
                    long due = time(receipt, "dueAt");
                    if (due > 0 && due < now) overdue++;
                }
                for (DocumentSnapshot request : approved.getResult().getDocuments()) {
                    long holdUntil = time(request, "holdUntil");
                    // Old approved requests without a deadline are still included
                    // for review, but expired holds are not counted as ready.
                    if (holdUntil == 0 || holdUntil > now) waiting++;
                }
                List<ActivityEvent> recent = new ArrayList<>();
                for (DocumentSnapshot receipt : lastBorrowed.getResult().getDocuments()) {
                    long at = time(receipt, "collectedAt");
                    if (at > 0 && at <= now) recent.add(new ActivityEvent(at,
                            "Đã giao Online: " + title(receipt) + " · " + formatted(at)));
                }
                for (DocumentSnapshot receipt : lastReturned.getResult().getDocuments()) {
                    long at = time(receipt, "returnedAt");
                    if (at > 0 && at <= now) recent.add(new ActivityEvent(at,
                            "Đã trả Online: " + title(receipt) + " · " + formatted(at)));
                }
                Collections.sort(recent, (left, right) -> Long.compare(right.at, left.at));
                if (recent.size() > 10)
                    recent = new ArrayList<>(recent.subList(0, 10));

                boolean cached = false;
                for (Task<QuerySnapshot> query : tasks)
                    cached |= query.getResult().getMetadata().isFromCache()
                            || query.getResult().getMetadata().hasPendingWrites();
                callback.complete(new Summary(borrows, returns, waiting, overdue,
                        weekly, recent, cached), null);
            } catch (RuntimeException malformedData) {
                callback.complete(null, "Dữ liệu thống kê Online chưa hợp lệ.");
            }
        });
    }

    private static long time(DocumentSnapshot doc, String field) {
        Timestamp stamp = doc.getTimestamp(field);
        return stamp == null ? 0L : stamp.toDate().getTime();
    }

    private static String title(DocumentSnapshot doc) {
        String title = doc.getString("bookTitle");
        return title == null || title.trim().isEmpty() ? "Sách thư viện" : title;
    }

    private static String formatted(long at) {
        return new SimpleDateFormat("dd/MM HH:mm", new Locale("vi", "VN"))
                .format(new Date(at));
    }
}
