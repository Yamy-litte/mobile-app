package com.example.libraryonline.data.local.dao;

import static org.junit.Assert.*;

import android.database.sqlite.SQLiteDatabase;
import java.util.Calendar;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Dashboard must never substitute Figma example values for actual SQLite counts. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class AdminDashboardStatsDaoTest {
    private SQLiteDatabase db;

    @Before public void setUp() {
        db = SQLiteDatabase.create(null);
        db.execSQL("CREATE TABLE LoanDetails (loanDetailId TEXT, loanReceiptId TEXT, copyId TEXT, borrowDate TEXT, dueDate TEXT, returnDate TEXT, detailStatus TEXT, createdAt TEXT)");
        db.execSQL("CREATE TABLE LoanReceipts (loanReceiptId TEXT, userId TEXT)");
        db.execSQL("CREATE TABLE BookCopies (copyId TEXT, bookTitleId TEXT, copyStatus TEXT, isHidden INTEGER)");
        db.execSQL("CREATE TABLE BookTitles (bookTitleId TEXT, title TEXT)");
        db.execSQL("CREATE TABLE Users (userId TEXT, fullName TEXT)");
    }

    @After public void tearDown() {
        db.close();
    }

    private static Calendar day() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.OCTOBER, 22, 12, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    @Test public void emptyDatabaseShowsZerosRatherThanMockDashboardCounts() {
        AdminDashboardStatsDao.Summary result = new AdminDashboardStatsDao(db).load(day());
        assertEquals(0, result.borrowedToday);
        assertEquals(0, result.returnedToday);
        assertEquals(0, result.awaitingPickup);
        assertEquals(0, result.overdue);
        assertEquals(7, result.dailyBorrowed.length);
        for (int n : result.dailyBorrowed) assertEquals(0, n);
        assertTrue(result.recentActivities.isEmpty());
    }

    @Test public void calculatesActualTodayAndMondayBasedWeekFromLocalLoans() {
        db.execSQL("INSERT INTO Users VALUES ('u1','Độc giả kiểm thử')");
        db.execSQL("INSERT INTO BookTitles VALUES ('b1','Sách kiểm thử')");
        db.execSQL("INSERT INTO BookCopies VALUES ('c1','b1','ChoLay',0)");
        db.execSQL("INSERT INTO LoanReceipts VALUES ('r1','u1')");
        db.execSQL("INSERT INTO LoanDetails VALUES ('l1','r1','c1','2026-10-22','2026-10-21',NULL,'DangMuon','2026-10-22T10:00:00')");
        db.execSQL("INSERT INTO LoanDetails VALUES ('l2','r1','c1','2026-10-20','2026-10-23','2026-10-22','DaTra','2026-10-20T10:00:00')");
        AdminDashboardStatsDao.Summary result = new AdminDashboardStatsDao(db).load(day());
        assertEquals(1, result.borrowedToday);
        assertEquals(1, result.returnedToday);
        assertEquals(1, result.awaitingPickup);
        assertEquals(1, result.overdue);
        assertEquals(1, result.dailyBorrowed[1]); // Tuesday
        assertEquals(1, result.dailyBorrowed[3]); // Thursday
        assertEquals(2, result.recentActivities.size());
    }
}
