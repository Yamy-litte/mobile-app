package com.example.libraryapp;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

/** Navigation for Admin tabs. Loan management lives on Home, not in a separate bottom tab. */
public final class AdminTabNavigation {
    private AdminTabNavigation() {}

    public static void bind(Activity activity) {
        bindDestination(activity, R.id.adminTabHome, AdminHomeActivity.class);
        bindDestination(activity, R.id.adminTabBooks, AdminBookInventoryActivity.class);
        bindDestination(activity, R.id.adminTabScan, AdminScanActivity.class);
        View loansTab = activity.findViewById(R.id.adminTabLoans);
        if (loansTab != null) loansTab.setVisibility(View.GONE);
        bindDestination(activity, R.id.adminTabAccount, AdminAccountActivity.class);
    }

    private static void bindDestination(Activity activity, int id, Class<?> destination) {
        View tab = activity.findViewById(id);
        if (tab == null) return;
        tab.setOnClickListener(v -> {
            if (activity.getClass().equals(destination)) return;
            activity.startActivity(new Intent(activity, destination));
        });
    }
}