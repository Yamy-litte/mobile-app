package com.example.libraryonline.data;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.repository.ReaderHistoryLocalRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Reader history is scoped to one UID and includes ONLY late-return fines. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class ReaderHistoryLocalRepositoryTest {
    private Context context;
    private DatabaseHelper helper;
    private SQLiteDatabase db;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        helper = new DatabaseHelper(context);
        db = helper.getWritableDatabase();
        for (int i = 1; i <= 2; i++) {
            String id = "reader" + i;
            db.execSQL("INSERT INTO Users(userId,fullName,email,createdAt,updatedAt) "
                    + "VALUES('" + id + "','Reader','" + id + "@test.example','2026-01-01','2026-01-01')");
        }
        db.execSQL("INSERT INTO BookTitles(bookTitleId,title,createdAt,updatedAt) "
                + "VALUES('title','Sample Book','2026-01-01','2026-01-01')");
        for (int i = 1; i <= 2; i++) {
            String n = Integer.toString(i);
            db.execSQL("INSERT INTO BookCopies(copyId,bookTitleId,barcode,createdAt,updatedAt) "
                    + "VALUES('copy" + n + "','title','B" + n + "','2026-01-01','2026-01-01')");
            db.execSQL("INSERT INTO LoanReceipts(loanReceiptId,userId,loanType,createdAt) "
                    + "VALUES('receipt" + n + "','reader" + n + "','TrucTiep','2026-01-01')");
            db.execSQL("INSERT INTO LoanDetails(loanDetailId,loanReceiptId,copyId,dueDate,createdAt) "
                    + "VALUES('detail" + n + "','receipt" + n + "','copy" + n + "','2026-02-01','2026-01-01')");
        }
        db.execSQL("INSERT INTO Penalties(penaltyId,userId,loanDetailId,copyId,penaltyType,daysLate,"
                + "amountDue,amountPaid,penaltyStatus,createdAt) "
                + "VALUES('fine1','reader1','detail1','copy1','LateReturn',2,10000,4000,'Partial','2026-02-03')");
        db.execSQL("INSERT INTO PenaltyPayments(paymentId,penaltyId,amountReceived,paymentMethod,"
                + "paymentStatus,receivedAt) "
                + "VALUES('payment1','fine1',4000,'Cash','Confirmed','2026-02-03')");
        db.execSQL("INSERT INTO Penalties(penaltyId,userId,loanDetailId,copyId,penaltyType,"
                + "amountDue,createdAt) "
                + "VALUES('oldLost','reader1','detail1','copy1','LostBook',50000,'2026-02-04')");
        db.execSQL("INSERT INTO Penalties(penaltyId,userId,loanDetailId,copyId,penaltyType,"
                + "amountDue,createdAt) "
                + "VALUES('otherFine','reader2','detail2','copy2','LateReturn',5000,'2026-02-04')");
    }

    @After public void teardown() {
        if (helper != null) helper.close();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }

    @Test public void retrievesOwnLoansLateFinesPaymentsAndOutstandingDebt() {
        try (ReaderHistoryLocalRepository repository = new ReaderHistoryLocalRepository(context)) {
            ReaderHistoryLocalRepository.Snapshot result = repository.load("reader1");
            assertEquals(1, result.loans.size());
            assertEquals("Sample Book", result.loans.get(0).title);
            assertEquals("receipt1", result.loans.get(0).receiptId);
            assertEquals(1, result.fines.size());
            assertEquals("fine1", result.fines.get(0).id);
            assertEquals(6000L, result.fines.get(0).remaining());
            assertEquals(1, result.fines.get(0).payments.size());
            assertEquals(4000L, result.fines.get(0).payments.get(0).amount);
            assertEquals("Confirmed", result.fines.get(0).payments.get(0).status);
            assertEquals(1, repository.load("reader2").fines.size());
        }
    }

    @Test public void emptyReaderCannotSeeOtherReaderData() {
        try (ReaderHistoryLocalRepository repository = new ReaderHistoryLocalRepository(context)) {
            ReaderHistoryLocalRepository.Snapshot result = repository.load("not-listed");
            assertTrue(result.loans.isEmpty());
            assertTrue(result.fines.isEmpty());
        }
    }
}
