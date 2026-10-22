package com.example.libraryonline.ui.notifications;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class OnlineLoanAlertCenterTest {
    @Test
    public void serverBaselineAndNewAlertsAreDeduplicatedPerUserAndRole() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SharedPreferences prefs = activity.getSharedPreferences(
                "online_loan_alert_seen_v1", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        OnlineLoanAlertCenter.registerSnapshot(
                activity, "reader-one", false, Arrays.asList("old-approved", "old-rejected"));
        assertEquals(3, prefs.getAll().size());
        OnlineLoanAlertCenter.registerSnapshot(
                activity, "reader-one", false, Arrays.asList("new-approved"));
        assertEquals(3, prefs.getAll().size());
        OnlineLoanAlertCenter.notifyNew(
                activity, "reader-one", false, "new-approved",
                "Đã duyệt", "Đến nhận sách");
        assertEquals(4, prefs.getAll().size());
        OnlineLoanAlertCenter.notifyNew(
                activity, "reader-one", false, "new-approved",
                "Đã duyệt", "Đến nhận sách");
        assertEquals(4, prefs.getAll().size());
        OnlineLoanAlertCenter.registerSnapshot(
                activity, "reader-one", true, Arrays.asList("pending-admin"));
        assertEquals(6, prefs.getAll().size());
        OnlineLoanAlertCenter.registerSnapshot(
                activity, "reader-two", false, Arrays.asList("other-user"));
        assertEquals(8, prefs.getAll().size());
    }
}
