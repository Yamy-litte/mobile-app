package com.example.mylibrary;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class ProfileActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        TextView tvUserDisplayName = findViewById(R.id.tvUserDisplayName);
        TextView tvUserEmail = findViewById(R.id.tvUserEmail);
        TextView tvLibraryCardNumber = findViewById(R.id.tvLibraryCardNumber);
        Button btnLogoutProfile = findViewById(R.id.btnLogoutProfile);

        String email = FirebaseAuth.getInstance().getCurrentUser() != null ? 
            FirebaseAuth.getInstance().getCurrentUser().getEmail() : "user@example.com";
        
        tvUserDisplayName.setText("User Profile");
        tvUserEmail.setText("Email: " + email);
        tvLibraryCardNumber.setText("Library Card: LC-001");

        btnLogoutProfile.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(ProfileActivity.this, MainActivity.class));
            finish();
        });
    }
}
