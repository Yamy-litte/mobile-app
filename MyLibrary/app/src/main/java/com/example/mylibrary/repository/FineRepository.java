package com.example.mylibrary.repository;

import com.example.mylibrary.dao.FineDao;
import com.example.mylibrary.dao.DataCallback;
import com.example.mylibrary.dao.FirestoreFineDao;
import com.example.mylibrary.models.Fine;
import java.util.List;

public class FineRepository {
    private final FineDao fineDao = new FirestoreFineDao();

    public void addFine(Fine fine, DataCallback<Fine> callback) {
        fineDao.addFine(fine, callback);
    }

    public void getFinesByCustomerId(String customerId, DataCallback<List<Fine>> callback) {
        fineDao.getFinesByCustomerId(customerId, callback);
    }

    public void getUnpaidFines(String customerId, DataCallback<List<Fine>> callback) {
        fineDao.getUnpaidFines(customerId, callback);
    }

    public void updateFine(Fine fine, DataCallback<Void> callback) {
        fineDao.updateFine(fine, callback);
    }

    public void payFine(String id, DataCallback<Void> callback) {
        fineDao.payFine(id, callback);
    }
}
