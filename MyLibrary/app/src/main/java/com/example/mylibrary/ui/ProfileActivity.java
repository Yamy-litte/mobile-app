package com.example.mylibrary.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylibrary.R;
import com.example.mylibrary.service.AuthService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private TextView txtProfileBack;
    private TextView tvUserDisplayName;
    private TextView tvUserEmail;
    private TextView tvLibraryCardNumber;

    private Button btnViewLibraryCard;
    private Button btnViewBorrowHistory;
    private Button btnViewReservations;
    private Button btnLogoutProfile;

    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        authService = new AuthService();

        txtProfileBack = findViewById(R.id.txtProfileBack);
        tvUserDisplayName = findViewById(R.id.tvUserDisplayName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        tvLibraryCardNumber = findViewById(R.id.tvLibraryCardNumber);

        btnViewLibraryCard = findViewById(R.id.btnViewLibraryCard);
        btnViewBorrowHistory = findViewById(R.id.btnViewBorrowHistory);
        btnViewReservations = findViewById(R.id.btnViewReservations);
        btnLogoutProfile = findViewById(R.id.btnLogoutProfile);

        txtProfileBack.setOnClickListener(v -> finish());

        btnViewLibraryCard.setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, LibraryCardActivity.class));
        });

        btnViewBorrowHistory.setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, BorrowHistoryActivity.class));
        });

        btnViewReservations.setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, ReservationsActivity.class));
        });

        btnLogoutProfile.setOnClickListener(v -> {
            authService.logout();
            Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        loadUserProfile();
    }

    private void loadUserProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            tvUserEmail.setText(user.getEmail());
            FirebaseFirestore.getInstance().collection("customers").document(user.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String name = doc.getString("full_name");
                            if (name != null && !name.isEmpty()) {
                                tvUserDisplayName.setText(name);
                            }
                        }
                    });

            FirebaseFirestore.getInstance().collection("library_cards")
                    .whereEqualTo("customerId", user.getUid())
                    .get()
                    .addOnSuccessListener(query -> {
                        if (!query.isEmpty()) {
                            String cardNumber = query.getDocuments().get(0).getString("cardNumber");
                            if (cardNumber != null) {
                                tvLibraryCardNumber.setText("Mã thẻ: " + cardNumber);
                            }
                        } else {
                            tvLibraryCardNumber.setText("Mã thẻ: LH-" + user.getUid().substring(0, 5).toUpperCase() + "-VN");
                        }
                    });
        }
    }
}
