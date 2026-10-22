package com.example.libraryonline.domain.calculator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/** Fine calculation matching the functional specification: 5,000 VND per overdue calendar day. */
public final class PenaltyCalculator {
    public static final int LATE_FEE_PER_DAY_VND = 5000;

    private PenaltyCalculator() {}

    /**
     * Dates must be ISO calendar dates (yyyy-MM-dd), as used by the circulation records.
     * Returns zero for on-time/early returns. Invalid or missing dates are rejected.
     */
    public static int lateDays(String dueDate, String actualReturnDate) {
        long due = parseDate(dueDate);
        long returned = parseDate(actualReturnDate);
        if (returned <= due) return 0;
        long days = (returned - due) / 86400000L;
        if (days > Integer.MAX_VALUE) throw new IllegalArgumentException("Date interval is too large");
        return (int) days;
    }

    public static long lateFeeVnd(String dueDate, String actualReturnDate) {
        return Math.multiplyExact((long) lateDays(dueDate, actualReturnDate), (long) LATE_FEE_PER_DAY_VND);
    }

    private static long parseDate(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Date must use yyyy-MM-dd");
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            java.util.Date parsed = format.parse(value);
            if (parsed == null || !format.format(parsed).equals(value)) {
                throw new IllegalArgumentException("Invalid date: " + value);
            }
            return parsed.getTime();
        } catch (ParseException ex) {
            throw new IllegalArgumentException("Invalid date: " + value, ex);
        }
    }
}
