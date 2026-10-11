package com.example.mylibrary.repository;

import com.example.mylibrary.dao.AdminDao;
import com.example.mylibrary.dao.DataCallback;
import com.example.mylibrary.dao.FirestoreAdminDao;
import com.example.mylibrary.models.Admin;
import java.util.List;

public class AdminRepository {
    private final AdminDao adminDao = new FirestoreAdminDao();

    public void addAdmin(Admin admin, DataCallback<Admin> callback) {
        adminDao.addAdmin(admin, callback);
    }

    public void getAdminById(String id, DataCallback<Admin> callback) {
        adminDao.getAdminById(id, callback);
    }

    public void getAllAdmins(DataCallback<List<Admin>> callback) {
        adminDao.getAllAdmins(callback);
    }

    public void updateAdmin(Admin admin, DataCallback<Void> callback) {
        adminDao.updateAdmin(admin, callback);
    }
}
