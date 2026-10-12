package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.UserDao;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private EditText editEmail, editPassword;
    private TextView textAuthStatus;
    private UserDao userDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        auth = FirebaseAuth.getInstance();
        userDao = new UserDao(this);
        editEmail = findViewById(R.id.editAuthEmail);
        editPassword = findViewById(R.id.editAuthPassword);
        textAuthStatus = findViewById(R.id.textAuthStatus);

        findViewById(R.id.buttonRegister).setOnClickListener(v -> register());
        findViewById(R.id.buttonLogin).setOnClickListener(v -> login());
        findViewById(R.id.buttonResetPassword).setOnClickListener(v -> resetPassword());
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && user.isEmailVerified()) openMenu();
    }

    private String emailInput() {
        return editEmail.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String passwordInput() {
        return editPassword.getText().toString();
    }

    private boolean validateInput() {
        String email = emailInput();
        String password = passwordInput();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError("Vui lòng nhập email hợp lệ.");
            editEmail.requestFocus();
            return false;
        }
        if (password.length() < 6) {
            editPassword.setError("Mật khẩu phải có ít nhất 6 ký tự.");
            editPassword.requestFocus();
            return false;
        }
        return true;
    }

    private void register() {
        if (!validateInput()) return;
        setBusy(true);
        auth.createUserWithEmailAndPassword(emailInput(), passwordInput())
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        setBusy(false);
                        showError(task.getException(), "Không thể tạo tài khoản.");
                        return;
                    }
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        setBusy(false);
                        showMessage("Tạo tài khoản thất bại. Vui lòng thử lại.");
                        return;
                    }
                    user.sendEmailVerification().addOnCompleteListener(this, verifyTask -> {
                        setBusy(false);
                        if (verifyTask.isSuccessful()) {
                            showMessage("Đã gửi liên kết xác minh đến email. Hãy xác minh rồi đăng nhập.");
                        } else {
                            showError(verifyTask.getException(), "Không gửi được email xác minh.");
                        }
                        auth.signOut();
                    });
                });
    }

    private void login() {
        if (!validateInput()) return;
        setBusy(true);
        auth.signInWithEmailAndPassword(emailInput(), passwordInput())
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        setBusy(false);
                        showError(task.getException(), "Đăng nhập thất bại.");
                        return;
                    }
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        setBusy(false);
                        showMessage("Không tìm thấy tài khoản đăng nhập.");
                        return;
                    }
                    user.reload().addOnCompleteListener(this, reloadTask -> {
                        FirebaseUser refreshedUser = auth.getCurrentUser();
                        if (refreshedUser == null || !refreshedUser.isEmailVerified()) {
                            setBusy(false);
                            auth.signOut();
                            showMessage("Email chưa được xác minh. Hãy mở email xác minh trước khi đăng nhập.");
                            return;
                        }
                        long localId = userDao.saveProfile(refreshedUser.getEmail(), "Customer");
                        setBusy(false);
                        if (localId < 1) {
                            auth.signOut();
                            showMessage("Không lưu được hồ sơ người dùng trong SQLite.");
                            return;
                        }
                        openMenu();
                    });
                });
    }

    private void resetPassword() {
        String email = emailInput();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError("Nhập email hợp lệ để đặt lại mật khẩu.");
            editEmail.requestFocus();
            return;
        }
        setBusy(true);
        auth.sendPasswordResetEmail(email).addOnCompleteListener(this, task -> {
            setBusy(false);
            if (task.isSuccessful()) showMessage("Đã gửi hướng dẫn đặt lại mật khẩu đến email.");
            else showError(task.getException(), "Không gửi được email đặt lại mật khẩu.");
        });
    }

    private void setBusy(boolean busy) {
        findViewById(R.id.buttonRegister).setEnabled(!busy);
        findViewById(R.id.buttonLogin).setEnabled(!busy);
        findViewById(R.id.buttonResetPassword).setEnabled(!busy);
        textAuthStatus.setText(busy ? "Đang xử lý..." : "");
    }

    private void showError(Exception exception, String fallback) {
        String message = exception != null && exception.getMessage() != null
                ? exception.getMessage() : fallback;
        showMessage(message);
    }

    private void showMessage(String message) {
        textAuthStatus.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void openMenu() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userDao != null) userDao.close();
    }
}
