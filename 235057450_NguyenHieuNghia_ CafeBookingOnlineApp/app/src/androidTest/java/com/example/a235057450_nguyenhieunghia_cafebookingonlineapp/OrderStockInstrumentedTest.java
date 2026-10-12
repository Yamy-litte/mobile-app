package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.BillDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.MenuDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.ReviewDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.DishReviewDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.database.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.BillDetail;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Menu;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class OrderStockInstrumentedTest {
    private Context testContext;
    private DatabaseHelper helper;
    private BillDao billDao;
    private MenuDao menuDao;

    @Before
    public void setUp() {
        // Use the instrumentation APK's private database, not the user's app database.
        testContext = InstrumentationRegistry.getInstrumentation().getContext();
        testContext.deleteDatabase("cafe_booking.db");
        helper = new DatabaseHelper(testContext);
        helper.getWritableDatabase().close();
        billDao = new BillDao(testContext);
        menuDao = new MenuDao(testContext);
    }

    @Test
    public void checkoutReducesStockAndCancellationRestoresIt() {
        Menu before = menuDao.getMenuById(1);
        assertEquals(17, before.getStockQuantity());

        BillDetail line = new BillDetail();
        line.setMenuId(1);
        line.setQuantity(2);
        long billId = billDao.checkout(1, Collections.singletonList(line));

        assertTrue("Checkout should create a bill", billId > 0);
        assertEquals(15, menuDao.getMenuById(1).getStockQuantity());
        assertEquals("CANCELLED", billDao.cancelOrder((int) billId, 1));
        assertEquals(17, menuDao.getMenuById(1).getStockQuantity());
    }

    @Test
    public void checkoutRejectsQuantityAboveStockWithoutChangingStock() {
        int before = menuDao.getMenuById(1).getStockQuantity();
        BillDetail line = new BillDetail();
        line.setMenuId(1);
        line.setQuantity(before + 1);

        assertEquals(-1L, billDao.checkout(1, Collections.singletonList(line)));
        assertEquals(before, menuDao.getMenuById(1).getStockQuantity());
    }

    @Test
    public void orderAndDishReviewsCanBeReadAndSaved() {
        ReviewDao reviewDao = new ReviewDao(testContext);
        DishReviewDao dishReviewDao = new DishReviewDao(testContext);
        try {
            assertEquals(3, reviewDao.getAllReviews().size());
            assertTrue(dishReviewDao.getRecentForMenu(1).size() > 0);

            BillDetail line = new BillDetail();
            line.setMenuId(1);
            line.setQuantity(1);
            long billId = billDao.checkout(1, Collections.singletonList(line));
            assertTrue(billId > 0);
            assertTrue(reviewDao.saveForBill((int) billId, 1, 5, "Kiểm thử đánh giá đơn hàng."));
            assertEquals(4, reviewDao.getAllReviews().size());

            assertTrue(dishReviewDao.saveForMenu(2, 1, 4, "Kiểm thử đánh giá món."));
            assertEquals(4, dishReviewDao.getForMenu(2, 1).getRating());
        } finally {
            reviewDao.close();
            dishReviewDao.close();
        }
    }

    @After
    public void tearDown() {
        if (billDao != null) billDao.close();
        if (menuDao != null) menuDao.close();
        if (helper != null) helper.close();
        if (testContext != null) testContext.deleteDatabase("cafe_booking.db");
    }
}
