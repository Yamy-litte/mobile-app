package com.example.libraryonline.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;
import com.example.libraryonline.ui.admin.AdminMainActivity;
import com.example.libraryonline.ui.reader.ReaderMainActivity;
import com.example.libraryonline.utils.InputValidator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "mylibrary_login";
    private static final String KEY_REMEMBER_EMAIL = "remember_email";

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private CheckBox rememberLogin;
    private TextView registerLink;
    private TextView forgotPasswordLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        emailInput = findViewById(R.id.editEmail);
        passwordInput = findViewById(R.id.editPassword);
        loginButton = findViewById(R.id.buttonLogin);
        rememberLogin = findViewById(R.id.checkRememberLogin);
        registerLink = findViewById(R.id.textRegister);
        forgotPasswordLink = findViewById(R.id.textForgotPassword);

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedEmail = preferences.getString(KEY_REMEMBER_EMAIL, "");
        if (!savedEmail.isEmpty()) {
            emailInput.setText(savedEmail);
            rememberLogin.setChecked(true);
        }

        loginButton.setOnClickListener(v -> login());
        registerLink.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        forgotPasswordLink.setOnClickListener(v -> sendPasswordReset());
    }

    private void login() {
        String email = InputValidator.normalizeEmail(emailInput.getText().toString());
        String password = passwordInput.getText().toString();

        if (!InputValidator.isValidEmail(email)) {
            emailInput.setError("Email không đúng định dạng");
            emailInput.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            passwordInput.setError("Vui lòng nhập mật khẩu");
            passwordInput.requestFocus();
            return;
        }

        SharedPreferences.Editor preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        if (rememberLogin.isChecked()) {
            preferences.putString(KEY_REMEMBER_EMAIL, email);
        } else {
            preferences.remove(KEY_REMEMBER_EMAIL);
        }
        preferences.apply();

        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful() || auth.getCurrentUser() == null) {
                        setLoading(false);
                        showMessage("Email hoặc mật khẩu không chính xác.");
                        return;
                    }

                    String uid = auth.getCurrentUser().getUid();
                    firestore.collection("Users").document(uid).get()
                            .addOnSuccessListener(userDoc -> {
                                if (!userDoc.exists()) {
                                    verifyAdmin(uid);
                                    return;
                                }

                                String role = userDoc.getString("role");
                                String cardStatus = userDoc.getString("cardStatus");
                                if (!"HoatDong".equals(cardStatus)) {
                                    rejectLogin("Thẻ độc giả chưa hoạt động, đã khóa hoặc hết hạn.");
                                    return;
                                }
                                if (!"Reader".equalsIgnoreCase(role)) {
                                    rejectLogin("Vai trò tài khoản không hợp lệ.");
                                    return;
                                }

                                setLoading(false);
                                startActivity(new Intent(this, ReaderMainActivity.class));
                                finish();
                            })
                            .addOnFailureListener(error -> {
                                setLoading(false);
                                showMessage("Không thể tải hồ sơ. Kiểm tra kết nối và quyền Firestore.");
                            });
                });
    }

    private void verifyAdmin(String uid) {
        firestore.collection("Admins").document(uid).get()
                .addOnSuccessListener(adminDoc -> {
                    String status = adminDoc.getString("adminStatus");
                    if (!adminDoc.exists()
                            || !("Active".equalsIgnoreCase(status)
                            || "HoatDong".equalsIgnoreCase(status))) {
                        rejectLogin("Tài khoản chưa có hồ sơ hoặc chưa được cấp quyền.");
                        return;
                    }
                    setLoading(false);
                    startActivity(new Intent(this, AdminMainActivity.class));
                    finish();
                })
                .addOnFailureListener(error -> {
                    setLoading(false);
                    showMessage("Không thể xác minh quyền quản trị viên.");
                });
    }

    private void sendPasswordReset() {
        String email = InputValidator.normalizeEmail(emailInput.getText().toString());
        if (!InputValidator.isValidEmail(email)) {
            emailInput.setError("Nhập email hợp lệ để nhận liên kết đặt lại mật khẩu");
            emailInput.requestFocus();
            return;
        }

        forgotPasswordLink.setEnabled(false);
        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    forgotPasswordLink.setEnabled(true);
                    if (task.isSuccessful()) {
                        showMessage("Nếu email thuộc tài khoản đã đăng ký, hướng dẫn đặt lại mật khẩu sẽ được gửi đến hộp thư.");
                    } else {
                        showMessage("Chưa gửi được email đặt lại mật khẩu. Vui lòng thử lại sau.");
                    }
                });
    }

    private void rejectLogin(String message) {
        auth.signOut();
        setLoading(false);
        showMessage(message);
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        loginButton.setText(loading ? "Đang đăng nhập..." : "Đăng nhập");
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
