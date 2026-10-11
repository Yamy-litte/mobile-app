
package com.example.mylibrary.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mylibrary.R;
import com.example.mylibrary.service.AuthService;

import android.view.View;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText edtEmail;
    private EditText edtPassword;
    private Button btnLogin;
    private TextView txtGoRegister;

    private AuthService authService;

    private String selectedRole = "Customer";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        selectedRole = getIntent().getStringExtra("selected_role");

        if (!"Admin".equals(selectedRole)) {
            selectedRole = "Customer";
        }

        authService = new AuthService();

        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        txtGoRegister = findViewById(R.id.txtGoRegister);

        if ("Admin".equals(selectedRole)) {
            txtGoRegister.setVisibility(View.GONE);
        }
        btnLogin.setOnClickListener(v -> login());

        txtGoRegister.setOnClickListener(v -> {

            Intent intent = new Intent(
                    LoginActivity.this,
                    RegisterActivity.class
            );
            startActivity(intent);
        });
    }

    private void login() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString();

        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("Vui lòng nhập email.");
            edtEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            edtPassword.setError("Vui lòng nhập mật khẩu.");
            edtPassword.requestFocus();
            return;
        }

        btnLogin.setEnabled(false);

        authService.login(email, password,
                new AuthService.FirebaseAuthCallback() {

                    @Override
                    public void onSuccess(FirebaseUser user) {
                        if (user == null) {
                            btnLogin.setEnabled(true);
                            Toast.makeText(
                                    LoginActivity.this,
                                    "Không lấy được thông tin tài khoản.",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        String collectionName = "Admin".equals(selectedRole)
                                ? "admins"
                                : "customers";

                        db.collection(collectionName)
                                .document(user.getUid())
                                .get()
                                .addOnSuccessListener(document -> {
                                    if (!document.exists()) {
                                        authService.logout();

                                        btnLogin.setEnabled(true);
                                        Toast.makeText(
                                                LoginActivity.this,
                                                "Tài khoản không thuộc vai trò " + selectedRole + " đã chọn.",
                                                Toast.LENGTH_SHORT
                                        ).show();

                                        return;
                                    }

                                    if ("Customer".equals(selectedRole)) {
                                        String customerName = document.getString("fullName");
                                        if (customerName == null) customerName = document.getString("full_name");

                                        Intent intent = new Intent(
                                                LoginActivity.this,
                                                HomeActivity.class
                                        );

                                        intent.putExtra("customer_name", customerName);

                                        startActivity(intent);
                                        finish();

                                    } else {
                                        Toast.makeText(
                                                LoginActivity.this,
                                                "Đăng nhập Admin thành công!",
                                                Toast.LENGTH_SHORT
                                        ).show();
                                    }
                                    btnLogin.setEnabled(true);

                                })
                                .addOnFailureListener(e -> {
                                    authService.logout();
                                    btnLogin.setEnabled(true);

                                    Toast.makeText(
                                            LoginActivity.this,
                                            "Không thể xác minh vai trò. Vui lòng thử lại.",
                                            Toast.LENGTH_LONG
                                    ).show();
                                });
                    }


                    @Override
                    public void onFailure(String errorMessage) {
                        btnLogin.setEnabled(true);

                        Toast.makeText(
                                LoginActivity.this,
                                errorMessage,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }
}
