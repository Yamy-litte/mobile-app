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
                    {R.layout.view_admin_reader_list, R.id.adminReaderScanCard},
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
                    {R.layout.dialog_admin_online_reject, R.id.adminOnlineRejectReason},
                    {R.layout.item_admin_online_request, R.id.adminOnlineReaderBarcode},
                    {R.layout.item_admin_online_request, R.id.adminOnlineItemCollect},
                    {R.layout.item_admin_online_request, R.id.adminOnlineItemReturn},
                    {R.layout.item_reader_online_request, R.id.readerOnlineItemCancel},
                    {R.layout.item_reader_online_request, R.id.readerOnlineItemDue},
                    {R.layout.view_reader_loans, R.id.containerReaderOnlineReceipts},
                    {R.layout.view_reader_loans, R.id.readerLoansTabBorrowed},
                    {R.layout.view_reader_loans, R.id.readerLoansTabPickup},
                    {R.layout.view_reader_loans, R.id.readerLoansTabPending},
                    {R.layout.view_reader_loans, R.id.readerLoansTabHistory},
                    {R.layout.view_reader_loans, R.id.readerLoansOnlineSection},
                    {R.layout.view_reader_loans, R.id.readerLoansLocalPanel},
                    {R.layout.view_reader_loans, R.id.readerLoansHistoryPanel},
                    {R.layout.activity_admin_main, R.id.adminHomeOnlineWork},
                    {R.layout.activity_admin_main, R.id.adminHomeOnlinePending},
                    {R.layout.activity_admin_main, R.id.adminHomeOnlineApproved},
                    {R.layout.activity_admin_main, R.id.adminHomeOnlineBorrowed},
                    {R.layout.activity_admin_main, R.id.adminHomeReviewOnline},
                    {R.layout.activity_admin_main, R.id.adminHomeStartHandover},
                    {R.layout.item_reader_online_receipt, R.id.readerOnlineReceiptStatus},
                    {R.layout.activity_reader_online_borrow, R.id.onlineBorrowValidation},
                    {R.layout.activity_reader_online_borrow, R.id.onlineBorrowRetry},
                    {R.layout.view_admin_circulation, R.id.circulationModeOnline},
                    {R.layout.view_admin_circulation, R.id.circulationModeDirect},
                    {R.layout.view_admin_circulation, R.id.circulationModePenalty},
                    {R.layout.view_admin_circulation, R.id.circulationReaderPanel},
                    {R.layout.view_admin_circulation, R.id.circulationOnlinePanel},
                    {R.layout.view_admin_circulation, R.id.circulationCopyPanel},
                    {R.layout.view_admin_circulation, R.id.circulationOnlineActions},
                    {R.layout.view_admin_circulation, R.id.circulationDirectPanel},
                    {R.layout.view_admin_circulation, R.id.circulationPenaltyPanel},
                    {R.layout.view_admin_circulation, R.id.buttonCirculationBorrow},
                    {R.layout.view_admin_circulation, R.id.buttonCirculationReturn},
                    {R.layout.view_admin_circulation, R.id.buttonCirculationCollectFine},
                    {R.layout.view_admin_circulation, R.id.textCirculationResult},
                    {R.layout.item_admin_counter_online_loan, R.id.counterOnlineStage},
                    {R.layout.view_admin_circulation, R.id.scanCirculationReaderBarcode},
                    {R.layout.view_admin_circulation, R.id.scanCirculationCopyBarcode},
                    {R.layout.view_admin_circulation, R.id.textCounterOnlineReader},
                    {R.layout.view_admin_circulation, R.id.containerCounterOnlineLoans},
                    {R.layout.view_admin_circulation, R.id.textCounterOnlineVerification},
                    {R.layout.view_admin_circulation, R.id.buttonCounterOnlineCollect},
                    {R.layout.view_admin_circulation, R.id.buttonCounterOnlineReturn},
                    {R.layout.item_admin_counter_online_loan, R.id.counterOnlineBookTitle},
                    {R.layout.item_admin_counter_online_loan, R.id.counterOnlineBookDetails},
                    {R.layout.item_admin_counter_online_loan, R.id.counterOnlineSelect},
                    {R.layout.item_admin_online_request, R.id.adminOnlineScanReader},
                    {R.layout.item_admin_online_request, R.id.adminOnlineScanCopy},
                    {R.layout.activity_admin_inventory, R.id.inventoryScanBarcode},
                    {R.layout.activity_library_barcode_scan, R.id.libraryBarcodeCancel},
                    {R.layout.activity_library_barcode_scan, R.id.zxing_barcode_scanner}
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

    @Test public void onlineDurationButtonsUseXmlSelectedStatePalette() {
        try (ActivityController<Screen> controller =
                Robolectric.buildActivity(Screen.class).setup()) {
            Screen activity = controller.get();
            ColorStateList bg = ContextCompat.getColorStateList(
                    activity, R.color.online_borrow_day_background);
            ColorStateList text = ContextCompat.getColorStateList(
                    activity, R.color.online_borrow_day_text);
            int[] selected = {android.R.attr.state_selected};
            assertEquals(ContextCompat.getColor(activity, R.color.library_blue),
                    bg.getColorForState(selected, 0));
            assertEquals(ContextCompat.getColor(activity, R.color.white),
                    text.getColorForState(selected, 0));
            assertEquals(ContextCompat.getColor(activity, R.color.library_accent_background),
                    bg.getDefaultColor());
        }
    }

    @Test public void onlineRequestValidationColorIsResourceDriven() {
        try (ActivityController<Screen> controller =
                     Robolectric.buildActivity(Screen.class).setup()) {
            Screen activity = controller.get();
            ColorStateList validation = ContextCompat.getColorStateList(
                    activity, R.color.online_borrow_validation_text);
            assertNotNull(validation);
            assertTrue(validation.isStateful());
            assertEquals(ContextCompat.getColor(activity, R.color.library_success),
                    validation.getColorForState(new int[]{android.R.attr.state_selected}, 0));
            assertEquals(ContextCompat.getColor(activity, R.color.text_secondary),
                    validation.getDefaultColor());
        }
    }

    @Test public void circulationWorkflowModeAndVerifiedStateUseLoginPalette() {
        try (ActivityController<Screen> controller =
                     Robolectric.buildActivity(Screen.class).setup()) {
            Screen activity = controller.get();
            int[] active = {android.R.attr.state_selected};
            ColorStateList mode = ContextCompat.getColorStateList(
                    activity, R.color.circulation_mode_text);
            ColorStateList verification = ContextCompat.getColorStateList(
                    activity, R.color.circulation_verification_text);
            assertNotNull(mode);
            assertNotNull(verification);
            assertTrue(mode.isStateful());
            assertTrue(verification.isStateful());
            assertEquals(ContextCompat.getColor(activity, R.color.surface),
                    mode.getColorForState(active, 0));
            assertEquals(ContextCompat.getColor(activity, R.color.text_secondary),
                    mode.getDefaultColor());
            assertEquals(ContextCompat.getColor(activity, R.color.library_success),
                    verification.getColorForState(active, 0));
        }
    }

    @Test public void readerLoanTabsUseLoginColorStateList() {
        try (ActivityController<Screen> controller =
                Robolectric.buildActivity(Screen.class).setup()) {
            Screen activity = controller.get();
            ColorStateList text = ContextCompat.getColorStateList(
                    activity, R.color.reader_loan_tab_text);
            assertNotNull(text);
            assertTrue(text.isStateful());
            assertEquals(ContextCompat.getColor(activity, R.color.surface),
                    text.getColorForState(new int[]{android.R.attr.state_selected}, 0));
            assertEquals(ContextCompat.getColor(activity, R.color.text_secondary),
                    text.getDefaultColor());
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
