package com.example.libraryonline.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.libraryonline.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, qualifiers = "w320dp-h800dp-mdpi")
public class XmlPresentationTest {
    public static class Screen extends AppCompatActivity {
        @Override public void onCreate(Bundle state) {
            setTheme(R.style.Theme_LibraryOnline);
            super.onCreate(state);
        }
    }

    @Test public void adminAndReaderItemTemplatesInflateWithExpectedControls() {
        try (ActivityController<Screen> controller =
                     Robolectric.buildActivity(Screen.class).setup()) {
            Screen activity = controller.get();
            int[][] checks = {
                    {R.layout.view_admin_reader_list, R.id.adminReaderSearch},
                    {R.layout.item_admin_reader_profile, R.id.adminReaderCardOpen},
                    {R.layout.view_admin_reader_detail, R.id.adminReaderDetailLock},
                    {R.layout.view_admin_config_panel, R.id.adminConfigBooksInput},
                    {R.layout.view_admin_reports_panel, R.id.adminReportBars},
                    {R.layout.item_admin_report_bar, R.id.adminReportBarDay},
                    {R.layout.item_reader_loan_record, R.id.readerLoanReceipt},
                    {R.layout.item_reader_local_notification, R.id.readerLocalNotifRead},
                    {R.layout.item_reader_online_request, R.id.readerOnlineItemCountdown},
                    {R.layout.item_reader_online_notification, R.id.readerOnlineNotRead},
                    {R.layout.item_admin_online_request, R.id.adminOnlineItemApprove},
                    {R.layout.dialog_admin_online_reject, R.id.adminOnlineRejectReason}
            };
            for (int[] pair : checks) {
                View view = activity.getLayoutInflater().inflate(
                        pair[0], new LinearLayout(activity), false);
                assertNotNull("Missing XML control for layout " + pair[0],
                        view.findViewById(pair[1]));
            }
        }
    }

    @Test public void inheritedAdminAndOnlineAccentsMatchLoginBlue() {
        try (ActivityController<Screen> controller =
                     Robolectric.buildActivity(Screen.class).setup()) {
            Screen activity = controller.get();
            int blue = ContextCompat.getColor(activity, R.color.library_blue);
            assertEquals(0xFF315EFB, blue);
            assertEquals(blue, ContextCompat.getColor(activity, R.color.admin_screen_primary));
            assertEquals(blue, ContextCompat.getColor(activity, R.color.online_forest));
            assertEquals(ContextCompat.getColor(activity, R.color.library_blue_dark),
                    ContextCompat.getColor(activity, R.color.admin_screen_primary_dark));
            assertEquals(ContextCompat.getColor(activity, R.color.library_accent_background),
                    ContextCompat.getColor(activity, R.color.online_tint));
            assertEquals(0xFFF5F7FC, ContextCompat.getColor(activity, R.color.background));
            assertEquals(0xFFFFFFFF, ContextCompat.getColor(activity, R.color.surface));
            assertEquals(0xFFDCE3EF, ContextCompat.getColor(activity, R.color.border));
            assertEquals(0xFF182230, ContextCompat.getColor(activity, R.color.text_primary));
            assertEquals(0xFF68758A, ContextCompat.getColor(activity, R.color.text_secondary));
        }
    }

    @Test public void selectedFiltersUseXmlColorStateLists() {
        try (ActivityController<Screen> controller =
                     Robolectric.buildActivity(Screen.class).setup()) {
            Screen activity = controller.get();
            ColorStateList bg = ContextCompat.getColorStateList(
                    activity, R.color.library_filter_background);
            ColorStateList text = ContextCompat.getColorStateList(
                    activity, R.color.library_filter_text);
            assertNotNull(bg);
            assertNotNull(text);
            assertTrue(bg.isStateful());
            int[] selected = {android.R.attr.state_selected};
            assertEquals(ContextCompat.getColor(activity, R.color.library_blue),
                    bg.getColorForState(selected, 0));
            assertEquals(ContextCompat.getColor(activity, R.color.white),
                    text.getColorForState(selected, 0));
            assertEquals(ContextCompat.getColor(activity, R.color.surface),
                    bg.getDefaultColor());
            assertEquals(ContextCompat.getColor(activity, R.color.text_primary),
                    text.getDefaultColor());
        }
    }
}
