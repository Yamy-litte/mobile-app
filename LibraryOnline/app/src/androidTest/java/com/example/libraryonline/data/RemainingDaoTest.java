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

        RegistrationRequest request = new RegistrationRequest();
        request.requestId="req-1"; request.fullName="New Reader"; request.dateOfBirth="2001-01-01";
        request.email="new@test.example"; request.phone="0900000000"; request.createdAt="2026-10-20";
        assertTrue(new RegistrationRequestDao(db).insert(request)>0);
        assertEquals(1, new RegistrationRequestDao(db).findPending().size());

        BookTitle title = new BookTitle();
        title.bookTitleId="title-1"; title.title="Test Book"; title.totalCopies=1; title.availableCopies=1;
        title.createdAt="2026-10-20"; title.updatedAt="2026-10-20";
        assertTrue(new BookTitleDao(db).insert(title)>0);

        BookCopy copy = new BookCopy();
        copy.copyId="copy-1"; copy.bookTitleId="title-1"; copy.barcode="BC-1";
        copy.copyStatus="SanSang"; copy.createdAt="2026-10-20"; copy.updatedAt="2026-10-20";
        assertTrue(new BookCopyDao(db).insert(copy)>0);
        assertEquals(1, new BookCopyDao(db).findAvailableByTitle("title-1").size());

        LoanReceipt receipt = new LoanReceipt();
        receipt.loanReceiptId="receipt-1"; receipt.userId="u-1"; receipt.adminId="a-1";
        receipt.loanType="TrucTiep"; receipt.createdAt="2026-10-20";
        assertTrue(new LoanReceiptDao(db).insert(receipt)>0);
        assertEquals(1, new LoanReceiptDao(db).findByUser("u-1").size());

        LoanDetail detail = new LoanDetail();
        detail.loanDetailId="detail-1"; detail.loanReceiptId="receipt-1"; detail.copyId="copy-1";
        detail.borrowDate="2026-10-20"; detail.dueDate="2026-11-03"; detail.createdAt="2026-10-20";
        assertTrue(new LoanDetailDao(db).insert(detail)>0);
        assertEquals(1, new LoanDetailDao(db).findActiveByUser("u-1").size());

        Reservation reservation = new Reservation();
        reservation.reservationId="reservation-1"; reservation.userId="u-1"; reservation.bookTitleId="title-1";
        reservation.reservationType="Online"; reservation.createdAt="2026-10-20";
        assertTrue(new ReservationDao(db).insert(reservation)>0);
        assertEquals(1, new ReservationDao(db).findQueue("title-1").size());

        Penalty penalty = new Penalty();
        penalty.penaltyId="penalty-1"; penalty.userId="u-1"; penalty.copyId="copy-1";
        penalty.penaltyType="LateReturn"; penalty.daysLate=2; penalty.amountDue=10000;
        penalty.createdAt="2026-10-20";
        assertTrue(new PenaltyDao(db).insert(penalty)>0);
        assertEquals(1, new PenaltyDao(db).findUnpaidByUser("u-1").size());

        PenaltyPayment payment = new PenaltyPayment();
        payment.paymentId="payment-1"; payment.penaltyId="penalty-1"; payment.receivedBy="a-1";
        payment.amountReceived=5000; payment.paymentMethod="Cash"; payment.receivedAt="2026-10-20";
        assertTrue(new PenaltyPaymentDao(db).insert(payment)>0);
        assertEquals(1, new PenaltyPaymentDao(db).findByPenalty("penalty-1").size());

        LibraryNotification notification = new LibraryNotification();
        notification.notificationId="notification-1"; notification.userId="u-1";
        notification.notificationType="Test"; notification.title="Test"; notification.message="Test message";
        notification.scheduledAt="2026-10-20";
        assertTrue(new LibraryNotificationDao(db).insert(notification)>0);
        assertEquals(1, new LibraryNotificationDao(db).findByUser("u-1").size());
        assertEquals(1, new LibraryNotificationDao(db).markRead("notification-1", "2026-10-20"));
    }
}