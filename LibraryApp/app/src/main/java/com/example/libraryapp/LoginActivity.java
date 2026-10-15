package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.AdminDao;
import com.example.libraryapp.dao.CustomerDao;

public class LoginActivity extends AppCompatActivity {
    private EditText emailInput, passwordInput;
    private String role = "reader";
    private FirebaseAuthManager auth;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        role = getIntent().getStringExtra("account_role");
        if (role == null) role = "reader";
        auth = new FirebaseAuthManager();
        emailInput = findViewById(R.id.editEmail);
        passwordInput = findViewById(R.id.editPassword);
        findViewById(R.id.btnLogin).setOnClickListener(v -> doLogin());
        findViewById(R.id.btnForgot).setOnClickListener(v -> sendResetEmail());
        findViewById(R.id.btnRegister).setOnClickListener(v -> {
            if ("admin".equals(role)) {
                Toast.makeText(this, "Tài khoản Admin được cấp bởi quản trị hệ thống.", Toast.LENGTH_LONG).show();
            } else {
                startActivity(new Intent(this, RegisterActivity.class));
            }
        });
    }

    private void doLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập email và mật khẩu.", Toast.LENGTH_SHORT).show();
            return;
        }
        auth.login(email, password).addOnSuccessListener(result -> {
            String uid = result.getUser().getUid();
            if ("admin".equals(role)) {
                AdminDao dao = new AdminDao(this);
                boolean allowed = dao.getByFirebaseUid(uid) != null;
                dao.close();
                if (!allowed) {
                    auth.logout();
                    Toast.makeText(this, "Tài khoản này chưa được cấp quyền Admin.", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, "Đăng nhập Admin thành công.", Toast.LENGTH_SHORT).show();
            } else {
                CustomerDao dao = new CustomerDao(this);
                boolean profileExists = dao.getByFirebaseUid(uid) != null;
                dao.close();
                if (!profileExists) {
                    auth.logout();
                    Toast.makeText(this, "Không tìm thấy hồ sơ độc giả. Vui lòng đăng ký tài khoản.", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, "Đăng nhập thành công.", Toast.LENGTH_SHORT).show();
            }
            Intent intent = new Intent(this, "admin".equals(role) ? AdminHomeActivity.class : ReaderHomeActivity.class);
            intent.putExtra("account_role", role);
            startActivity(intent);
            finish();
        }).addOnFailureListener(e ->
            Toast.makeText(this, friendly(e.getMessage()), Toast.LENGTH_LONG).show());
    }

    private void sendResetEmail() {
        String email = emailInput.getText().toString().trim();
        if (email.isEmpty()) {
            emailInput.setError("Nhập email để đặt lại mật khẩu");
            return;
        }
        auth.sendPasswordResetEmail(email).addOnSuccessListener(v ->
            Toast.makeText(this, "Đã gửi email đặt lại mật khẩu.", Toast.LENGTH_LONG).show()
        ).addOnFailureListener(e ->
            Toast.makeText(this, friendly(e.getMessage()), Toast.LENGTH_LONG).show());
    }

    private String friendly(String message) {
        if (message == null) return "Không thể xác thực. Vui lòng thử lại.";
        if (message.contains("password")) return "Email hoặc mật khẩu không chính xác.";
        if (message.contains("network")) return "Không có kết nối mạng. Vui lòng thử lại.";
        return message;
    }
}
