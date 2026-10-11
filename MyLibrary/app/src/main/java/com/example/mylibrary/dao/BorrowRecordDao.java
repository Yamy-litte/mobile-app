package com.example.mylibrary.dao;

import com.example.mylibrary.models.BorrowRecord;
import java.util.List;

public interface BorrowRecordDao {
    void addBorrowRecord(BorrowRecord record, DataCallback<BorrowRecord> callback);
    void getBorrowRecordById(String id, DataCallback<BorrowRecord> callback);
    void getBorrowRecordsByCustomerId(String customerId, DataCallback<List<BorrowRecord>> callback);
    void getActiveBorrowRecords(DataCallback<List<BorrowRecord>> callback);
    void getOverdueBorrowRecords(DataCallback<List<BorrowRecord>> callback);
    void updateBorrowRecord(BorrowRecord record, DataCallback<Void> callback);
    void returnBorrowRecord(String id, long returnDate, String condition, DataCallback<Void> callback);
}
