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
import com.example.libraryonline.utils.InputValidator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class AdminLoginActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private TextView backLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        emailInput = findViewById(R.id.editAdminEmail);
        passwordInput = findViewById(R.id.editAdminPassword);
        loginButton = findViewById(R.id.buttonAdminLogin);
        backLink = findViewById(R.id.textBackToReaderLogin);

        loginButton.setOnClickListener(v -> loginAsAdmin());
        backLink.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void loginAsAdmin() {
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

        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful() || auth.getCurrentUser() == null) {
                        setLoading(false);
                        showMessage("Email hoặc mật khẩu không chính xác.");
                        return;
                    }

                    String uid = auth.getCurrentUser().getUid();
                    firestore.collection("Admins").document(uid).get()
                            .addOnSuccessListener(adminDoc -> {
                                String status = adminDoc.getString("adminStatus");
                                String profileEmail = adminDoc.getString("email");
                                boolean active = "Active".equalsIgnoreCase(status)
                                        || "HoatDong".equalsIgnoreCase(status);
                                boolean emailMatches = profileEmail == null
                                        || profileEmail.trim().equalsIgnoreCase(email);

                                if (!adminDoc.exists() || !active || !emailMatches) {
                                    auth.signOut();
                                    setLoading(false);
                                    showMessage("Tài khoản này chưa được cấp quyền quản trị. "
                                            + "Kiểm tra UID và hồ sơ Admin trong Firestore.");
                                    return;
                                }

                                startActivity(new Intent(this, AdminMainActivity.class));
                                finish();
                            })
                            .addOnFailureListener(error -> {
                                auth.signOut();
                                setLoading(false);
                                showMessage("Không thể xác minh quyền quản trị. Kiểm tra Firestore Rules và kết nối.");
                            });
                });
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        loginButton.setText(loading ? "Đang xác minh..." : "Đăng nhập Admin");
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
