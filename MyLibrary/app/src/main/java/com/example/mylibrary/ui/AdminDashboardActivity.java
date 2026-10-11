package com.example.mylibrary.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylibrary.R;
import com.example.mylibrary.service.AuthService;
import com.google.firebase.firestore.FirebaseFirestore;

public class AdminDashboardActivity extends AppCompatActivity {

    private TextView txtTotalBooks;
    private TextView txtTotalCustomers;

    private Button btnManageBooks;
    private Button btnScanBorrow;
    private Button btnScanReturn;
    private Button btnManageReservations;
    private Button btnManageCustomers;
    private Button btnManageStaff;
    private Button btnLogoutAdmin;

    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        authService = new AuthService();

        txtTotalBooks = findViewById(R.id.txtTotalBooks);
        txtTotalCustomers = findViewById(R.id.txtTotalCustomers);

        btnManageBooks = findViewById(R.id.btnManageBooks);
        btnScanBorrow = findViewById(R.id.btnScanBorrow);
        btnScanReturn = findViewById(R.id.btnScanReturn);
        btnManageReservations = findViewById(R.id.btnManageReservations);
        btnManageCustomers = findViewById(R.id.btnManageCustomers);
        btnManageStaff = findViewById(R.id.btnManageStaff);
        btnLogoutAdmin = findViewById(R.id.btnLogoutAdmin);

        btnManageBooks.setOnClickListener(v -> startActivity(new Intent(this, ManageBooksActivity.class)));
        btnScanBorrow.setOnClickListener(v -> startActivity(new Intent(this, ScanBorrowActivity.class)));
        btnScanReturn.setOnClickListener(v -> startActivity(new Intent(this, ScanReturnActivity.class)));
        btnManageReservations.setOnClickListener(v -> startActivity(new Intent(this, ManageReservationsActivity.class)));
        btnManageCustomers.setOnClickListener(v -> Toast.makeText(this, "Chức năng quản lý độc giả đang được hoàn thiện.", Toast.LENGTH_SHORT).show());
        btnManageStaff.setOnClickListener(v -> Toast.makeText(this, "Chức năng quản lý nhân viên đang được hoàn thiện.", Toast.LENGTH_SHORT).show());

        btnLogoutAdmin.setOnClickListener(v -> {
            authService.logout();
            Toast.makeText(this, "Đã đăng xuất Admin", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        loadDashboardStats();
    }

    private void loadDashboardStats() {
        FirebaseFirestore.getInstance().collection("books").get()
                .addOnSuccessListener(query -> txtTotalBooks.setText(String.valueOf(query.size())));

        FirebaseFirestore.getInstance().collection("customers").get()
                .addOnSuccessListener(query -> txtTotalCustomers.setText(String.valueOf(query.size())));
    }
}
