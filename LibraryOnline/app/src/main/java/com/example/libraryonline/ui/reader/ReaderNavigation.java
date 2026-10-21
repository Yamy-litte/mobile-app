package com.example.libraryonline.ui.reader;

import android.app.Activity;
import android.content.Intent;

/** Return from a book detail to the existing Reader root and its selected destination. */
public final class ReaderNavigation {
    public static final String EXTRA_TAB = "readerDestinationTab";

    private ReaderNavigation() {}

    public static void returnToMain(Activity source, int tab) {
        source.startActivity(
                new Intent(source, ReaderMainActivity.class)
                        .putExtra(EXTRA_TAB, tab)
                        .addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        source.finish();
    }
}
