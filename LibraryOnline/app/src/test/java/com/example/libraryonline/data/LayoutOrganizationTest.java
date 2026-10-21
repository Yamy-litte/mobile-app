package com.example.libraryonline.data;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.libraryonline.R;
import com.example.libraryonline.ui.admin.inventory.InventoryActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w320dp-h800dp-mdpi")
public class LayoutOrganizationTest {
    public static class Screen extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle state) {
            setTheme(R.style.Theme_LibraryOnline);
            super.onCreate(state);
        }
    }

    @Test
    public void relocatedInventoryActivityIsRegisteredAndRemainsPrivate() throws Exception {
        android.app.Application app = RuntimeEnvironment.getApplication();
        ActivityInfo info =
                app.getPackageManager()
                        .getActivityInfo(new ComponentName(app, InventoryActivity.class), 0);
        assertEquals(InventoryActivity.class.getName(), info.name);
        assertFalse(info.exported);
    }

    @Test
    public void readerChipSelectionAndPopularCardSizeSurviveXmlExtraction() {
        try (org.robolectric.android.controller.ActivityController<Screen> controller =
                Robolectric.buildActivity(Screen.class).setup()) {
            Screen screen = controller.get();
            LinearLayout parent = new LinearLayout(screen);
            TextView chip =
                    (TextView)
                            screen.getLayoutInflater()
                                    .inflate(R.layout.item_reader_category, parent, false);
            assertEquals(
                    ContextCompat.getColor(screen, R.color.library_blue),
                    chip.getCurrentTextColor());
            chip.setSelected(true);
            assertEquals(ContextCompat.getColor(screen, R.color.white), chip.getCurrentTextColor());
            chip.setSelected(false);
            assertEquals(
                    ContextCompat.getColor(screen, R.color.library_blue),
                    chip.getCurrentTextColor());
            View card =
                    screen.getLayoutInflater()
                            .inflate(R.layout.item_reader_popular_book, parent, false);
            assertEquals(292, card.getLayoutParams().width);
            assertEquals(10, ((LinearLayout.LayoutParams) card.getLayoutParams()).getMarginEnd());
            assertNotNull(card.findViewById(R.id.imageReaderBookCover));
            assertNotNull(card.findViewById(R.id.textReaderBookTitle));
        }
    }
}
