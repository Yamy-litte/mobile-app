package com.example.libraryonline.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.libraryonline.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, qualifiers = "w320dp-h800dp-mdpi")
public class SharedPaletteTest {
    public static class Screen extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle state) {
            setTheme(R.style.Theme_LibraryOnline);
            super.onCreate(state);
        }
    }

    @Test
    public void semanticColorsReuseExistingLibraryPalette() {
        android.content.Context context = RuntimeEnvironment.getApplication();
        assertSameColor(context, R.color.background, R.color.library_background);
        assertSameColor(context, R.color.surface, R.color.library_surface);
        assertSameColor(context, R.color.input_background, R.color.library_surface);
        assertSameColor(context, R.color.border, R.color.library_border);
        assertSameColor(context, R.color.text_primary, R.color.library_text);
        assertSameColor(context, R.color.text_secondary, R.color.library_muted);
        assertSameColor(context, R.color.admin_screen_bg, R.color.background);
        assertSameColor(context, R.color.admin_screen_ink, R.color.text_primary);
        assertSameColor(context, R.color.admin_screen_muted, R.color.text_secondary);
        assertSameColor(context, R.color.admin_screen_border, R.color.border);
        assertSameColor(context, R.color.admin_screen_readonly, R.color.input_background);
        assertSameColor(context, R.color.online_canvas, R.color.background);
    }

    @Test
    public void adminReaderAuthInventoryAndOnlineLoanUseSameBackgroundDayAndNight() {
        int[] layouts = {
                R.layout.activity_auth_login,
                R.layout.activity_reader_main,
                R.layout.activity_admin_main,
                R.layout.activity_admin_management,
                R.layout.activity_admin_inventory,
                R.layout.activity_reader_online_borrow,
                R.layout.view_auth_session_gate
        };
        for (String variant : new String[]{"-notnight", "-night"}) {
            RuntimeEnvironment.setQualifiers("w320dp-h800dp" + variant + "-mdpi");
            try (ActivityController<Screen> controller =
                         Robolectric.buildActivity(Screen.class).setup()) {
                Screen screen = controller.get();
                int expected = ContextCompat.getColor(screen, R.color.background);
                for (int layout : layouts) {
                    View root = screen.getLayoutInflater()
                            .inflate(layout, new FrameLayout(screen), false);
                    assertTrue("Expected a solid screen background: " + layout,
                            root.getBackground() instanceof ColorDrawable);
                    assertEquals("Wrong screen background: " + layout,
                            expected, ((ColorDrawable) root.getBackground()).getColor());
                }
            }
        }
    }

    private static void assertSameColor(android.content.Context context, int a, int b) {
        assertEquals(ContextCompat.getColor(context, b), ContextCompat.getColor(context, a));
    }
}
