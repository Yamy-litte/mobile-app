package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.UserDAO;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.User;

public class MainActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private EditText emailInput, passwordInput;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            openDashboard(auth.getCurrentUser());
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(28, 24, 28, 24);
        root.setBackgroundColor(0xFFFAF7F2);

        TextView title = new TextView(this);
        title.setText("Café Booking Online");
        title.setTextSize(26);
        title.setTextColor(0xFF744B30);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, params());

        TextView subtitle = new TextView(this);
        subtitle.setText("Đăng nhập tài khoản");
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = params();
        sp.topMargin = 16;
        root.addView(subtitle, sp);

        emailInput = field(root, "Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        passwordInput = field(root, "Mật khẩu", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        loginButton = new Button(this);
        loginButton.setText("Đăng nhập");
        LinearLayout.LayoutParams lp = params();
        lp.topMargin = 12;
        root.addView(loginButton, lp);
        loginButton.setOnClickListener(v -> login());

        Button register = new Button(this);
        register.setText("Tạo tài khoản mới");
        root.addView(register, params());
        register.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));

        Button reset = new Button(this);
        reset.setText("Quên mật khẩu");
        root.addView(reset, params());
        reset.setOnClickListener(v -> sendPasswordReset());

        setContentView(root);
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

    private EditText field(LinearLayout root, String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(inputType);
        editText.setSingleLine(true);
        LinearLayout.LayoutParams p = params();
        p.topMargin = 8;
        root.addView(editText, p);
        return editText;
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
