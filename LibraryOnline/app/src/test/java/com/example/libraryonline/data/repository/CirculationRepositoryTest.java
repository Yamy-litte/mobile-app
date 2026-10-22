package com.example.libraryonline.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.libraryonline.data.local.db.DatabaseHelper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class CirculationRepositoryTest {

    @Test
    public void receivedReservationDoesNotBlockBorrowingSameTitleAgain() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);

        DatabaseHelper seedHelper = new DatabaseHelper(context);
        SQLiteDatabase db = seedHelper.getWritableDatabase();
        String now = "2026-10-22T10:00:00";

        ContentValues reader = new ContentValues();
        reader.put("userId", "reader-1");
        reader.put("fullName", "Reader One");
        reader.put("email", "reader-one@example.test");
        reader.put("barcode", "CARD-1");
        reader.put("role", "Reader");
        reader.put("cardStatus", "HoatDong");
        reader.put("isDeleted", 0);
        reader.put("createdAt", now);
        reader.put("updatedAt", now);
        db.insertOrThrow("Users", null, reader);

        ContentValues adminUser = new ContentValues();
        adminUser.put("userId", "admin-auth-1");
        adminUser.put("fullName", "Admin One");
        adminUser.put("email", "admin-one@example.test");
        adminUser.put("role", "Admin");
        adminUser.put("cardStatus", "HoatDong");
        adminUser.put("isDeleted", 0);
        adminUser.put("createdAt", now);
        adminUser.put("updatedAt", now);
        db.insertOrThrow("Users", null, adminUser);

        ContentValues admin = new ContentValues();
        admin.put("adminId", "admin-1");
        admin.put("userId", "admin-auth-1");
        admin.put("employeeCode", "EMP-1");
        admin.put("adminStatus", "Active");
        admin.put("createdAt", now);
        admin.put("updatedAt", now);
        db.insertOrThrow("Admins", null, admin);

        ContentValues title = new ContentValues();
        title.put("bookTitleId", "title-1");
        title.put("title", "Test title");
        title.put("totalCopies", 1);
        title.put("availableCopies", 1);
        title.put("isHidden", 0);
        title.put("createdAt", now);
        title.put("updatedAt", now);
        db.insertOrThrow("BookTitles", null, title);

        ContentValues copy = new ContentValues();
        copy.put("copyId", "copy-1");
        copy.put("bookTitleId", "title-1");
        copy.put("barcode", "BOOK-1");
        copy.put("copyStatus", "SanSang");
        copy.put("isHidden", 0);
        copy.put("createdAt", now);
        copy.put("updatedAt", now);
        db.insertOrThrow("BookCopies", null, copy);

        ContentValues receivedReservation = new ContentValues();
        receivedReservation.put("reservationId", "reservation-1");
        receivedReservation.put("userId", "reader-1");
        receivedReservation.put("bookTitleId", "title-1");
        receivedReservation.putNull("copyId");
        receivedReservation.put("reservationType", "DatTruoc");
        receivedReservation.put("reservationStatus", "Received");
        receivedReservation.put("requestedLoanDays", 7);
        receivedReservation.put("createdAt", now);
        db.insertOrThrow("Reservations", null, receivedReservation);
        seedHelper.close();

        CirculationRepository repository = new CirculationRepository(context);
        try {
            CirculationRepository.OperationResult result =
                    repository.borrow("CARD-1", "BOOK-1", 7, "admin-auth-1");
            assertNotNull(result);
            assertAvailableCopies(context, 0);

            CirculationRepository.OperationResult returned =
                    repository.returnCopy("CARD-1", "BOOK-1", "admin-auth-1");
            assertNotNull(returned);
            assertAvailableCopies(context, 1);
        } finally {
            repository.close();
            context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void returningCopyAssignsItToOldestWaitingReservation() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);

        DatabaseHelper seedHelper = new DatabaseHelper(context);
        SQLiteDatabase db = seedHelper.getWritableDatabase();
        String now = "2026-10-22T10:00:00";

        ContentValues reader = new ContentValues();
        reader.put("userId", "reader-1");
        reader.put("fullName", "Reader One");
        reader.put("email", "reader-one@example.test");
        reader.put("barcode", "CARD-1");
        reader.put("role", "Reader");
        reader.put("cardStatus", "HoatDong");
        reader.put("isDeleted", 0);
        reader.put("createdAt", now);
        reader.put("updatedAt", now);
        db.insertOrThrow("Users", null, reader);

        ContentValues adminUser = new ContentValues();
        adminUser.put("userId", "admin-auth-1");
        adminUser.put("fullName", "Admin One");
        adminUser.put("email", "admin-one@example.test");
        adminUser.put("role", "Admin");
        adminUser.put("cardStatus", "HoatDong");
        adminUser.put("isDeleted", 0);
        adminUser.put("createdAt", now);
        adminUser.put("updatedAt", now);
        db.insertOrThrow("Users", null, adminUser);

        ContentValues admin = new ContentValues();
        admin.put("adminId", "admin-1");
        admin.put("userId", "admin-auth-1");
        admin.put("employeeCode", "EMP-1");
        admin.put("adminStatus", "Active");
        admin.put("createdAt", now);
        admin.put("updatedAt", now);
        db.insertOrThrow("Admins", null, admin);

        ContentValues title = new ContentValues();
        title.put("bookTitleId", "title-1");
        title.put("title", "Test title");
        title.put("totalCopies", 1);
        title.put("availableCopies", 1);
        title.put("isHidden", 0);
        title.put("createdAt", now);
        title.put("updatedAt", now);
        db.insertOrThrow("BookTitles", null, title);

        ContentValues copy = new ContentValues();
        copy.put("copyId", "copy-1");
        copy.put("bookTitleId", "title-1");
        copy.put("barcode", "BOOK-1");
        copy.put("copyStatus", "SanSang");
        copy.put("isHidden", 0);
        copy.put("createdAt", now);
        copy.put("updatedAt", now);
        db.insertOrThrow("BookCopies", null, copy);

        ContentValues receivedReservation = new ContentValues();
        receivedReservation.put("reservationId", "reservation-1");
        receivedReservation.put("userId", "reader-1");
        receivedReservation.put("bookTitleId", "title-1");
        receivedReservation.putNull("copyId");
        receivedReservation.put("reservationType", "DatTruoc");
        receivedReservation.put("reservationStatus", "Received");
        receivedReservation.put("requestedLoanDays", 7);
        receivedReservation.put("createdAt", now);
        db.insertOrThrow("Reservations", null, receivedReservation);

        ContentValues waitingReader = new ContentValues();
        waitingReader.put("userId", "reader-2");
        waitingReader.put("fullName", "Reader Two");
        waitingReader.put("email", "reader-two@example.test");
        waitingReader.put("barcode", "CARD-2");
        waitingReader.put("role", "Reader");
        waitingReader.put("cardStatus", "HoatDong");
        waitingReader.put("isDeleted", 0);
        waitingReader.put("createdAt", now);
        waitingReader.put("updatedAt", now);
        db.insertOrThrow("Users", null, waitingReader);

        ContentValues queuedReservation = new ContentValues();
        queuedReservation.put("reservationId", "reservation-queue-1");
        queuedReservation.put("userId", "reader-2");
        queuedReservation.put("bookTitleId", "title-1");
        queuedReservation.putNull("copyId");
        queuedReservation.put("reservationType", "DatTruoc");
        queuedReservation.put("reservationStatus", "Pending");
        queuedReservation.put("queuePosition", 1);
        queuedReservation.put("requestedLoanDays", 7);
        queuedReservation.put("createdAt", now);
        db.insertOrThrow("Reservations", null, queuedReservation);
        seedHelper.close();

        CirculationRepository repository = new CirculationRepository(context);
        try {
            CirculationRepository.OperationResult result =
                    repository.borrow("CARD-1", "BOOK-1", 7, "admin-auth-1");
            assertNotNull(result);
            assertAvailableCopies(context, 0);

            CirculationRepository.OperationResult returned =
                    repository.returnCopy("CARD-1", "BOOK-1", "admin-auth-1");
            assertNotNull(returned);
            assertAvailableCopies(context, 0);
            assertTrue(returned.message.contains("Đã ưu tiên cuốn sách cho độc giả đang chờ đến"));
            assertDatabaseValue(context,
                    "SELECT copyStatus FROM BookCopies WHERE copyId='copy-1'", "DaDatTruoc");
            assertDatabaseValue(context,
                    "SELECT priorityUserId FROM BookCopies WHERE copyId='copy-1'", "reader-2");
            assertDatabaseValue(context,
                    "SELECT reservationStatus FROM Reservations WHERE reservationId='reservation-queue-1'", "Ready");
            assertDatabaseValue(context,
                    "SELECT userId FROM Notifications WHERE relatedEntityId='reservation-queue-1'", "reader-2");

            try {
                repository.borrow("CARD-1", "BOOK-1", 7, "admin-auth-1");
                throw new AssertionError("A different reader must not borrow a reserved copy");
            } catch (IllegalArgumentException expected) {
                assertEquals("Cuốn sách được ưu tiên cho độc giả đặt trước khác.", expected.getMessage());
            }

            CirculationRepository.OperationResult priorityBorrow =
                    repository.borrow("CARD-2", "BOOK-1", 7, "admin-auth-1");
            assertNotNull(priorityBorrow);
            assertDatabaseValue(context,
                    "SELECT reservationStatus FROM Reservations WHERE reservationId='reservation-queue-1'", "Completed");
        } finally {
            repository.close();
            context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        }
    }


    @Test
    public void expiredReservationHoldAdvancesQueueOnReturn() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);

        DatabaseHelper seedHelper = new DatabaseHelper(context);
        SQLiteDatabase db = seedHelper.getWritableDatabase();
        String now = "2026-10-22T10:00:00";

        for (int i = 1; i <= 3; i++) {
            ContentValues reader = new ContentValues();
            reader.put("userId", "reader-" + i);
            reader.put("fullName", "Reader " + i);
            reader.put("email", "reader-" + i + "@example.test");
            reader.put("barcode", "CARD-" + i);
            reader.put("role", "Reader");
            reader.put("cardStatus", "HoatDong");
            reader.put("isDeleted", 0);
            reader.put("createdAt", now);
            reader.put("updatedAt", now);
            db.insertOrThrow("Users", null, reader);
        }

        ContentValues adminUser = new ContentValues();
        adminUser.put("userId", "admin-auth-1");
        adminUser.put("fullName", "Admin One");
        adminUser.put("email", "admin-one@example.test");
        adminUser.put("role", "Admin");
        adminUser.put("cardStatus", "HoatDong");
        adminUser.put("isDeleted", 0);
        adminUser.put("createdAt", now);
        adminUser.put("updatedAt", now);
        db.insertOrThrow("Users", null, adminUser);

        ContentValues admin = new ContentValues();
        admin.put("adminId", "admin-1");
        admin.put("userId", "admin-auth-1");
        admin.put("employeeCode", "EMP-1");
        admin.put("adminStatus", "Active");
        admin.put("createdAt", now);
        admin.put("updatedAt", now);
        db.insertOrThrow("Admins", null, admin);

        ContentValues title = new ContentValues();
        title.put("bookTitleId", "title-1");
        title.put("title", "Test title");
        title.put("totalCopies", 2);
        title.put("availableCopies", 0);
        title.put("isHidden", 0);
        title.put("createdAt", now);
        title.put("updatedAt", now);
        db.insertOrThrow("BookTitles", null, title);

        for (int i = 1; i <= 2; i++) {
            ContentValues copy = new ContentValues();
            copy.put("copyId", "copy-" + i);
            copy.put("bookTitleId", "title-1");
            copy.put("barcode", "BOOK-" + i);
            copy.put("copyStatus", i == 1 ? "DaMuon" : "DaDatTruoc");
            copy.put("priorityUserId", i == 2 ? "reader-2" : null);
            copy.put("isHidden", 0);
            copy.put("createdAt", now);
            copy.put("updatedAt", now);
            db.insertOrThrow("BookCopies", null, copy);
        }

        ContentValues receipt = new ContentValues();
        receipt.put("loanReceiptId", "loan-1");
        receipt.put("userId", "reader-1");
        receipt.put("adminId", "admin-1");
        receipt.put("loanType", "TrucTiep");
        receipt.put("receiptStatus", "Open");
        receipt.put("createdAt", now);
        receipt.put("checkoutAt", now);
        db.insertOrThrow("LoanReceipts", null, receipt);

        ContentValues detail = new ContentValues();
        detail.put("loanDetailId", "detail-1");
        detail.put("loanReceiptId", "loan-1");
        detail.put("copyId", "copy-1");
        detail.put("borrowDate", "2026-10-20");
        detail.put("dueDate", "2026-11-03");
        detail.putNull("returnDate");
        detail.put("detailStatus", "DangMuon");
        detail.put("extensionCount", 0);
        detail.put("extended", 0);
        detail.put("createdAt", now);
        db.insertOrThrow("LoanDetails", null, detail);

        ContentValues expired = new ContentValues();
        expired.put("reservationId", "reservation-expired");
        expired.put("userId", "reader-2");
        expired.put("bookTitleId", "title-1");
        expired.put("copyId", "copy-2");
        expired.put("reservationType", "DatTruoc");
        expired.put("reservationStatus", "Ready");
        expired.put("requestedLoanDays", 7);
        expired.put("createdAt", now);
        expired.put("priorityUntil", "2000-01-01T00:00:00");
        expired.put("notifiedAt", now);
        db.insertOrThrow("Reservations", null, expired);

        ContentValues waiting = new ContentValues();
        waiting.put("reservationId", "reservation-next");
        waiting.put("userId", "reader-3");
        waiting.put("bookTitleId", "title-1");
        waiting.put("reservationType", "DatTruoc");
        waiting.put("reservationStatus", "Pending");
        waiting.put("queuePosition", 2);
        waiting.put("requestedLoanDays", 7);
        waiting.put("createdAt", now);
        db.insertOrThrow("Reservations", null, waiting);
        seedHelper.close();

        CirculationRepository repository = new CirculationRepository(context);
        try {
            CirculationRepository.OperationResult result =
                    repository.returnCopy("CARD-1", "BOOK-1", "admin-auth-1");
            assertNotNull(result);
            assertDatabaseValue(context,
                    "SELECT reservationStatus FROM Reservations WHERE reservationId='reservation-expired'", "Expired");
            assertDatabaseValue(context,
                    "SELECT reservationStatus FROM Reservations WHERE reservationId='reservation-next'", "Ready");
            assertDatabaseValue(context,
                    "SELECT copyId FROM Reservations WHERE reservationId='reservation-next'", "copy-2");
            assertDatabaseValue(context,
                    "SELECT priorityUserId FROM BookCopies WHERE copyId='copy-2'", "reader-3");
            assertDatabaseValue(context,
                    "SELECT copyStatus FROM BookCopies WHERE copyId='copy-2'", "DaDatTruoc");
            assertAvailableCopies(context, 1);
        } finally {
            repository.close();
            context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        }
    }


    @Test
    public void backgroundExpiryProcessorAdvancesExpiredReservationQueue() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);

        DatabaseHelper seedHelper = new DatabaseHelper(context);
        SQLiteDatabase db = seedHelper.getWritableDatabase();
        String now = "2026-10-22T10:00:00";

        for (int i = 2; i <= 3; i++) {
            ContentValues reader = new ContentValues();
            reader.put("userId", "reader-" + i);
            reader.put("fullName", "Reader " + i);
            reader.put("email", "reader-" + i + "@example.test");
            reader.put("barcode", "CARD-" + i);
            reader.put("role", "Reader");
            reader.put("cardStatus", "HoatDong");
            reader.put("isDeleted", 0);
            reader.put("createdAt", now);
            reader.put("updatedAt", now);
            db.insertOrThrow("Users", null, reader);
        }

        ContentValues title = new ContentValues();
        title.put("bookTitleId", "title-1");
        title.put("title", "Test title");
        title.put("totalCopies", 1);
        title.put("availableCopies", 0);
        title.put("isHidden", 0);
        title.put("createdAt", now);
        title.put("updatedAt", now);
        db.insertOrThrow("BookTitles", null, title);

        ContentValues copy = new ContentValues();
        copy.put("copyId", "copy-1");
        copy.put("bookTitleId", "title-1");
        copy.put("barcode", "BOOK-1");
        copy.put("copyStatus", "DaDatTruoc");
        copy.put("priorityUserId", "reader-2");
        copy.put("isHidden", 0);
        copy.put("createdAt", now);
        copy.put("updatedAt", now);
        db.insertOrThrow("BookCopies", null, copy);

        ContentValues expired = new ContentValues();
        expired.put("reservationId", "reservation-expired");
        expired.put("userId", "reader-2");
        expired.put("bookTitleId", "title-1");
        expired.put("copyId", "copy-1");
        expired.put("reservationType", "DatTruoc");
        expired.put("reservationStatus", "Ready");
        expired.put("requestedLoanDays", 7);
        expired.put("createdAt", now);
        expired.put("priorityUntil", "2000-01-01T00:00:00");
        expired.put("notifiedAt", now);
        db.insertOrThrow("Reservations", null, expired);

        ContentValues waiting = new ContentValues();
        waiting.put("reservationId", "reservation-next");
        waiting.put("userId", "reader-3");
        waiting.put("bookTitleId", "title-1");
        waiting.put("reservationType", "DatTruoc");
        waiting.put("reservationStatus", "Pending");
        waiting.put("queuePosition", 2);
        waiting.put("requestedLoanDays", 7);
        waiting.put("createdAt", now);
        db.insertOrThrow("Reservations", null, waiting);
        seedHelper.close();

        CirculationRepository repository = new CirculationRepository(context);
        try {
            repository.processExpiredReservationHolds();
            assertDatabaseValue(context,
                    "SELECT reservationStatus FROM Reservations WHERE reservationId='reservation-expired'", "Expired");
            assertDatabaseValue(context,
                    "SELECT reservationStatus FROM Reservations WHERE reservationId='reservation-next'", "Ready");
            assertDatabaseValue(context,
                    "SELECT copyId FROM Reservations WHERE reservationId='reservation-next'", "copy-1");
            assertDatabaseValue(context,
                    "SELECT priorityUserId FROM BookCopies WHERE copyId='copy-1'", "reader-3");
            assertDatabaseValue(context,
                    "SELECT copyStatus FROM BookCopies WHERE copyId='copy-1'", "DaDatTruoc");
            assertDatabaseValue(context,
                    "SELECT userId FROM Notifications WHERE relatedEntityId='reservation-next'", "reader-3");
            assertAvailableCopies(context, 0);
        } finally {
            repository.close();
            context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        }
    }

    private static void assertDatabaseValue(Context context, String sql, String expected) {
        DatabaseHelper helper = new DatabaseHelper(context);
        try (android.database.Cursor cursor = helper.getReadableDatabase().rawQuery(sql, null)) {
            if (!cursor.moveToFirst()) throw new AssertionError("Query returned no rows: " + sql);
            assertEquals(expected, cursor.getString(0));
        } finally {
            helper.close();
        }
    }

    private static void assertAvailableCopies(Context context, int expected) {
        DatabaseHelper helper = new DatabaseHelper(context);
        try (android.database.Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT availableCopies FROM BookTitles WHERE bookTitleId='title-1'", null)) {
            if (!cursor.moveToFirst()) throw new AssertionError("Book title was not persisted");
            org.junit.Assert.assertEquals(expected, cursor.getInt(0));
        } finally {
            helper.close();
        }
    }
}
