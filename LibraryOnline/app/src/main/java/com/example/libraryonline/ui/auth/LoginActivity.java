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
import com.example.libraryonline.utils.PasswordVisibilityToggle;
import com.example.libraryonline.ui.admin.AdminMainActivity;
import com.example.libraryonline.ui.reader.ReaderMainActivity;
import com.example.libraryonline.ui.auth.ChangePasswordActivity;
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
    private TextView adminLoginLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        emailInput = findViewById(R.id.editEmail);
        passwordInput = findViewById(R.id.editPassword);
        PasswordVisibilityToggle.attach(passwordInput);
        loginButton = findViewById(R.id.buttonLogin);
        rememberLogin = findViewById(R.id.checkRememberLogin);
        registerLink = findViewById(R.id.textRegister);
        forgotPasswordLink = findViewById(R.id.textForgotPassword);
        adminLoginLink = findViewById(R.id.textAdminLogin);

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedEmail = preferences.getString(KEY_REMEMBER_EMAIL, "");
        if (!savedEmail.isEmpty()) {
            emailInput.setText(savedEmail);
            rememberLogin.setChecked(true);
        }

        loginButton.setOnClickListener(v -> login());
        registerLink.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        forgotPasswordLink.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));
        adminLoginLink.setOnClickListener(v ->
                startActivity(new Intent(this, AdminLoginActivity.class)));
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
                    // Không cho tài khoản có hồ sơ Admin đi vào luồng đăng nhập độc giả.
                    firestore.collection("Admins").document(uid).get()
                            .addOnSuccessListener(adminDoc -> {
                                if (adminDoc.exists()) {
                                    rejectLogin("Đây là tài khoản quản trị viên. Vui lòng đăng nhập tại trang Admin.");
                                    return;
                                }

                                firestore.collection("Users").document(uid).get()
                                        .addOnSuccessListener(userDoc -> {
                                            if (!userDoc.exists()) {
                                                rejectLogin("Tài khoản không có hồ sơ độc giả. Vui lòng dùng đúng trang đăng nhập.");
                                                return;
                                            }

                                            String role = userDoc.getString("role");
                                            String cardStatus = userDoc.getString("cardStatus");
                                            if (!"Reader".equalsIgnoreCase(role)) {
                                                rejectLogin("Tài khoản này không phải độc giả. Vui lòng đăng nhập đúng trang.");
                                                return;
                                            }
                                            if (!"HoatDong".equals(cardStatus)) {
                                                rejectLogin("Thẻ độc giả chưa hoạt động, đã khóa hoặc hết hạn.");
                                                return;
                                            }
                                            if (Boolean.TRUE.equals(userDoc.getBoolean("mustChangePassword"))) {
                                                setLoading(false);
                                                startActivity(new Intent(this, ChangePasswordActivity.class));
                                                finish();
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
                            })
                            .addOnFailureListener(error -> {
                                setLoading(false);
                                showMessage("Không thể xác minh loại tài khoản. Kiểm tra Firestore Rules và kết nối.");
                            });
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
