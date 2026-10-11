package com.example.mylibrary;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        EditText etFullName = findViewById(R.id.etFullName);
        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        EditText etPhone = findViewById(R.id.etPhone);
        Button btnRegister = findViewById(R.id.btnRegister);
        Button btnBackToLogin = findViewById(R.id.btnBackToLogin);

        btnRegister.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter required information", Toast.LENGTH_SHORT).show();
                return;
            }

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        String uid = result.getUser() != null ? result.getUser().getUid() : "";
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("id", uid);
                        userMap.put("email", email);
                        userMap.put("full_name", fullName);
                        userMap.put("phone_number", phone);
                        userMap.put("status", "active");
                        userMap.put("avatar_url", "");
                        userMap.put("created_at", System.currentTimeMillis());
                        userMap.put("updated_at", System.currentTimeMillis());

                        db.collection("customers").document(uid)
                                .set(userMap)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(RegisterActivity.this, HomeActivity.class));
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save profile", Toast.LENGTH_SHORT).show());
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Registration failed", Toast.LENGTH_SHORT).show());
        });

        btnBackToLogin.setOnClickListener(v -> finish());
    }
}
