package com.example.libraryonline.data.local.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Read-only statistics for transactions on this device's SQLite database.
 * These totals are not consolidated Firestore statistics.
 */
public final class AdminDashboardStatsDao {
    private final SQLiteDatabase db;

    public AdminDashboardStatsDao(SQLiteDatabase db) { this.db = db; }

    public static final class Summary {
        public final int borrowedToday, returnedToday, awaitingPickup, overdue;
        public final int[] dailyBorrowed;
        public final List<String> recentActivities;

        Summary(int borrowed, int returned, int awaiting, int overdue,
                int[] week, List<String> recent) {
            borrowedToday = borrowed;
            returnedToday = returned;
            awaitingPickup = awaiting;
            this.overdue = overdue;
            dailyBorrowed = week;
            recentActivities = recent;
        }
    }

    public Summary load(Calendar clock) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        String today = formatter.format(clock.getTime());
        int borrowed = count("SELECT COUNT(*) FROM LoanReceipts "
                + "WHERE substr(createdAt,1,10)=? AND receiptStatus != 'Cancelled'",
                new String[]{today});
        int returned = count("SELECT COUNT(*) FROM LoanDetails WHERE returnDate=?", new String[]{today});
        int awaiting = count("SELECT COUNT(*) FROM BookCopies WHERE copyStatus='ChoLay' AND isHidden=0", null);
        int overdue = count("SELECT COUNT(*) FROM LoanDetails WHERE returnDate IS NULL "
                + "AND dueDate < ? AND detailStatus IN ('DangMuon','QuaHan')", new String[]{today});

        Calendar monday = (Calendar) clock.clone();
        monday.add(Calendar.DAY_OF_MONTH, -((monday.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        String[] dates = new String[7];
        for (int i = 0; i < dates.length; i++) {
            dates[i] = formatter.format(monday.getTime());
            monday.add(Calendar.DAY_OF_MONTH, 1);
        }
        int[] weekly = new int[7];
        try (Cursor c = db.rawQuery("SELECT borrowDate, COUNT(*) FROM LoanDetails "
                        + "WHERE borrowDate BETWEEN ? AND ? GROUP BY borrowDate",
                new String[]{dates[0], dates[6]})) {
            while (c.moveToNext()) {
                for (int i = 0; i < dates.length; i++) {
                    if (dates[i].equals(c.getString(0))) {
                        weekly[i] = c.getInt(1);
                        break;
                    }
                }
            }
        }

        List<String> recent = new ArrayList<>();
        String sql = "SELECT COALESCE(NULLIF(u.fullName,''),'Độc giả'), "
                + "COALESCE(NULLIF(t.title,''),'Sách thư viện'), d.borrowDate, d.returnDate "
                + "FROM LoanDetails d JOIN LoanReceipts r ON r.loanReceiptId=d.loanReceiptId "
                + "LEFT JOIN Users u ON u.userId=r.userId "
                + "LEFT JOIN BookCopies c ON c.copyId=d.copyId "
                + "LEFT JOIN BookTitles t ON t.bookTitleId=c.bookTitleId "
                + "ORDER BY CASE WHEN d.returnDate IS NOT NULL "
                + "THEN d.returnDate || 'T23:59:59' ELSE d.createdAt END DESC LIMIT 3";
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                String reader = c.getString(0), book = c.getString(1);
                String whenBorrowed = c.getString(2), whenReturned = c.getString(3);
                if (whenReturned != null && !whenReturned.isEmpty())
                    recent.add(reader + " • Đã trả " + book + " (" + whenReturned + ")");
                else
                    recent.add(reader + " • Đã mượn " + book
                            + (whenBorrowed == null ? "" : " (" + whenBorrowed + ")"));
            }
        }
        return new Summary(borrowed, returned, awaiting, overdue, weekly, recent);
    }

    private int count(String sql, String[] args) {
        try (Cursor c = db.rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
}
