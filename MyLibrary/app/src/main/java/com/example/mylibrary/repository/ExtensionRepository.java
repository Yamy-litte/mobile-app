package com.example.mylibrary.repository;

import com.example.mylibrary.dao.ExtensionDao;
import com.example.mylibrary.dao.DataCallback;
import com.example.mylibrary.dao.FirestoreExtensionDao;
import com.example.mylibrary.models.Extension;
import java.util.List;

public class ExtensionRepository {
    private final ExtensionDao extensionDao = new FirestoreExtensionDao();

    public void addExtension(Extension extension, DataCallback<Extension> callback) {
        extensionDao.addExtension(extension, callback);
    }

    public void getExtensionsByBorrowRecordId(String borrowRecordId, DataCallback<List<Extension>> callback) {
        extensionDao.getExtensionsByBorrowRecordId(borrowRecordId, callback);
    }

    public void getPendingExtensions(DataCallback<List<Extension>> callback) {
        extensionDao.getPendingExtensions(callback);
    }

    public void updateExtension(Extension extension, DataCallback<Void> callback) {
        extensionDao.updateExtension(extension, callback);
    }

    public void approveExtension(String id, DataCallback<Void> callback) {
        extensionDao.approveExtension(id, callback);
    }

    public void rejectExtension(String id, DataCallback<Void> callback) {
        extensionDao.rejectExtension(id, callback);
    }
}
