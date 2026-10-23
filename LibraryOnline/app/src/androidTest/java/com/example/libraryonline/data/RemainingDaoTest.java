package com.example.libraryonline.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.*;
import com.example.libraryonline.data.model.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RemainingDaoTest {
    private Context context;
    private DatabaseHelper helper;
    private SQLiteDatabase db;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        helper = new DatabaseHelper(context);
        db = helper.getWritableDatabase();
    }

    @After public void tearDown() {
        if (helper != null) helper.close();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }

    @Test public void remainingDaosCanInsertAndReadRelatedRecords() {
        User user = new User("u-1", "Test Reader", "reader@test.example", "2000-01-01", "2026-10-20");
        assertTrue(new UserDao(db).insert(user) > 0);

        Admin admin = new Admin();
        admin.adminId="a-1"; admin.userId="u-1"; admin.employeeCode="EMP-1";
        admin.createdAt="2026-10-20"; admin.updatedAt="2026-10-20";
        assertTrue(new AdminDao(db).insert(admin)>0);
        assertEquals("EMP-1", new AdminDao(db).findByUserId("u-1").employeeCode);

        // Historical tables remain compatible even without unused DAO wrappers.
        db.execSQL("INSERT INTO RegistrationRequests(requestId,fullName,dateOfBirth,email,phone,createdAt) "
                + "VALUES('req-1','New Reader','2001-01-01','new@test.example','0900000000','2026-10-20')");
        assertEquals(1L, android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM RegistrationRequests", null));

        BookTitle title = new BookTitle();
        title.bookTitleId="title-1"; title.title="Test Book"; title.totalCopies=1; title.availableCopies=1;
        title.createdAt="2026-10-20"; title.updatedAt="2026-10-20";
        assertTrue(new BookTitleDao(db).insert(title)>0);

        BookCopy copy = new BookCopy();
        copy.copyId="copy-1"; copy.bookTitleId="title-1"; copy.barcode="BC-1";
        copy.copyStatus="SanSang"; copy.createdAt="2026-10-20"; copy.updatedAt="2026-10-20";
        assertTrue(new BookCopyDao(db).insert(copy)>0);
        assertEquals(1, new BookCopyDao(db).findAvailableByTitle("title-1").size());

        db.execSQL("INSERT INTO LoanReceipts(loanReceiptId,userId,adminId,loanType,createdAt) "
                + "VALUES('receipt-1','u-1','a-1','TrucTiep','2026-10-20')");
        assertEquals(1L, android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM LoanReceipts WHERE userId='u-1'", null));

        LoanDetail detail = new LoanDetail();
        detail.loanDetailId="detail-1"; detail.loanReceiptId="receipt-1"; detail.copyId="copy-1";
        detail.borrowDate="2026-10-20"; detail.dueDate="2026-11-03"; detail.createdAt="2026-10-20";
        assertTrue(new LoanDetailDao(db).insert(detail)>0);
        assertEquals(1, new LoanDetailDao(db).findActiveByUser("u-1").size());

        db.execSQL("INSERT INTO Reservations(reservationId,userId,bookTitleId,reservationType,reservationStatus,createdAt) "
                + "VALUES('reservation-1','u-1','title-1','Online','Pending','2026-10-20')");
        assertEquals(1L, android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM Reservations WHERE bookTitleId='title-1'", null));

        db.execSQL("INSERT INTO Penalties(penaltyId,userId,copyId,penaltyType,daysLate,amountDue,createdAt) "
                + "VALUES('penalty-1','u-1','copy-1','LateReturn',2,10000,'2026-10-20')");
        assertEquals(1L, android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM Penalties WHERE userId='u-1'", null));

        db.execSQL("INSERT INTO PenaltyPayments(paymentId,penaltyId,receivedBy,amountReceived,paymentMethod,receivedAt) "
                + "VALUES('payment-1','penalty-1','a-1',5000,'Cash','2026-10-20')");
        assertEquals(1L, android.database.DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM PenaltyPayments WHERE penaltyId='penalty-1'", null));

        LibraryNotification notification = new LibraryNotification();
        notification.notificationId="notification-1"; notification.userId="u-1";
        notification.notificationType="Test"; notification.title="Test"; notification.message="Test message";
        notification.scheduledAt="2026-10-20";
        assertTrue(new LibraryNotificationDao(db).insert(notification)>0);
        assertEquals(1, new LibraryNotificationDao(db).findByUser("u-1").size());
        assertEquals(1, new LibraryNotificationDao(db).markRead("notification-1", "2026-10-20"));
    }
}