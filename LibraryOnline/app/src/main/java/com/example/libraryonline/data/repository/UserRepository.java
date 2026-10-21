package com.example.libraryonline.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.libraryonline.data.local.dao.UserDao;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.model.User;

import java.util.List;

/** Repository boundary for user-profile persistence. Never stores authentication credentials. */
public final class UserRepository {
    private final DatabaseHelper helper;

    public UserRepository(Context context) { helper = new DatabaseHelper(context); }

    public long createProfile(User user) {
        SQLiteDatabase db = helper.getWritableDatabase();
        return new UserDao(db).insert(user);
    }

    public long upsertProfile(User user) {
        return new UserDao(helper.getWritableDatabase()).upsertFromCloud(user);
    }

    public User getById(String userId) {
        return new UserDao(helper.getReadableDatabase()).findById(userId);
    }

    public User getByEmail(String email) {
        return new UserDao(helper.getReadableDatabase()).findByEmail(email);
    }

    public int updateProfile(User user) {
        return new UserDao(helper.getWritableDatabase()).update(user);
    }

    public List<User> getActiveUsers() {
        return new UserDao(helper.getReadableDatabase()).getActiveUsers();
    }

    public void close() { helper.close(); }
}