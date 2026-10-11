package com.example.mylibrary.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthService {
    private final FirebaseAuth auth;
    private final SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "LibraryPrefs";
    private static final String USER_TYPE_KEY = "userType";

    public AuthService(Context context) {
        this.auth = FirebaseAuth.getInstance();
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void registerUser(String email, String password, String userType, AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    if (result.getUser() != null) {
                        saveUserType(result.getUser().getUid(), userType);
                        callback.onSuccess(result.getUser());
                    } else {
                        callback.onError(new Exception("User creation failed"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public void loginUser(String email, String password, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    if (result.getUser() != null) {
                        callback.onSuccess(result.getUser());
                    } else {
                        callback.onError(new Exception("Login failed"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public void logout() {
        auth.signOut();
        clearUserPreferences();
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public boolean isUserLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public void saveUserType(String userId, String userType) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("userId", userId);
        editor.putString(USER_TYPE_KEY, userType);
        editor.apply();
    }

    public String getUserType() {
        return sharedPreferences.getString(USER_TYPE_KEY, "CUSTOMER");
    }

    private void clearUserPreferences() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(Exception e);
    }
}
