package com.example.libraryonline.ui.admin;

import android.app.Activity;
import android.view.View;
import android.widget.ScrollView;

import com.example.libraryonline.R;

/** Navigation within the existing Admin screen, with no new business operations. */
public final class AdminHomeView {
    public static final int HOME = 0, REQUESTS = 1, ACCOUNT = 2;
    private final Activity activity;
    private final int[] panels = {R.id.adminOverview, R.id.adminRequests, R.id.adminAccount};
    private final int[] tabs = {R.id.navAdminHome, R.id.navAdminRequests, R.id.navAdminAccount};
    private int selected;

    public AdminHomeView(Activity activity, int restoredTab) {
        this.activity = activity;
        for (int id :
                new int[] {
                    R.id.textAdminOverview,
                    R.id.textAdminWork,
                    R.id.textAdminShortcuts,
                    R.id.textAdminRequestsHeading,
                    R.id.textAdminAccountHeading
                })
            androidx.core.view.ViewCompat.setAccessibilityHeading(activity.findViewById(id), true);
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            activity.findViewById(tabs[i]).setOnClickListener(v -> show(tab));
        }
        activity.findViewById(R.id.buttonAdminAccount).setOnClickListener(v -> show(ACCOUNT));
        activity.findViewById(R.id.buttonAdminReview).setOnClickListener(v -> show(REQUESTS));
        activity.findViewById(R.id.actionAdminRegistrations)
                .setOnClickListener(v -> show(REQUESTS));
        activity.findViewById(R.id.actionAdminResets)
                .setOnClickListener(
                        v -> {
                            show(REQUESTS);
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
        selected = tab >= HOME && tab <= ACCOUNT ? tab : HOME;
        for (int i = 0; i < panels.length; i++) {
            activity.findViewById(panels[i])
                    .setVisibility(i == selected ? View.VISIBLE : View.GONE);
            activity.findViewById(tabs[i]).setSelected(i == selected);
        }
        ((ScrollView) activity.findViewById(R.id.adminScroll)).scrollTo(0, 0);
    }
}
