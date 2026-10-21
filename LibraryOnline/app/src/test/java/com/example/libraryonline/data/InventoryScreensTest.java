package com.example.libraryonline.data;

import static org.junit.Assert.*;

import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.*;

import com.example.libraryonline.R;
import com.example.libraryonline.data.inventory.StockIntake;
import com.example.libraryonline.data.local.dao.CategoryDao;
import com.example.libraryonline.data.model.*;
import com.example.libraryonline.data.session.*;
import com.example.libraryonline.ui.admin.inventory.*;
import com.google.android.gms.tasks.*;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, qualifiers = "w360dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class InventoryScreensTest {
    public static class Screen extends InventoryActivity {
        @Override
        protected void onCreate(Bundle state) {
            setTheme(R.style.Theme_LibraryOnline);
            super.onCreate(state);
        }

        @Override
        protected SessionGateway createSessionGateway() {
            return new SessionGateway() {
                public String currentUid() {
                    return "ui-admin";
                }

                public Task<SessionProfile> resolve() {
                    return Tasks.forResult(
                            new SessionProfile(
                                    "ui-admin",
                                    SessionProfile.Role.ADMIN,
                                    "Admin",
                                    "",
                                    null,
                                    null,
                                    false));
                }

                public AutoCloseable watch(Runnable changed) {
                    return () -> {};
                }

                public void signOut() {}
            };
        }

        @Override
        protected void onSessionAuthorized(
                SessionProfile profile) {} // Test catalogue snapshots, no cloud writes.
    }

    private org.robolectric.android.controller.ActivityController<Screen> controller;
    private Screen screen;
    private BookTitle book;
    private BookCopy copy;
    private InventoryViewModel model;

    @Before
    public void start() throws Exception {
        controller = Robolectric.buildActivity(Screen.class).setup();
        screen = controller.get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        book = new BookTitle();
        book.bookTitleId = "BOOK10";
        book.title = "Đất rừng phương Nam";
        book.author = "Đoàn Giỏi";
        book.categoryId = "van-hoc";
        book.category = "Văn học Việt Nam";
        book.publisher = "NXB Kim Đồng";
        book.isbn = "9786042168124";
        book.publishYear = 2022;
        book.totalCopies = 3;
        book.availableCopies = 2;
        copy = new BookCopy();
        copy.copyId = "COPY-1";
        copy.bookTitleId = book.bookTitleId;
        copy.barcode = "BOOK10-001";
        copy.copyStatus = "SanSang";
        copy.shelfLocation = "A2-15";
        set("books", new ArrayList<>(Arrays.asList(book)));
        set("copies", new ArrayList<>(Arrays.asList(copy)));
        set(
                "categories",
                new ArrayList<>(
                        Arrays.asList(
                                new CategoryDao.Category("van-hoc", "Văn học Việt Nam", true))));
        model = (InventoryViewModel) get("model");
        render();
    }

    @After
    public void stop() {
        controller.pause().stop().destroy();
    }

    private void set(String name, Object value) throws Exception {
        var field = InventoryActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(screen, value);
    }

    private Object get(String name) throws Exception {
        var field = InventoryActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private void render() throws Exception {
        var method = InventoryActivity.class.getDeclaredMethod("render");
        method.setAccessible(true);
        method.invoke(screen);
    }

    @Test
    public void addAndEditUseFullPagesAndSnapshotUpdatesDoNotDiscardDraft() throws Exception {
        preview("list");
        screen.findViewById(R.id.inventoryAdd).performClick();
        assertEquals(
                "Thêm đầu sách",
                ((TextView) screen.findViewById(R.id.inventoryHeading)).getText().toString());
        assertEquals(View.VISIBLE, screen.findViewById(R.id.editorInitialStock).getVisibility());
        ((EditText) screen.findViewById(R.id.formTitle)).setText("Bản nháp chưa lưu");
        ((EditText) screen.findViewById(R.id.formCover)).setText("https://example.org/book.jpg");
        ((EditText) screen.findViewById(R.id.intakeShelf)).setText("A3-12");
        render();
        assertEquals(
                "Bản nháp chưa lưu",
                ((EditText) screen.findViewById(R.id.formTitle)).getText().toString());
        set(
                "categories",
                new ArrayList<>(
                        Arrays.asList(
                                new CategoryDao.Category("van-hoc", "Văn học", true),
                                new CategoryDao.Category("science", "Khoa học", true))));
        render();
        assertEquals(
                "Bản nháp chưa lưu",
                ((EditText) screen.findViewById(R.id.formTitle)).getText().toString());
        assertEquals(
                "https://example.org/book.jpg",
                ((EditText) screen.findViewById(R.id.formCover)).getText().toString());
        assertEquals(
                "A3-12", ((EditText) screen.findViewById(R.id.intakeShelf)).getText().toString());
        preview("add");
        set("page", "list");
        set("renderedKey", null);
        render();
        screen.findViewById(R.id.inventoryBookEdit).performClick();
        assertEquals(View.GONE, screen.findViewById(R.id.editorInitialStock).getVisibility());
        assertEquals(
                book.title, ((EditText) screen.findViewById(R.id.formTitle)).getText().toString());
        preview("edit");
        model.busy = true;
        render();
        assertFalse(screen.findViewById(R.id.editorSave).isEnabled());
        model.busy = false;
    }

    @Test
    public void restockForecastUsesRealCountersAndStepperStaysWithinAllowedRange()
            throws Exception {
        screen.findViewById(R.id.inventoryBookIntake).performClick();
        screen.findViewById(R.id.intakePlus).performClick();
        assertTrue(
                ((TextView) screen.findViewById(R.id.intakeForecast))
                        .getText()
                        .toString()
                        .contains("3 → 5"));
        for (int i = 0; i < 10; i++) screen.findViewById(R.id.intakePlus).performClick();
        assertEquals(
                "5", ((EditText) screen.findViewById(R.id.intakeQuantity)).getText().toString());
        for (int i = 0; i < 10; i++) screen.findViewById(R.id.intakeMinus).performClick();
        assertEquals(
                "1", ((EditText) screen.findViewById(R.id.intakeQuantity)).getText().toString());
        screen.findViewById(R.id.intakePlus).performClick();
        ((EditText) screen.findViewById(R.id.intakeShelf)).setText("A2-15");
        preview("intake");
        ((EditText) screen.findViewById(R.id.intakeQuantity)).setText("0");
        screen.findViewById(R.id.intakeSubmit).performClick();
        assertEquals(View.VISIBLE, screen.findViewById(R.id.intakeError).getVisibility());
        assertFalse(model.hasPending());
    }

    @Test
    public void copyDetailsKeepBarcodeReadOnlyAndDisableRetirementWhenReserved() throws Exception {
        screen.findViewById(R.id.inventoryBookCopies).performClick();
        screen.findViewById(R.id.copyShelf).performClick();
        assertEquals(
                copy.barcode,
                ((TextView) screen.findViewById(R.id.copyBarcodeText)).getText().toString());
        assertFalse(screen.findViewById(R.id.copyBarcodeText) instanceof EditText);
        assertTrue(screen.findViewById(R.id.copyRetire).isEnabled());
        preview("copy");
        copy.priorityUserId = "reader-waiting";
        render();
        assertFalse(screen.findViewById(R.id.copyRetire).isEnabled());
        assertFalse(screen.findViewById(R.id.copyHide).isEnabled());
    }

    @Test
    public void labelPageWaitsForActualCopiesAndNeverFabricatesBarcodes() throws Exception {
        StockIntake intake = new StockIntake("receipt-test", 2, "A2-15");
        model.completedIntake = intake;
        model.completedBook = book;
        model.selectedId = book.bookTitleId;
        set("page", "labels");
        set("renderedKey", null);
        render();
        assertFalse(screen.findViewById(R.id.labelsPrint).isEnabled());
        assertEquals(0, ((LinearLayout) screen.findViewById(R.id.labelItems)).getChildCount());
        List<BookCopy> result = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            BookCopy c = new BookCopy();
            c.copyId = intake.copyId(i);
            c.bookTitleId = book.bookTitleId;
            c.barcode = "BOOK10-00" + (6 + i);
            c.shelfLocation = "A2-15";
            result.add(c);
        }
        set("copies", result);
        render();
        assertTrue(screen.findViewById(R.id.labelsPrint).isEnabled());
        LinearLayout labels = screen.findViewById(R.id.labelItems);
        assertEquals(2, labels.getChildCount());
        assertEquals(
                "BOOK10-006",
                ((TextView) labels.getChildAt(0).findViewById(R.id.labelBarcode))
                        .getText()
                        .toString());
        preview("labels");
    }

    private void preview(String name) throws Exception {
        View root = screen.findViewById(R.id.inventoryRoot);
        root.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 360, 900);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        root.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(360, root.getWidth());
        assertTrue(screen.findViewById(R.id.adminBottomNavigation).getHeight() >= 48);
        String folder = System.getenv("INVENTORY_PREVIEW_DIR");
        if (folder == null) return;
        android.graphics.Bitmap image =
                android.graphics.Bitmap.createBitmap(
                        360, 900, android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(image));
        try (var out = new java.io.FileOutputStream(folder + "/" + name + ".png")) {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
