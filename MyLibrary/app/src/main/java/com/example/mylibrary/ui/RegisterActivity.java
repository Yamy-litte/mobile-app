
package com.example.mylibrary.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylibrary.R;
import com.example.mylibrary.service.AuthService;

public class RegisterActivity extends AppCompatActivity {

    private EditText edtFullName;
    private EditText edtRegisterEmail;
    private EditText edtRegisterPassword;
    private EditText edtConfirmPassword;
    private Button btnRegister;
    private TextView txtGoLogin;

    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authService = new AuthService();

        edtFullName = findViewById(R.id.edtFullName);
        edtRegisterEmail = findViewById(R.id.edtRegisterEmail);
        edtRegisterPassword = findViewById(R.id.edtRegisterPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        txtGoLogin = findViewById(R.id.txtGoLogin);

        btnRegister.setOnClickListener(v -> register());

        txtGoLogin.setOnClickListener(v -> {
            Intent intent = new Intent(
                    RegisterActivity.this,
                    LoginActivity.class
            );
            startActivity(intent);
            finish();
        });
    }

    private void register() {
        String fullName = edtFullName.getText().toString().trim();
        String email = edtRegisterEmail.getText().toString().trim();
        String password = edtRegisterPassword.getText().toString();
        String confirmPassword =
                edtConfirmPassword.getText().toString();

        if (TextUtils.isEmpty(fullName)) {
            edtFullName.setError("Vui lòng nhập họ và tên.");
            edtFullName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            edtRegisterEmail.setError("Vui lòng nhập email.");
            edtRegisterEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            edtRegisterPassword.setError(
                    "Mật khẩu phải có ít nhất 6 ký tự."
            );
            edtRegisterPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            edtConfirmPassword.setError(
                    "Mật khẩu xác nhận không khớp."
            );
            edtConfirmPassword.requestFocus();
            return;
        }

        btnRegister.setEnabled(false);

        authService.register(fullName, email, password,
                new AuthService.FirebaseAuthCallback() {
                    @Override
                    public void onSuccess(
                            com.google.firebase.auth.FirebaseUser user) {

                        btnRegister.setEnabled(true);

                        Toast.makeText(
                                RegisterActivity.this,
                                "Đăng ký thành công!",
                                Toast.LENGTH_LONG
                        ).show();

                        Intent intent = new Intent(
                                RegisterActivity.this,
                                LoginActivity.class
                        );
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        btnRegister.setEnabled(true);

                        Toast.makeText(
                                RegisterActivity.this,
                                errorMessage,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }
}
