package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RegisterActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private EditText editEmail;
    private EditText editPassword;
    private EditText editConfirmPassword;
    private TextView textRegisterStatus;
    private android.view.View buttonCreateAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        editEmail = findViewById(R.id.editRegisterEmail);
        editPassword = findViewById(R.id.editRegisterPassword);
        editConfirmPassword = findViewById(R.id.editRegisterConfirmPassword);
        textRegisterStatus = findViewById(R.id.textRegisterStatus);
        buttonCreateAccount = findViewById(R.id.buttonCreateAccount);

        buttonCreateAccount.setOnClickListener(v -> register());
        findViewById(R.id.buttonBackToLogin).setOnClickListener(v -> finish());
    }

    private void register() {
        String email = editEmail.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        String password = editPassword.getText().toString();
        String confirmPassword = editConfirmPassword.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError("Vui lòng nhập email hợp lệ.");
            editEmail.requestFocus();
            return;
        }
        if (password.length() < 6) {
            editPassword.setError("Mật khẩu phải có ít nhất 6 ký tự.");
            editPassword.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            editConfirmPassword.setError("Mật khẩu xác nhận không khớp.");
            editConfirmPassword.requestFocus();
            return;
        }

        setBusy(true);
        auth.createUserWithEmailAndPassword(email, password)
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
                            auth.signOut();
                            showMessage("Đăng ký thành công! Hãy mở email để xác minh, sau đó quay lại đăng nhập.");
                            editPassword.setText("");
                            editConfirmPassword.setText("");
                        } else {
                            showError(verifyTask.getException(),
                                    "Tài khoản đã tạo nhưng không gửi được email xác minh. Bạn có thể thử gửi lại sau.");
                        }
                    });
                });
    }

    private void setBusy(boolean busy) {
        buttonCreateAccount.setEnabled(!busy);
        findViewById(R.id.buttonBackToLogin).setEnabled(!busy);
        textRegisterStatus.setText(busy ? "Đang tạo tài khoản..." : "");
    }

    private void showError(Exception exception, String fallback) {
        String message = exception != null && exception.getMessage() != null
                ? exception.getMessage() : fallback;
        showMessage(message);
    }

    private void showMessage(String message) {
        textRegisterStatus.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
