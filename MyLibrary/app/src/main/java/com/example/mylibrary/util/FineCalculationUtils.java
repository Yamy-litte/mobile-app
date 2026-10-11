package com.example.mylibrary.util;

public class FineCalculationUtils {
    public static double calculateOverdueFine(int daysOverdue) {
        return daysOverdue * Constants.FINE_PER_DAY_OVERDUE;
    }

    public static double calculateDamagedFine() {
        return Constants.FINE_DAMAGED;
    }

    public static double calculateLostFine() {
        return Constants.FINE_LOST;
    }

    public static double calculateTotalFine(double overdueFine, boolean isDamaged, boolean isLost) {
        double total = overdueFine;
        if (isDamaged) total += Constants.FINE_DAMAGED;
        if (isLost) total += Constants.FINE_LOST;
        return total;
    }
}
