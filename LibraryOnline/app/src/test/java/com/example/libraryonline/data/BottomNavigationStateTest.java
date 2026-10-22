package com.example.libraryonline.data;

import static org.junit.Assert.*;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;
import com.example.libraryonline.data.model.User;
import com.example.libraryonline.data.session.*;
import com.example.libraryonline.ui.admin.*;
import com.example.libraryonline.ui.admin.inventory.InventoryActivity;
import com.example.libraryonline.ui.reader.BookDetailActivity;
import com.example.libraryonline.ui.reader.ReaderHomeView;
import com.example.libraryonline.ui.reader.ReaderMainActivity;
import com.example.libraryonline.ui.reader.ReaderNavigation;
import com.google.android.gms.tasks.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, qualifiers = "w320dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BottomNavigationStateTest {
    public static class Screen extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle s) {
            setTheme(R.style.Theme_LibraryOnline);
            super.onCreate(s);
        }
    }

    @Test
    public void readerChangesPanelsRestoresSelectionAndBackPreservesSearch() throws Exception {
        try (var c = Robolectric.buildActivity(Screen.class).setup()) {
            Screen screen = c.get();
            screen.setContentView(R.layout.activity_reader_main);
            ReaderHomeView home = new ReaderHomeView(screen, ReaderHomeView.HOME);
            EditText query = screen.findViewById(R.id.inputBookSearch);
            query.setText("Sapiens");
            screen.findViewById(R.id.navReaderLoans).performClick();
            assertEquals(ReaderHomeView.LOANS, home.selectedTab());
            assertEquals(View.GONE, screen.findViewById(R.id.readerScroll).getVisibility());
            assertEquals(View.VISIBLE, screen.findViewById(R.id.readerLoans).getVisibility());
            screen.findViewById(R.id.navReaderNotifications).performClick();
            assertEquals(
                    View.VISIBLE, screen.findViewById(R.id.readerNotifications).getVisibility());
            screen.findViewById(R.id.navReaderAccount).performClick();
            assertEquals(ReaderHomeView.ACCOUNT, home.selectedTab());
            ReaderHomeView restored = new ReaderHomeView(screen, home.selectedTab());
            assertEquals(View.VISIBLE, screen.findViewById(R.id.readerAccount).getVisibility());
            assertTrue(restored.onBack());
            assertFalse(restored.onBack());
            assertEquals("Sapiens", query.getText().toString());
            assertEquals(
                    R.id.navReaderHome,
                    ((BottomNavigationView) screen.findViewById(R.id.readerBottomNavigation))
                            .getSelectedItemId());
            screen.findViewById(R.id.buttonReaderAccount).performClick();
            assertEquals(ReaderHomeView.ACCOUNT, restored.selectedTab());
            User user = new User();
            user.barcode = "READER-TEST";
            user.cardStatus = "HoatDong";
            restored.bindProfile(
                    new SessionProfile(
                            "reader",
                            SessionProfile.Role.READER,
                            "Độc giả thử nghiệm",
                            "reader@example.test",
                            user,
                            null,
                            false));
            assertTrue(
                    ((TextView) screen.findViewById(R.id.readerAccountBarcode))
                            .getText()
                            .toString()
                            .contains("READER-TEST"));
            restored.clearProfile();
            assertEquals(
                    "",
                    ((TextView) screen.findViewById(R.id.readerAccountName)).getText().toString());
            restored.show(999);
            assertEquals(ReaderHomeView.HOME, restored.selectedTab());
            preview(screen, "reader");
        }
    }

    @Test
    public void adminManagementContainsExistingApprovalsAndInventoryOpensRealScreen()
            throws Exception {
        try (var c = Robolectric.buildActivity(Screen.class).setup()) {
            Screen screen = c.get();
            screen.setContentView(R.layout.activity_admin_main);
            AdminHomeView home = new AdminHomeView(screen, AdminHomeView.HOME);
            screen.findViewById(R.id.navAdminRequests).performClick();
            assertEquals(View.VISIBLE, screen.findViewById(R.id.adminManagementHub).getVisibility());
            assertEquals(View.GONE, screen.findViewById(R.id.adminRequests).getVisibility());
            screen.findViewById(R.id.navAdminCirculation).performClick();
            assertEquals(View.VISIBLE, screen.findViewById(R.id.adminCirculation).getVisibility());
            assertEquals(View.GONE, screen.findViewById(R.id.adminScroll).getVisibility());
            home = new AdminHomeView(screen, home.selectedTab());
            assertEquals(AdminHomeView.CIRCULATION, home.selectedTab());
            screen.findViewById(R.id.navAdminInventory).performClick();
            Intent intent = Shadows.shadowOf(screen).getNextStartedActivity();
            assertEquals(InventoryActivity.class.getName(), intent.getComponent().getClassName());
            assertEquals(
                    R.id.navAdminCirculation,
                    ((BottomNavigationView) screen.findViewById(R.id.adminBottomNavigation))
                            .getSelectedItemId());
            home.show(AdminHomeView.HOME);
            preview(screen, "admin");
        }
    }

    public static class Inventory extends InventoryActivity {
        @Override
        protected void onCreate(Bundle s) {
            setTheme(R.style.Theme_LibraryOnline);
            super.onCreate(s);
        }

        @Override
        protected SessionGateway createSessionGateway() {
            return new SessionGateway() {
                public String currentUid() {
                    return "admin";
                }

                public Task<SessionProfile> resolve() {
                    return Tasks.forResult(
                            new SessionProfile(
                                    "admin",
                                    SessionProfile.Role.ADMIN,
                                    "Admin",
                                    "",
                                    null,
                                    null,
                                    false));
                }

                public AutoCloseable watch(Runnable r) {
                    return () -> {};
                }

                public void signOut() {}
            };
        }

        @Override
        protected void onSessionAuthorized(
                SessionProfile profile) {} // No catalogue network access in this navigation test.
    }

    @Test
    public void inventorySelectsItsTabAndReturnsToExistingAdminRootWithoutStacking()
            throws Exception {
        try (var c = Robolectric.buildActivity(Inventory.class).setup()) {
            Inventory screen = c.get();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(screen.isSessionReady());
            assertEquals(
                    R.id.navAdminInventory,
                    ((BottomNavigationView) screen.findViewById(R.id.adminBottomNavigation))
                            .getSelectedItemId());
            preview(screen, "inventory");
            screen.findViewById(R.id.navAdminAccount).performClick();
            Intent intent = Shadows.shadowOf(screen).getNextStartedActivity();
            assertEquals(AdminMainActivity.class.getName(), intent.getComponent().getClassName());
            assertEquals(AdminHomeView.ACCOUNT, intent.getIntExtra(AdminNavigation.EXTRA_TAB, -1));
            assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
            assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
            assertTrue(screen.isFinishing());
            screen.findViewById(R.id.navAdminHome).performClick();
            assertNull(Shadows.shadowOf(screen).getNextStartedActivity());
        }
    }

    public static class Detail extends BookDetailActivity {
        @Override
        protected void onCreate(Bundle state) {
            setTheme(R.style.Theme_LibraryOnline);
            super.onCreate(state);
        }

        @Override
        protected SessionGateway createSessionGateway() {
            return new SessionGateway() {
                public String currentUid() {
                    return "reader";
                }

                public Task<SessionProfile> resolve() {
                    return Tasks.forResult(
                            new SessionProfile(
                                    "reader",
                                    SessionProfile.Role.READER,
                                    "Reader",
                                    "",
                                    null,
                                    null,
                                    false));
                }

                public AutoCloseable watch(Runnable r) {
                    return () -> {};
                }

                public void signOut() {}
            };
        }

        @Override
        protected void onSessionAuthorized(SessionProfile profile) {}
    }

    @Test
    public void readerDetailRoutesAllTabsIncludingReselectedHomeToExistingRoot() throws Exception {
        int[] ids = {
            R.id.navReaderHome,
            R.id.navReaderLoans,
            R.id.navReaderNotifications,
            R.id.navReaderAccount
        };
        for (int i = 0; i < ids.length; i++)
            try (var c = Robolectric.buildActivity(Detail.class).setup()) {
                Detail screen = c.get();
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                assertTrue(screen.isSessionReady());
                if (i == 0) preview(screen, "reader-detail");
                screen.findViewById(ids[i]).performClick();
                Intent intent = Shadows.shadowOf(screen).getNextStartedActivity();
                assertEquals(
                        ReaderMainActivity.class.getName(), intent.getComponent().getClassName());
                assertEquals(i, intent.getIntExtra(ReaderNavigation.EXTRA_TAB, -1));
                assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
                assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
                assertTrue(screen.isFinishing());
                screen.findViewById(ids[i]).performClick();
                assertNull(Shadows.shadowOf(screen).getNextStartedActivity());
            }
    }

    private void preview(AppCompatActivity activity, String name) throws Exception {
        View root =
                ((android.view.ViewGroup) activity.findViewById(android.R.id.content))
                        .getChildAt(0);
        root.measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 320, 800);
        BottomNavigationView nav =
                activity.findViewById(
                        name.startsWith("reader")
                                ? R.id.readerBottomNavigation
                                : R.id.adminBottomNavigation);
        assertEquals(320, nav.getWidth());
        assertTrue(nav.getHeight() >= 48);
        for (int i = 0; i < nav.getMenu().size(); i++) {
            View item = nav.findViewById(nav.getMenu().getItem(i).getItemId());
            assertTrue(item.getWidth() >= 48);
            assertTrue(item.getHeight() >= 48);
        }
        String dir = System.getenv("NAV_PREVIEW_DIR");
        if (dir == null) return;
        android.graphics.Bitmap image =
                android.graphics.Bitmap.createBitmap(
                        320, 800, android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(image));
        try (var stream = new java.io.FileOutputStream(dir + "/" + name + ".png")) {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
        }
    }
}
