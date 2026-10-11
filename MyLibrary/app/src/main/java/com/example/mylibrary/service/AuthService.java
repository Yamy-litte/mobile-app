package com.example.mylibrary.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.mylibrary.model.Customer;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AuthService {
    private final FirebaseAuth auth;
    private SharedPreferences sharedPreferences;
    private final FirebaseFirestore db;
    private static final String PREFS_NAME = "LibraryPrefs";
    private static final String USER_TYPE_KEY = "userType";

    public AuthService() {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
    }

    public AuthService(Context context) {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
        if (context != null) {
            this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    public interface FirebaseAuthCallback {
        void onSuccess(FirebaseUser user);
        void onFailure(String errorMessage);
    }

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(Exception e);
    }

    public void login(String email, String password, FirebaseAuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    if (result.getUser() != null) {
                        callback.onSuccess(result.getUser());
                    } else {
                        callback.onFailure("Đăng nhập thất bại.");
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(
                        e.getMessage() != null ? e.getMessage() : "Đăng nhập thất bại."
                ));
    }

    public void register(String fullName, String email, String password, FirebaseAuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser != null) {
                        Customer customer = new Customer(
                                firebaseUser.getUid(),
                                email,
                                fullName,
                                "",
                                "",
                                "ACTIVE",
                                System.currentTimeMillis(),
                                System.currentTimeMillis()
                        );
                        db.collection("customers").document(firebaseUser.getUid()).set(customer)
                                .addOnSuccessListener(unused -> callback.onSuccess(firebaseUser))
                                .addOnFailureListener(e -> callback.onFailure("Tạo tài khoản thành công nhưng không thể lưu thông tin: " + e.getMessage()));
                    } else {
                        callback.onFailure("Đăng ký thất bại.");
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(
                        e.getMessage() != null ? e.getMessage() : "Đăng ký thất bại."
                ));
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
        if (sharedPreferences != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("userId", userId);
            editor.putString(USER_TYPE_KEY, userType);
            editor.apply();
        }
    }

    public String getUserType() {
        if (sharedPreferences != null) {
            return sharedPreferences.getString(USER_TYPE_KEY, "CUSTOMER");
        }
        return "CUSTOMER";
    }

    private void clearUserPreferences() {
        if (sharedPreferences != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.clear();
            editor.apply();
        }
    }
}
