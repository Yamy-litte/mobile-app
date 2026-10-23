package com.example.libraryonline.domain;

import com.example.libraryonline.domain.validator.LoanExtensionPolicy;
import org.junit.Test;
import static org.junit.Assert.*;

public class LoanExtensionPolicyTest {
    @Test public void renewsExactlySevenDaysAfterPickup() {
        long due = 1_800_000_000_000L;
        assertNull(LoanExtensionPolicy.rejection(false, 0, due, due - 1));
        assertEquals(due + 7L * 86_400_000L, LoanExtensionPolicy.extendMillis(due));
    }

    @Test public void cannotRenewAlreadyReturnedOverdueOrExtendedLoan() {
        long due = 1_800_000_000_000L;
        assertNotNull(LoanExtensionPolicy.rejection(true, 0, due, due - 1));
        assertNotNull(LoanExtensionPolicy.rejection(false, 1, due, due - 1));
        assertNotNull(LoanExtensionPolicy.rejection(false, 0, due, due + 1));
        assertNotNull(LoanExtensionPolicy.rejection(false, 0, 0, due - 1));
    }

    @Test public void localLoansRemainEligibleOnTheDueDate() {
        assertEquals("2026-11-07",
                LoanExtensionPolicy.extendLocalDate("2026-10-31", "2026-10-31"));
        try {
            LoanExtensionPolicy.extendLocalDate("2026-10-30", "2026-10-31");
            fail("Late local borrow must not be extendable");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("quá hạn"));
        }
    }
}
