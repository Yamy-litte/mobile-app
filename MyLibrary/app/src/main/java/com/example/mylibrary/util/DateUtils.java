package com.example.mylibrary.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateUtils {
    private static final String DATE_FORMAT = "dd/MM/yyyy";
    private static final String DATETIME_FORMAT = "dd/MM/yyyy HH:mm";

    public static String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public static String formatDateTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATETIME_FORMAT, Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public static long addDays(long timestamp, int days) {
        return timestamp + (days * 24 * 60 * 60 * 1000L);
    }

    public static int getDaysDifference(long startTime, long endTime) {
        long diffTime = Math.abs(endTime - startTime);
        return (int) (diffTime / (24 * 60 * 60 * 1000L));
    }

    public static boolean isOverdue(long dueDate) {
        return dueDate < System.currentTimeMillis();
    }
}
