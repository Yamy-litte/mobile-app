package com.example.libraryapp;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

/** Navigation for the five Admin tabs shown in the project Figma. */
public final class AdminTabNavigation {
    private AdminTabNavigation() {}

    public static void bind(Activity activity) {
        bindDestination(activity, R.id.adminTabHome, AdminHomeActivity.class);
        bindDestination(activity, R.id.adminTabBooks, AdminBookInventoryActivity.class);
        bindDestination(activity, R.id.adminTabScan, AdminScanActivity.class);
        // The Home screen currently contains the live request queue and borrow-request actions.
        bindDestination(activity, R.id.adminTabLoans, AdminHomeActivity.class);
        bindDestination(activity, R.id.adminTabAccount, AdminAccountActivity.class);
    }

    private static void bindDestination(Activity activity, int id, Class<?> destination) {
        View tab = activity.findViewById(id);
        if (tab == null) return;
        tab.setOnClickListener(v -> {
            if (activity.getClass().equals(destination)) return;
            Intent intent = new Intent(activity, destination);
            activity.startActivity(intent);
        });
    }
}
