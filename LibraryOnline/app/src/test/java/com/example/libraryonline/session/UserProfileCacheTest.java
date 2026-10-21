package com.example.libraryonline.session;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.UserDao;
import com.example.libraryonline.data.model.User;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28, manifest=Config.NONE)
public class UserProfileCacheTest {
    private Context context; private DatabaseHelper helper; private SQLiteDatabase db;
    @Before public void setUp() { context=RuntimeEnvironment.getApplication(); context.deleteDatabase(DatabaseHelper.DATABASE_NAME); helper=new DatabaseHelper(context); db=helper.getWritableDatabase(); }
    @After public void tearDown() { helper.close(); context.deleteDatabase(DatabaseHelper.DATABASE_NAME); }
    private User user(String uid, String email) { User u=new User(uid,"Nguyen Lan",email,"2026-01-01","2026-01-01"); u.cardStatus="HoatDong"; return u; }
    @Test public void repeatedSyncUpdatesTheSameUidAndPreservesLoanReferences() {
        UserDao dao=new UserDao(db); User u=user("reader","reader@example.com"); dao.upsertFromCloud(u);
        db.execSQL("INSERT INTO LoanReceipts(loanReceiptId,userId,loanType,receiptStatus,createdAt) VALUES('receipt','reader','Online','Open','2026-01-01')");
        u.fullName="Ten cap nhat"; dao.upsertFromCloud(u);
        assertEquals("Ten cap nhat",dao.findById("reader").fullName);
        assertEquals(1,dao.getActiveUsers().size());
        try(android.database.Cursor c=db.rawQuery("SELECT userId FROM LoanReceipts WHERE loanReceiptId='receipt'",null)) { assertTrue(c.moveToFirst()); assertEquals("reader",c.getString(0)); }
    }
    @Test public void conflictingEmailDoesNotReplaceAnotherIdentity() {
        UserDao dao=new UserDao(db); dao.upsertFromCloud(user("first","reader@example.com"));
        assertThrows(android.database.sqlite.SQLiteConstraintException.class,()->dao.upsertFromCloud(user("second","reader@example.com")));
        assertNotNull(dao.findById("first")); assertNull(dao.findById("second"));
    }
}
