package com.example.libraryapp;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

/** Connects the four Reader bottom tabs across customer screens. */
public final class ReaderTabNavigation {
    private ReaderTabNavigation() {}
    public static void bind(Activity activity) {
        View home = activity.findViewById(R.id.tabHome);
        View borrowed = activity.findViewById(R.id.tabBorrowedBooks);
        View notifications = activity.findViewById(R.id.tabNotifications);
        View account = activity.findViewById(R.id.tabAccount);
        if (home != null) home.setOnClickListener(v -> open(activity, ReaderHomeActivity.class));
        if (borrowed != null) borrowed.setOnClickListener(v -> open(activity, ReaderBorrowedBooksActivity.class));
        if (notifications != null) notifications.setOnClickListener(v -> open(activity, ReaderNotificationsActivity.class));
        if (account != null) account.setOnClickListener(v -> open(activity, ReaderAccountActivity.class));
    }
    private static void open(Activity current, Class<?> target) {
        if (current.getClass() == target) return;
        Intent intent = new Intent(current, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        current.startActivity(intent);
        current.overridePendingTransition(0, 0);
    }
}