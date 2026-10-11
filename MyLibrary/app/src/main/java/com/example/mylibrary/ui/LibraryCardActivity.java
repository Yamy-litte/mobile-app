package com.example.mylibrary.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylibrary.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LibraryCardActivity extends AppCompatActivity {

    private TextView txtCardBack;
    private TextView txtCardHolderName;
    private TextView txtCardNumber;
    private TextView txtBarcodeCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library_card);

        txtCardBack = findViewById(R.id.txtCardBack);
        txtCardHolderName = findViewById(R.id.txtCardHolderName);
        txtCardNumber = findViewById(R.id.txtCardNumber);
        txtBarcodeCode = findViewById(R.id.txtBarcodeCode);

        txtCardBack.setOnClickListener(v -> finish());

        loadCardData();
    }

    private void loadCardData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("customers").document(user.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String name = doc.getString("full_name");
                            if (name != null) txtCardHolderName.setText(name.toUpperCase());
                        }
                    });

            FirebaseFirestore.getInstance().collection("library_cards")
                    .whereEqualTo("customerId", user.getUid())
                    .get()
                    .addOnSuccessListener(query -> {
                        if (!query.isEmpty()) {
                            String cardNum = query.getDocuments().get(0).getString("cardNumber");
                            if (cardNum != null) {
                                txtCardNumber.setText(cardNum);
                                txtBarcodeCode.setText("*" + cardNum.replace("-", "") + "*");
                            }
                        } else {
                            String fallback = "LH-" + user.getUid().substring(0, 5).toUpperCase() + "-VN";
                            txtCardNumber.setText(fallback);
                            txtBarcodeCode.setText("*" + fallback.replace("-", "") + "*");
                        }
                    });
        }
    }
}
