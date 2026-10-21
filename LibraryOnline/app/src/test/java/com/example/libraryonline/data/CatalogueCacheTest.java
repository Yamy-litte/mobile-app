package com.example.libraryonline.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.CategoryDao;
import com.example.libraryonline.data.local.dao.BookTitleDao;
import com.example.libraryonline.data.model.BookTitle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class CatalogueCacheTest {
    private Context context;
    private DatabaseHelper helper;
    private SQLiteDatabase db;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        helper = new DatabaseHelper(context);
        db = helper.getWritableDatabase();
    }
    @After public void tearDown() {
        if (helper != null) helper.close();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }
    private BookTitle book(String id, String categoryId, String name) {
        BookTitle b = new BookTitle();
        b.bookTitleId = id;
        b.title = "Sách trong kho";
        b.author = "Tác giả";
        b.createdAt = "2026-10-21";
        b.updatedAt = "2026-10-21";
        b.categoryId = categoryId;
        b.category = name;
        b.totalCopies = 1;
        b.availableCopies = 1;
        return b;
    }
    @Test public void cloudCategoryIdCanShareDisplayNameWithAnExistingLocalId() {
        CategoryDao categories = new CategoryDao(db);
        categories.upsert("van-hoc", "Văn học", true);
        categories.upsert("CAT001", "Văn học", true);
        new BookTitleDao(db).upsertFromCloud(book("real-book", "CAT001", "Văn học"));
        assertEquals("CAT001", new BookTitleDao(db).findById("real-book").categoryId);
        assertEquals("Văn học", categories.getName("van-hoc"));
        assertEquals("Văn học", categories.getName("CAT001"));
    }
    @Test public void aBookSnapshotMustNotRenameOrReactivateItsCategory() {
        CategoryDao categories = new CategoryDao(db);
        categories.upsert("CAT001", "Thể loại từ Firebase", false);
        new BookTitleDao(db).upsertFromCloud(book("real-book", "CAT001", "CAT001"));
        assertEquals("Thể loại từ Firebase", categories.getName("CAT001"));
        assertFalse(categories.getAll().stream().anyMatch(c -> c.categoryId.equals("CAT001")));
        assertEquals("Thể loại từ Firebase", new BookTitleDao(db).findById("real-book").category);
    }
    @Test public void upgradingVersionSixPreservesCachedBooksAndTheirReferences() {
        new BookTitleDao(db).insert(book("existing-book", "van-hoc", "Văn học"));
        helper.close();
        // Restore the exact v6 category constraint to exercise an installed-device upgrade.
        SQLiteDatabase legacy = SQLiteDatabase.openDatabase(
                context.getDatabasePath(DatabaseHelper.DATABASE_NAME).getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        legacy.setForeignKeyConstraintsEnabled(false);
        legacy.execSQL("DROP TRIGGER IF EXISTS trg_booktitles_category_insert");
        legacy.execSQL("DROP TRIGGER IF EXISTS trg_booktitles_category_update");
        legacy.execSQL("CREATE TABLE Categories_v6 (categoryId TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL UNIQUE, isActive INTEGER NOT NULL DEFAULT 1, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)");
        legacy.execSQL("INSERT INTO Categories_v6 SELECT * FROM Categories");
        legacy.execSQL("DROP TABLE Categories");
        legacy.execSQL("ALTER TABLE Categories_v6 RENAME TO Categories");
        legacy.setVersion(6);
        legacy.close();
        helper = new DatabaseHelper(context);
        db = helper.getWritableDatabase();
        assertEquals("existing-book", new BookTitleDao(db).findById("existing-book").bookTitleId);
        new CategoryDao(db).upsert("CAT001", "Văn học", true);
        new BookTitleDao(db).upsertFromCloud(book("cloud-book", "CAT001", "Văn học"));
        assertNotNull(new BookTitleDao(db).findById("existing-book"));
        assertNotNull(new BookTitleDao(db).findById("cloud-book"));
        try (android.database.Cursor c = db.rawQuery("PRAGMA foreign_key_check", null)) {
            assertEquals(0, c.getCount());
        }
        try (android.database.Cursor c = db.rawQuery("PRAGMA foreign_keys", null)) {
            assertTrue(c.moveToFirst());
            assertEquals(1, c.getInt(0));
        }
    }
}
