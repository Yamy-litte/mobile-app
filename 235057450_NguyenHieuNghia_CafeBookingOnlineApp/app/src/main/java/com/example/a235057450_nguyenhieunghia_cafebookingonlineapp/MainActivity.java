package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.UserDAO;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.User;

public class MainActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private EditText emailInput, passwordInput;
    private TextView resetButton, registerButton;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            openDashboard(auth.getCurrentUser());
            return;
        }

        setContentView(R.layout.activity_login);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registerButton);
        resetButton = findViewById(R.id.resetButton);
        loginButton.setOnClickListener(v -> login());
        registerButton.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        resetButton.setOnClickListener(v -> sendPasswordReset());
    }

    private void login() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Nhập email hợp lệ");
            return;
        }
        if (password.isEmpty()) {
            passwordInput.setError("Nhập mật khẩu");
            return;
        }
        loginButton.setEnabled(false);
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            loginButton.setEnabled(true);
            if (task.isSuccessful() && task.getResult().getUser() != null) {
                openDashboard(task.getResult().getUser());
            } else {
                Toast.makeText(this, "Đăng nhập thất bại. Kiểm tra email và mật khẩu.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void sendPasswordReset() {
        String email = emailInput.getText().toString().trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Nhập email để nhận liên kết đặt lại mật khẩu");
            return;
        }
        auth.sendPasswordResetEmail(email).addOnCompleteListener(task ->
                Toast.makeText(this, task.isSuccessful()
                        ? "Đã gửi hướng dẫn đặt lại mật khẩu nếu email tồn tại."
                        : "Không thể gửi email đặt lại mật khẩu.", Toast.LENGTH_LONG).show());
    }

    private void openDashboard(FirebaseUser firebaseUser) {
        UserDAO dao = new UserDAO(getApplicationContext());
        User profile = dao.getByEmail(firebaseUser.getEmail());
        if (profile == null) {
            profile = new User();
            profile.setFullName(firebaseUser.getDisplayName() == null ? "Customer" : firebaseUser.getDisplayName());
            profile.setEmail(firebaseUser.getEmail());
            profile.setRole(User.ROLE_CUSTOMER);
            long id = dao.insert(profile);
            if (id > 0) profile.setUserId(id);
        }
        String role = profile.getRole();
        dao.close();

        Intent intent = new Intent(this, DashboardActivity.class);
        intent.putExtra("user_email", firebaseUser.getEmail());
        intent.putExtra("user_role", role);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

}
