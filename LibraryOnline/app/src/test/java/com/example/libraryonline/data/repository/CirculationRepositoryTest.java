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
