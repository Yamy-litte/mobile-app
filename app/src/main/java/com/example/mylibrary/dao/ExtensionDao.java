package com.example.mylibrary.dao;

import com.example.mylibrary.models.Extension;
import java.util.List;

public interface ExtensionDao {
    void addExtension(Extension extension, DataCallback<Extension> callback);
    void getExtensionById(String id, DataCallback<Extension> callback);
    void getExtensionsByBorrowRecordId(String borrowRecordId, DataCallback<List<Extension>> callback);
    void getPendingExtensions(DataCallback<List<Extension>> callback);
    void updateExtension(Extension extension, DataCallback<Void> callback);
    void approveExtension(String id, DataCallback<Void> callback);
    void rejectExtension(String id, DataCallback<Void> callback);
}
