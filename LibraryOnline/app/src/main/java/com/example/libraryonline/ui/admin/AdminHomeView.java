package com.example.libraryonline.ui.admin;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.ScrollView;

import com.example.libraryonline.R;
import com.example.libraryonline.ui.admin.inventory.InventoryActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Navigation within the existing Admin screen, with no new business operations. */
public final class AdminHomeView {
    public static final int HOME = 0, REQUESTS = 1, ACCOUNT = 2, CIRCULATION = 3, APPROVALS = 4;
    private final Activity activity;
    private final int[] panels = {R.id.adminOverview, R.id.adminManagementHub, R.id.adminAccount};
    private final int[] tabs = {
        R.id.navAdminHome, R.id.navAdminRequests, R.id.navAdminAccount, R.id.navAdminCirculation
    };
    private final BottomNavigationView navigation;
    private int selected;

    public AdminHomeView(Activity activity, int restoredTab) {
        this(
                activity,
                restoredTab,
                () -> activity.startActivity(new Intent(activity, InventoryActivity.class)));
    }

    public AdminHomeView(Activity activity, int restoredTab, Runnable openInventory) {
        this.activity = activity;
        navigation = activity.findViewById(R.id.adminBottomNavigation);
        for (int id :
                new int[] {
                    R.id.textAdminOverview,
                    R.id.textAdminWork,
                    R.id.textAdminShortcuts,
                    R.id.textAdminRequestsHeading,
                    R.id.textAdminAccountHeading
                })
            androidx.core.view.ViewCompat.setAccessibilityHeading(activity.findViewById(id), true);
        navigation.setOnItemSelectedListener(
                item -> {
                    if (item.getItemId() == R.id.navAdminInventory) {
                        openInventory.run();
                        return false; // Selection belongs to InventoryActivity while it is visible.
                    }
                    for (int i = 0; i < tabs.length; i++)
                        if (item.getItemId() == tabs[i]) {
                            show(i);
                            return true;
                        }
                    return false;
                });
        navigation.setOnItemReselectedListener(
                item -> {
                    if (item.getItemId() != R.id.navAdminCirculation)
                        ((ScrollView) activity.findViewById(R.id.adminScroll)).smoothScrollTo(0, 0);
                });
        activity.findViewById(R.id.buttonAdminAccount).setOnClickListener(v -> show(ACCOUNT));
        activity.findViewById(R.id.buttonAdminReview).setOnClickListener(v -> show(APPROVALS));
        activity.findViewById(R.id.actionAdminRegistrations)
                .setOnClickListener(v -> show(APPROVALS));
        activity.findViewById(R.id.actionAdminResets)
                .setOnClickListener(
                        v -> {
                            show(APPROVALS);
                            ScrollView scroll = activity.findViewById(R.id.adminScroll);
                            scroll.post(
                                    () -> {
                                        android.graphics.Rect rect = new android.graphics.Rect();
                                        View target =
                                                activity.findViewById(
                                                        R.id.textPasswordResetPendingCount);
                                        target.getDrawingRect(rect);
                                        scroll.offsetDescendantRectToMyCoords(target, rect);
                                        scroll.smoothScrollTo(0, rect.top);
                                    });
                        });
        show(restoredTab);
    }

    public int selectedTab() {
        return selected;
    }

    public void show(int tab) {
        selected = tab >= HOME && tab <= APPROVALS ? tab : HOME;
        for (int i = 0; i < panels.length; i++) {
            activity.findViewById(panels[i])
                    .setVisibility(i == selected ? View.VISIBLE : View.GONE);
        }
        activity.findViewById(R.id.adminRequests).setVisibility(
                selected == APPROVALS ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.adminScroll)
                .setVisibility(selected == CIRCULATION ? View.GONE : View.VISIBLE);
        activity.findViewById(R.id.adminHomeHeader)
                .setVisibility(selected == HOME ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.adminCirculation)
                .setVisibility(selected == CIRCULATION ? View.VISIBLE : View.GONE);
        navigation.getMenu().findItem(tabs[selected == APPROVALS ? REQUESTS : selected]).setChecked(true);
        ((ScrollView) activity.findViewById(R.id.adminScroll)).scrollTo(0, 0);
    }
}
