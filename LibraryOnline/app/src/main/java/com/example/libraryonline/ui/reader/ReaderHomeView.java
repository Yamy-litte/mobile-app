package com.example.libraryonline.ui.reader;

import android.app.Activity;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.libraryonline.R;
import com.example.libraryonline.data.session.SessionProfile;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Changes panels without rebuilding the catalogue or losing the current search. */
public final class ReaderHomeView {
    public static final int HOME = 0, LOANS = 1, NOTIFICATIONS = 2, ACCOUNT = 3;
    private final Activity activity;
    private final BottomNavigationView navigation;
    private final int[] panels = {
        R.id.readerScroll, R.id.readerLoans, R.id.readerNotifications, R.id.readerAccount
    };
    private final int[] tabs = {
        R.id.navReaderHome, R.id.navReaderLoans, R.id.navReaderNotifications, R.id.navReaderAccount
    };
    private int selected;
    public interface TabChangedListener {
        void onTabChanged(int tab);
    }

    private TabChangedListener tabChangedListener;

    public ReaderHomeView(Activity activity, int restoredTab) {
        this.activity = activity;
        navigation = activity.findViewById(R.id.readerBottomNavigation);
        navigation.setOnItemSelectedListener(
                item -> {
                    for (int i = 0; i < tabs.length; i++)
                        if (tabs[i] == item.getItemId()) {
                            show(i);
                            return true;
                        }
                    return false;
                });
        navigation.setOnItemReselectedListener(
                item ->
                        ((ScrollView) activity.findViewById(panels[selected]))
                                .smoothScrollTo(0, 0));
        activity.findViewById(R.id.buttonReaderAccount).setOnClickListener(v -> show(ACCOUNT));
        show(restoredTab);
    }

    public void setOnTabChangedListener(TabChangedListener listener) {
        tabChangedListener = listener;
    }

    public int selectedTab() {
        return selected;
    }

    public void show(int tab) {
        selected = tab >= HOME && tab <= ACCOUNT ? tab : HOME;
        for (int i = 0; i < panels.length; i++)
            activity.findViewById(panels[i])
                    .setVisibility(i == selected ? View.VISIBLE : View.GONE);
        navigation.getMenu().findItem(tabs[selected]).setChecked(true);
        if (tabChangedListener != null) tabChangedListener.onTabChanged(selected);
    }

    public boolean onBack() {
        if (selected == HOME) return false;
        show(HOME);
        return true;
    }

    public void bindProfile(SessionProfile profile) {
        ((TextView) activity.findViewById(R.id.readerAccountName)).setText(profile.displayName);
        ((TextView) activity.findViewById(R.id.readerAccountEmailInput)).setText(profile.email);
        String barcode = profile.user == null ? null : profile.user.barcode;
        String status = profile.user == null ? null : profile.user.cardStatus;
        ((TextView) activity.findViewById(R.id.readerAccountBarcode))
                .setText(
                        activity.getString(
                                R.string.nav_card_barcode,
                                value(barcode, R.string.nav_not_issued)));
        ((TextView) activity.findViewById(R.id.readerAccountStatus))
                .setText(
                        activity.getString(
                                R.string.nav_card_status, value(status, R.string.nav_not_updated)));
    }

    private String value(String value, int fallback) {
        return value == null || value.trim().isEmpty() ? activity.getString(fallback) : value;
    }

    public void clearProfile() {
        for (int id :
                new int[] {
                    R.id.readerAccountName,
                    R.id.readerAccountEmailInput,
                    R.id.readerAccountBarcode,
                    R.id.readerAccountStatus
                }) ((TextView) activity.findViewById(id)).setText("");
    }
}
