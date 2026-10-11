package com.example.mylibrary;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AdminDashboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        Button btnManageBooks = findViewById(R.id.btnManageBooks);
        Button btnManageBorrowRecords = findViewById(R.id.btnManageBorrowRecords);
        Button btnManageReservations = findViewById(R.id.btnManageReservations);
        Button btnManageExtensions = findViewById(R.id.btnManageExtensions);
        Button btnManageFines = findViewById(R.id.btnManageFines);
        Button btnReports = findViewById(R.id.btnReports);
        Button btnLogoutAdmin = findViewById(R.id.btnLogoutAdmin);

        btnManageBooks.setOnClickListener(v -> Toast.makeText(this, "Manage Books", Toast.LENGTH_SHORT).show());
        btnManageBorrowRecords.setOnClickListener(v -> Toast.makeText(this, "Manage Borrow Records", Toast.LENGTH_SHORT).show());
        btnManageReservations.setOnClickListener(v -> Toast.makeText(this, "Manage Reservations", Toast.LENGTH_SHORT).show());
        btnManageExtensions.setOnClickListener(v -> Toast.makeText(this, "Manage Extensions", Toast.LENGTH_SHORT).show());
        btnManageFines.setOnClickListener(v -> Toast.makeText(this, "Manage Fines", Toast.LENGTH_SHORT).show());
        btnReports.setOnClickListener(v -> Toast.makeText(this, "View Reports", Toast.LENGTH_SHORT).show());
        btnLogoutAdmin.setOnClickListener(v -> {
            Toast.makeText(this, "Admin logged out", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}
