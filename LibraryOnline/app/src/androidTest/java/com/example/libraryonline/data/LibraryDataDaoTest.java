package com.example.libraryonline.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.libraryonline.data.local.dao.BookTitleDao;
import com.example.libraryonline.data.local.dao.SystemConfigDao;
import com.example.libraryonline.data.local.dao.UserDao;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.model.BookTitle;
import com.example.libraryonline.data.model.SystemConfig;
import com.example.libraryonline.data.model.User;
import com.example.libraryonline.data.repository.BookRepository;
import com.example.libraryonline.data.repository.UserRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LibraryDataDaoTest {
    private Context context;
    private DatabaseHelper helper;
    private SQLiteDatabase db;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
        helper = new DatabaseHelper(context);
        db = helper.getWritableDatabase();
    }

    @After
    public void tearDown() {
        if (helper != null) helper.close();
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME);
    }

    @Test
    public void userDaoCanInsertFindAndUpdateProfile() {
        User user = new User("test-user-1", "Nguyen Van A", "reader@example.test",
                "2026-10-20", "2026-10-20");
        user.phone = "0900000000";
        assertTrue(new UserDao(db).insert(user) > 0);

        User loaded = new UserDao(db).findById("test-user-1");
        assertNotNull(loaded);
        assertEquals("Nguyen Van A", loaded.fullName);
        assertEquals("reader@example.test", loaded.email);

        loaded.fullName = "Nguyen Van B";
        loaded.updatedAt = "2026-10-21";
        assertEquals(1, new UserDao(db).update(loaded));
        assertEquals("Nguyen Van B", new UserDao(db).findById("test-user-1").fullName);
    }

    @Test
    public void bookTitleDaoCanInsertSearchAndUpdate() {
        BookTitle book = new BookTitle();
        book.bookTitleId = "test-book-1";
        book.title = "Lap trinh Android";
        book.author = "Tac gia thu nghiem";
        book.category = "Cong nghe";
        book.totalCopies = 2;
        book.availableCopies = 2;
        book.createdAt = "2026-10-20";
        book.updatedAt = "2026-10-20";
        assertTrue(new BookTitleDao(db).insert(book) > 0);

        assertNotNull(new BookTitleDao(db).findById("test-book-1"));
        assertEquals(1, new BookTitleDao(db).search("Android").size());

        book.title = "Lap trinh Android nang cao";
        assertEquals(1, new BookTitleDao(db).update(book));
        assertEquals("Lap trinh Android nang cao",
                new BookTitleDao(db).findById("test-book-1").title);
    }

    @Test
    public void defaultSystemConfigIsCreated() {
        SystemConfig config = new SystemConfigDao(db).getCurrent();
        assertNotNull(config);
        assertEquals(5, config.maxBooksPerUser);
        assertEquals(14, config.maxLoanDays);
        assertEquals(5000, config.lateFeePerDay);
    }

    @Test
    public void repositoriesCanReadAndWriteProfilesAndBooks() {
        UserRepository users = new UserRepository(context);
        BookRepository books = new BookRepository(context);
        try {
            User user = new User("repo-user-1", "Repository Test", "repo@example.test",
                    "2026-10-20", "2026-10-20");
            assertTrue(users.createProfile(user) > 0);
            assertEquals("Repository Test", users.getByEmail("repo@example.test").fullName);

            BookTitle book = new BookTitle();
            book.bookTitleId = "repo-book-1";
            book.title = "Repository Book";
            book.totalCopies = 1;
            book.availableCopies = 1;
            book.createdAt = "2026-10-20";
            book.updatedAt = "2026-10-20";
            assertTrue(books.addBookTitle(book) > 0);
            assertEquals("Repository Book", books.getBookTitle("repo-book-1").title);
        } finally {
            users.close();
            books.close();
        }
    }
}