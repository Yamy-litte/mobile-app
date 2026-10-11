
package com.example.mylibrary.service;

import com.example.mylibrary.dao.AuthDAO;
import com.example.mylibrary.model.Customer;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthService {

    private final FirebaseAuth firebaseAuth;
    private final AuthDAO authDAO;

    public AuthService() {
        firebaseAuth = FirebaseAuth.getInstance();
        authDAO = new AuthDAO();
    }

    // Đăng ký tài khoản và lưu hồ sơ khách hàng

    public void register(
            String fullName,
            String email,
            String password,
            FirebaseAuthCallback callback) {

        if (callback == null) {
            return;
        }

        if (fullName == null || fullName.trim().isEmpty()) {
            callback.onFailure("Vui lòng nhập họ và tên.");
            return;
        }

        if (email == null || email.trim().isEmpty()) {
            callback.onFailure("Vui lòng nhập email.");
            return;
        }

        if (password == null || password.length() < 6) {
            callback.onFailure("Mật khẩu phải có ít nhất 6 ký tự.");
            return;
        }

        String name = fullName.trim();
        String normalizedEmail = email.trim();

        firebaseAuth.createUserWithEmailAndPassword(
                normalizedEmail,
                password
        ).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                String message = task.getException() != null
                        ? task.getException().getMessage()
                        : "Không thể đăng ký tài khoản.";

                callback.onFailure(message);
                return;
            }

            FirebaseUser user = firebaseAuth.getCurrentUser();

            if (user == null) {
                callback.onFailure(
                        "Tài khoản đã tạo nhưng không lấy được thông tin người dùng."
                );
                return;
            }

            long currentTime = System.currentTimeMillis();

            Customer customer = new Customer(
                    user.getUid(),
                    normalizedEmail,
                    name,
                    "",
                    "",
                    "active",
                    currentTime,
                    currentTime
            );

            authDAO.saveCustomer(customer,
                    new AuthDAO.OnCustomerSaveListener() {
                        @Override
                        public void onSuccess() {
                            callback.onSuccess(user);
                        }

                        @Override
                        public void onFailure(String error) {
                            callback.onFailure(
                                    "Tài khoản đã được tạo nhưng lưu hồ sơ thất bại: "
                                            + error
                            );
                        }
                    });
        });
    }


    // Đăng nhập bằng email và mật khẩu
    public void login(
            String email,
            String password,
            FirebaseAuthCallback callback) {

        if (callback == null) {
            return;
        }

        if (email == null || email.trim().isEmpty()) {
            callback.onFailure("Vui lòng nhập email.");
            return;
        }

        if (password == null || password.isEmpty()) {
            callback.onFailure("Vui lòng nhập mật khẩu.");
            return;
        }

        firebaseAuth.signInWithEmailAndPassword(
                email.trim(),
                password
        ).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = firebaseAuth.getCurrentUser();

                if (user != null) {
                    callback.onSuccess(user);
                } else {
                    callback.onFailure(
                            "Đăng nhập thành công nhưng không lấy được thông tin người dùng."
                    );
                }
            } else {
                String message = task.getException() != null
                        ? task.getException().getMessage()
                        : "Email hoặc mật khẩu không chính xác.";

                callback.onFailure(message);
            }
        });
    }

    // Đăng xuất
    public void logout() {
        firebaseAuth.signOut();
    }

    // Lấy người dùng hiện tại
    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    // Kiểm tra trạng thái đăng nhập
    public boolean isLoggedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    // Callback trả kết quả cho giao diện
    public interface FirebaseAuthCallback {
        void onSuccess(FirebaseUser user);

        void onFailure(String errorMessage);
    }
}
