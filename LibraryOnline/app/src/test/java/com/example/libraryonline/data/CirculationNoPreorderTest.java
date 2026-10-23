package com.example.libraryonline.data;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.repository.CirculationRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Regression: removing preorders must not change archived data or Online handover. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class CirculationNoPreorderTest {
    private Context context;
    private DatabaseHelper helper;
    private SQLiteDatabase db;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        helper = new DatabaseHelper(context);
        db = helper.getWritableDatabase();
        db.execSQL("INSERT INTO Users(userId,fullName,email,barcode,role,cardStatus,createdAt,updatedAt) "
                + "VALUES('reader','Reader','reader@test.example','TV-READER','Reader','HoatDong','2026-01-01','2026-01-01')");
        db.execSQL("INSERT INTO BookTitles(bookTitleId,title,totalCopies,availableCopies,createdAt,updatedAt) "
                + "VALUES('title','Test book',1,0,'2026-01-01','2026-01-01')");
        db.execSQL("INSERT INTO BookCopies(copyId,bookTitleId,barcode,copyStatus,createdAt,updatedAt) "
                + "VALUES('copy','title','BC-COPY','DaMuon','2026-01-01','2026-01-01')");
        db.execSQL("INSERT INTO Reservations(reservationId,userId,bookTitleId,reservationType,reservationStatus,createdAt) "
                + "VALUES('queue','reader','title','DatTruoc','Pending','2026-01-01')");
    }

    private String singleValue(String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

    @After public void tearDown() {
        if (helper != null) helper.close();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }

    @Test public void returnNeverAssignsBookToOldPreorderQueue() {
        db.execSQL("INSERT INTO LoanReceipts(loanReceiptId,userId,loanType,createdAt) "
                + "VALUES('receipt','reader','TrucTiep','2026-01-01')");
        db.execSQL("INSERT INTO LoanDetails(loanDetailId,loanReceiptId,copyId,borrowDate,dueDate,createdAt) "
                + "VALUES('detail','receipt','copy','2026-01-01','2099-01-01','2026-01-01')");
        try (CirculationRepository repository = new CirculationRepository(context)) {
            repository.returnCopy("TV-READER", "BC-COPY", "active-admin");
        }
        assertEquals("SanSang", singleValue("SELECT copyStatus FROM BookCopies WHERE copyId='copy'"));
        assertEquals("Pending", singleValue("SELECT reservationStatus FROM Reservations WHERE reservationId='queue'"));
        assertEquals("0", singleValue("SELECT CAST(COUNT(*) AS TEXT) FROM Notifications WHERE notificationType='ReservationReady'"));
    }

    @Test public void historicalPreorderDoesNotBlockNewDirectCheckout() {
        db.execSQL("UPDATE BookCopies SET copyStatus='SanSang' WHERE copyId='copy'");
        try (CirculationRepository repository = new CirculationRepository(context)) {
            repository.borrow("TV-READER", "BC-COPY", 7, "active-admin");
        }
        assertEquals("DaMuon", singleValue("SELECT copyStatus FROM BookCopies WHERE copyId='copy'"));
        assertEquals("Pending", singleValue("SELECT reservationStatus FROM Reservations WHERE reservationId='queue'"));
    }

    @Test public void onlineHoldMustGoThroughFirestoreHandover() {
        db.execSQL("UPDATE BookCopies SET copyStatus='ChoLay', priorityUserId='reader' WHERE copyId='copy'");
        try (CirculationRepository repository = new CirculationRepository(context)) {
            try {
                repository.borrow("TV-READER", "BC-COPY", 7, "active-admin");
                fail("Local counter must not consume a Firebase online hold.");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("Online"));
            }
        }
    }
}
