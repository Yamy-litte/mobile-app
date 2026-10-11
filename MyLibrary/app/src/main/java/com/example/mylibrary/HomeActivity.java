package com.example.mylibrary;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Button btnSearchBooks = findViewById(R.id.btnSearchBooks);
        Button btnMyProfile = findViewById(R.id.btnMyProfile);
        Button btnBorrowedBooks = findViewById(R.id.btnBorrowedBooks);
        Button btnReservations = findViewById(R.id.btnReservations);
        Button btnMyFines = findViewById(R.id.btnMyFines);
        Button btnLogout = findViewById(R.id.btnLogout);

        btnSearchBooks.setOnClickListener(v -> Toast.makeText(this, "Search books feature", Toast.LENGTH_SHORT).show());
        btnMyProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        btnBorrowedBooks.setOnClickListener(v -> Toast.makeText(this, "Borrowed books list", Toast.LENGTH_SHORT).show());
        btnReservations.setOnClickListener(v -> Toast.makeText(this, "Reservations list", Toast.LENGTH_SHORT).show());
        btnMyFines.setOnClickListener(v -> Toast.makeText(this, "Fines list", Toast.LENGTH_SHORT).show());
        btnLogout.setOnClickListener(v -> {
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
