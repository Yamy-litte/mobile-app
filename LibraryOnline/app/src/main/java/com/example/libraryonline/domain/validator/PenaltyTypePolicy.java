package com.example.libraryonline.domain.validator;

/**
 * New penalty workflow supports overdue returns only. Existing SQLite enum
 * values (including historical LostBook rows) remain readable for compatibility.
 */
public final class PenaltyTypePolicy {
    public static final String LATE_RETURN = "LateReturn";

    private PenaltyTypePolicy() {}

    public static boolean canCollect(String type) {
        return LATE_RETURN.equals(type);
    }
}
