package com.example.mylibrary.dao;

import com.example.mylibrary.models.Admin;
import java.util.List;

public interface AdminDao {
    void addAdmin(Admin admin, DataCallback<Admin> callback);
    void getAdminById(String id, DataCallback<Admin> callback);
    void getAllAdmins(DataCallback<List<Admin>> callback);
    void updateAdmin(Admin admin, DataCallback<Void> callback);
}
