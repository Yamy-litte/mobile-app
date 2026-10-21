package com.example.libraryonline.ui.admin;

import android.app.Activity;
import android.content.Intent;

/** Reuse the Admin root instead of stacking another home for every inventory tab tap. */
public final class AdminNavigation {
    public static final String EXTRA_TAB = "adminDestinationTab";

    private AdminNavigation() {}

    public static void returnToMain(Activity source, int tab) {
        source.startActivity(
                new Intent(source, AdminMainActivity.class)
                        .putExtra(EXTRA_TAB, tab)
                        .addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        source.finish();
    }
}
