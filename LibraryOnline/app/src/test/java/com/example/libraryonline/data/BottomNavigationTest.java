package com.example.libraryonline.data;

import static org.junit.Assert.*;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, qualifiers = "w320dp-h800dp-mdpi")
public class BottomNavigationTest {
    public static class Screen extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle state) {
            setTheme(R.style.Theme_LibraryOnline);
            super.onCreate(state);
        }
    }

    private BottomNavigationView findNavigation(View view) {
        if (view instanceof BottomNavigationView) return (BottomNavigationView) view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                BottomNavigationView result = findNavigation(((ViewGroup) view).getChildAt(i));
                if (result != null) return result;
            }
        return null;
    }

    @Test
    public void adminHomeAndInventoryExposeSameFiveDestinationsFromFigma() {
        try (var controller = Robolectric.buildActivity(Screen.class).setup()) {
            Screen screen = controller.get();
            for (int layout :
                    new int[] {R.layout.activity_admin_main, R.layout.activity_admin_inventory}) {
                screen.setContentView(layout);
                BottomNavigationView nav =
                        findNavigation(screen.findViewById(android.R.id.content));
                assertNotNull("Admin needs a real bottom navigation on home and inventory", nav);
                assertEquals(5, nav.getMenu().size());
                String[] titles = {"Trang chủ", "Kho sách", "Mượn trả", "Quản lý", "Tài khoản"};
                for (int i = 0; i < titles.length; i++)
                    assertEquals(titles[i], nav.getMenu().getItem(i).getTitle().toString());
            }
        }
    }

    @Test
    public void readerDetailKeepsTheSameBottomNavigation() {
        try (var controller = Robolectric.buildActivity(Screen.class).setup()) {
            Screen screen = controller.get();
            screen.setContentView(R.layout.activity_reader_book_detail);
            BottomNavigationView nav = findNavigation(screen.findViewById(android.R.id.content));
            assertNotNull("Book detail in Figma keeps Reader navigation", nav);
            assertEquals(4, nav.getMenu().size());
        }
    }

    @Test
    public void readerExposesFourIconDestinationsFromFigma() {
        try (var controller = Robolectric.buildActivity(Screen.class).setup()) {
            Screen screen = controller.get();
            screen.setContentView(R.layout.activity_reader_main);
            BottomNavigationView nav = findNavigation(screen.findViewById(android.R.id.content));
            assertNotNull("Reader needs selectable navigation instead of text glyphs", nav);
            assertEquals(4, nav.getMenu().size());
            String[] titles = {"Trang chủ", "Mượn sách", "Thông báo", "Tài khoản"};
            for (int i = 0; i < titles.length; i++) {
                assertEquals(titles[i], nav.getMenu().getItem(i).getTitle().toString());
                assertNotNull(nav.getMenu().getItem(i).getIcon());
            }
        }
    }
}
