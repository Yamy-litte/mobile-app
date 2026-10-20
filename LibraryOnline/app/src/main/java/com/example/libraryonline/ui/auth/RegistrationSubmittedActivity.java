package com.example.libraryonline.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;

public class RegistrationSubmittedActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration_submitted);

        TextView fullName = findViewById(R.id.textSubmittedFullName);
        TextView birthDate = findViewById(R.id.textSubmittedBirthDate);
        TextView phone = findViewById(R.id.textSubmittedPhone);
        TextView email = findViewById(R.id.textSubmittedEmail);

        fullName.setText(getIntent().getStringExtra("fullName"));
        birthDate.setText(getIntent().getStringExtra("birthDateDisplay"));
        phone.setText(getIntent().getStringExtra("phone"));
        email.setText(getIntent().getStringExtra("email"));

        Button backToLogin = findViewById(R.id.buttonBackToLogin);
        backToLogin.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
