package com.example.mylibrary.dao;

import com.example.mylibrary.models.Fine;
import java.util.List;

public interface FineDao {
    void addFine(Fine fine, DataCallback<Fine> callback);
    void getFinesByCustomerId(String customerId, DataCallback<List<Fine>> callback);
    void getUnpaidFines(String customerId, DataCallback<List<Fine>> callback);
    void updateFine(Fine fine, DataCallback<Void> callback);
    void payFine(String id, DataCallback<Void> callback);
}
