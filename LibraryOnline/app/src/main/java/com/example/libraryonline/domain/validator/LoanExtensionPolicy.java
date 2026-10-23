package com.example.libraryonline.domain.validator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Shared post-checkout extension policy for in-person and Online loans.
 * The loan origin affects approval/pickup only, not the seven-day/once rule.
 * Historical DatTruoc queues were removed and do not block extensions.
 */
public final class LoanExtensionPolicy {
    public static final int DAYS = 7;
    private static final long DAY_MS = 86_400_000L;

    private LoanExtensionPolicy() {}

    public static String rejection(boolean returned, int count, long dueMillis, long nowMillis) {
        if (returned) return "Cuốn sách đã trả, không thể gia hạn.";
        if (count >= 1) return "Mỗi lần mượn chỉ được gia hạn một lần.";
        if (dueMillis <= 0) return "Không xác định được hạn trả.";
        if (nowMillis > dueMillis) return "Sách đã quá hạn, không thể gia hạn.";
        return null;
    }

    /** Firestore Timestamp uses milliseconds; preserve the original time of day. */
    public static long extendMillis(long dueMillis) {
        return Math.addExact(dueMillis, DAYS * DAY_MS);
    }

    /** SQLite stores dueDate as yyyy-MM-dd: valid through the entire due date. */
    public static String extendLocalDate(String dueDate, String today) {
        if (dueDate == null || today == null || dueDate.compareTo(today) < 0)
            throw new IllegalArgumentException("Sách đã quá hạn hoặc thiếu hạn trả.");
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        f.setLenient(false);
        try {
            Date parsed = f.parse(dueDate);
            if (parsed == null || !dueDate.equals(f.format(parsed)))
                throw new IllegalArgumentException("Hạn trả không hợp lệ.");
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(parsed);
            calendar.add(Calendar.DAY_OF_MONTH, DAYS);
            return f.format(calendar.getTime());
        } catch (ParseException e) {
            throw new IllegalArgumentException("Hạn trả không hợp lệ.", e);
        }
    }
}
