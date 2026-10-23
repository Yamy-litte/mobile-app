package com.example.libraryonline.data;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.dao.LoanDetailDao;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The unified Reader tabs only use actual handed-over and returned loans. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class ReaderLoanTabDataTest {
    private Context context;
    private DatabaseHelper helper;
    private SQLiteDatabase db;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        helper = new DatabaseHelper(context);
        db = helper.getWritableDatabase();
        db.execSQL("INSERT INTO Users(userId,fullName,email,createdAt,updatedAt) "
                + "VALUES('reader','Reader','reader@example.test','2026-01-01','2026-01-01')");
        db.execSQL("INSERT INTO BookTitles(bookTitleId,title,createdAt,updatedAt) "
                + "VALUES('title','Book','2026-01-01','2026-01-01')");
        for (int i = 1; i <= 3; i++) {
            String n = Integer.toString(i);
            db.execSQL("INSERT INTO BookCopies(copyId,bookTitleId,barcode,createdAt,updatedAt) "
                    + "VALUES('copy" + n + "','title','BC" + n + "','2026-01-01','2026-01-01')");
            db.execSQL("INSERT INTO LoanReceipts(loanReceiptId,userId,loanType,createdAt) "
                    + "VALUES('receipt" + n + "','reader','TrucTiep','2026-01-01')");
        }
        db.execSQL("INSERT INTO LoanDetails(loanDetailId,loanReceiptId,copyId,dueDate,createdAt) "
                + "VALUES('active','receipt1','copy1','2026-11-01','2026-01-01')");
        db.execSQL("INSERT INTO LoanDetails(loanDetailId,loanReceiptId,copyId,dueDate,returnDate,detailStatus,createdAt) "
                + "VALUES('returned','receipt2','copy2','2026-10-10','2026-10-09','DaTra','2026-01-01')");
        db.execSQL("INSERT INTO LoanDetails(loanDetailId,loanReceiptId,copyId,dueDate,returnDate,detailStatus,createdAt) "
                + "VALUES('legacy','receipt3','copy3','2026-10-01','2026-10-09','Mat','2026-01-01')");
    }

    @After public void tearDown() {
        if (helper != null) helper.close();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }

    @Test public void borrowedAndReturnedTabsUseDisjointLoanDetails() {
        LoanDetailDao dao = new LoanDetailDao(db);
        assertEquals(1, dao.findActiveByUser("reader").size());
        assertEquals("active", dao.findActiveByUser("reader").get(0).loanDetailId);
        assertEquals(1, dao.findReturnedByUser("reader").size());
        assertEquals("returned", dao.findReturnedByUser("reader").get(0).loanDetailId);
        assertEquals(0, dao.findReturnedByUser("another-user").size());
    }
}
