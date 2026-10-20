package com.example.libraryonline.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
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
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private TextView registerLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        emailInput = findViewById(R.id.editEmail);
        passwordInput = findViewById(R.id.editPassword);
        loginButton = findViewById(R.id.buttonLogin);
        registerLink = findViewById(R.id.textRegister);

        loginButton.setOnClickListener(v -> login());
        registerLink.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void login() {
        String email = emailInput.getText().toString().trim();
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
