package com.example.libraryonline.domain;

import com.example.libraryonline.domain.calculator.PenaltyCalculator;
import org.junit.Test;
import static org.junit.Assert.*;

public class PenaltyCalculatorTest {
    @Test public void noFineWhenReturnedOnDueDate() {
        assertEquals(0, PenaltyCalculator.lateDays("2026-10-20", "2026-10-20"));
        assertEquals(0L, PenaltyCalculator.lateFeeVnd("2026-10-20", "2026-10-20"));
    }

    @Test public void noFineWhenReturnedEarly() {
        assertEquals(0, PenaltyCalculator.lateDays("2026-10-20", "2026-10-19"));
    }

    @Test public void chargesFiveThousandPerOverdueCalendarDay() {
        assertEquals(1, PenaltyCalculator.lateDays("2026-10-20", "2026-10-21"));
        assertEquals(15000L, PenaltyCalculator.lateFeeVnd("2026-10-20", "2026-10-23"));
    }

    @Test public void handlesMonthAndYearBoundaries() {
        assertEquals(2, PenaltyCalculator.lateDays("2026-12-31", "2027-01-02"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedDate() {
        PenaltyCalculator.lateDays("20/10/2026", "2026-10-21");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsImpossibleDate() {
        PenaltyCalculator.lateDays("2026-02-30", "2026-03-01");
    }
}
