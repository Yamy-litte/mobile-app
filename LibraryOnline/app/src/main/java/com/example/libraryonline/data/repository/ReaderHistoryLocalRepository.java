package com.example.libraryonline.data.repository;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.libraryonline.data.local.db.DatabaseHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only history for a selected Reader, on THIS device only.
 * Cloud receipts are fetched separately; there is no cross-device local ledger sync.
 */
public final class ReaderHistoryLocalRepository implements AutoCloseable {
    private final DatabaseHelper helper;

    public ReaderHistoryLocalRepository(Context context) {
        helper = new DatabaseHelper(context.getApplicationContext());
    }

    public static final class Loan {
        public String detailId, receiptId, loanType, title, copyId, barcode;
        public String borrowedAt, dueAt, returnedAt, status, receiptStatus;
        public int extensionCount;
    }

    public static final class Payment {
        public String penaltyId, method, status, receivedAt;
        public long amount;
    }

    public static final class Fine {
        public String id, detailId, copyId, title, status, createdAt, paidAt;
        public int lateDays;
        public long amountDue, amountPaid;
        public final List<Payment> payments = new ArrayList<>();
        public long remaining() { return Math.max(0, amountDue - amountPaid); }
    }

    public static final class Snapshot {
        public final List<Loan> loans = new ArrayList<>();
        public final List<Fine> fines = new ArrayList<>();
    }

    public Snapshot load(String userId) {
        if (userId == null || userId.trim().isEmpty())
            throw new IllegalArgumentException("Thiếu mã độc giả.");
        Snapshot result = new Snapshot();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT d.loanDetailId,r.loanReceiptId,r.loanType,"
                + "COALESCE(bt.title,''),d.copyId,COALESCE(bc.barcode,''),"
                + "d.borrowDate,d.dueDate,d.returnDate,d.detailStatus,d.extensionCount,r.receiptStatus "
                + "FROM LoanDetails d JOIN LoanReceipts r ON r.loanReceiptId=d.loanReceiptId "
                + "LEFT JOIN BookCopies bc ON bc.copyId=d.copyId "
                + "LEFT JOIN BookTitles bt ON bt.bookTitleId=bc.bookTitleId "
                + "WHERE r.userId=? ORDER BY COALESCE(d.borrowDate,d.createdAt) DESC,d.createdAt DESC",
                new String[]{userId})) {
            while (c.moveToNext()) {
                Loan row = new Loan();
                row.detailId = c.getString(0);
                row.receiptId = c.getString(1);
                row.loanType = c.getString(2);
                row.title = c.getString(3);
                row.copyId = c.getString(4);
                row.barcode = c.getString(5);
                row.borrowedAt = c.getString(6);
                row.dueAt = c.getString(7);
                row.returnedAt = c.getString(8);
                row.status = c.getString(9);
                row.extensionCount = c.getInt(10);
                row.receiptStatus = c.getString(11);
                result.loans.add(row);
            }
        }
        Map<String, Fine> finesById = new LinkedHashMap<>();
        try (Cursor c = db.rawQuery(
                "SELECT p.penaltyId,p.loanDetailId,p.copyId,COALESCE(bt.title,''),"
                + "p.daysLate,p.amountDue,p.amountPaid,p.penaltyStatus,p.createdAt,p.paidAt "
                + "FROM Penalties p LEFT JOIN BookCopies bc ON bc.copyId=p.copyId "
                + "LEFT JOIN BookTitles bt ON bt.bookTitleId=bc.bookTitleId "
                + "WHERE p.userId=? AND p.penaltyType='LateReturn' "
                + "ORDER BY p.createdAt DESC", new String[]{userId})) {
            while (c.moveToNext()) {
                Fine fine = new Fine();
                fine.id = c.getString(0);
                fine.detailId = c.getString(1);
                fine.copyId = c.getString(2);
                fine.title = c.getString(3);
                fine.lateDays = c.getInt(4);
                fine.amountDue = c.getLong(5);
                fine.amountPaid = c.getLong(6);
                fine.status = c.getString(7);
                fine.createdAt = c.getString(8);
                fine.paidAt = c.getString(9);
                finesById.put(fine.id, fine);
                result.fines.add(fine);
            }
        }
        try (Cursor c = db.rawQuery(
                "SELECT pp.penaltyId,pp.amountReceived,pp.paymentMethod,"
                + "pp.paymentStatus,pp.receivedAt "
                + "FROM PenaltyPayments pp JOIN Penalties p ON p.penaltyId=pp.penaltyId "
                + "WHERE p.userId=? AND p.penaltyType='LateReturn' "
                + "ORDER BY pp.receivedAt ASC", new String[]{userId})) {
            while (c.moveToNext()) {
                Fine fine = finesById.get(c.getString(0));
                if (fine == null) continue;
                Payment payment = new Payment();
                payment.penaltyId = c.getString(0);
                payment.amount = c.getLong(1);
                payment.method = c.getString(2);
                payment.status = c.getString(3);
                payment.receivedAt = c.getString(4);
                fine.payments.add(payment);
            }
        }
        return result;
    }

    @Override public void close() { helper.close(); }
}
