package com.example.mylibrary.repository;

import com.example.mylibrary.dao.BorrowRecordDao;
import com.example.mylibrary.dao.DataCallback;
import com.example.mylibrary.dao.FirestoreBorrowRecordDao;
import com.example.mylibrary.models.BorrowRecord;
import java.util.List;

public class BorrowRecordRepository {
    private final BorrowRecordDao borrowRecordDao = new FirestoreBorrowRecordDao();

    public void addBorrowRecord(BorrowRecord record, DataCallback<BorrowRecord> callback) {
        borrowRecordDao.addBorrowRecord(record, callback);
    }

    public void getBorrowRecordById(String id, DataCallback<BorrowRecord> callback) {
        borrowRecordDao.getBorrowRecordById(id, callback);
    }

    public void getBorrowRecordsByCustomerId(String customerId, DataCallback<List<BorrowRecord>> callback) {
        borrowRecordDao.getBorrowRecordsByCustomerId(customerId, callback);
    }

    public void getActiveBorrowRecords(DataCallback<List<BorrowRecord>> callback) {
        borrowRecordDao.getActiveBorrowRecords(callback);
    }

    public void getOverdueBorrowRecords(DataCallback<List<BorrowRecord>> callback) {
        borrowRecordDao.getOverdueBorrowRecords(callback);
    }

    public void updateBorrowRecord(BorrowRecord record, DataCallback<Void> callback) {
        borrowRecordDao.updateBorrowRecord(record, callback);
    }

    public void returnBorrowRecord(String id, long returnDate, String condition, DataCallback<Void> callback) {
        borrowRecordDao.returnBorrowRecord(id, returnDate, condition, callback);
    }
}
