package com.example.mylibrary.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylibrary.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Staff workflow for recording a loan.
 * A barcode alone is not enough to create a valid loan: the customer and actual
 * book-copy documents must be resolved first. This screen therefore refuses to
 * write placeholder IDs into Firestore until that lookup flow is implemented.
 */
public class ScanBorrowActivity extends AppCompatActivity {

    private TextView txtScanBorrowBack;
    private EditText edtBarcodeManual;
    private Button btnConfirmScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_borrow);

        txtScanBorrowBack = findViewById(R.id.txtScanBorrowBack);
        edtBarcodeManual = findViewById(R.id.edtBarcodeManual);
        btnConfirmScan = findViewById(R.id.btnConfirmScan);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Vui lòng đăng nhập tài khoản nhân viên.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra("selected_role", "Admin");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        txtScanBorrowBack.setOnClickListener(v -> finish());
        btnConfirmScan.setOnClickListener(v -> {
            String code = edtBarcodeManual.getText().toString().trim();
            if (code.isEmpty()) {
                edtBarcodeManual.setError("Nhập mã vạch sách hoặc mã bản sao");
                edtBarcodeManual.requestFocus();
                return;
            }

            // Do not create fake customer/copy IDs. Doing so would corrupt loan history.
            Toast.makeText(this,
                    "Chưa thể ghi nhận phiếu mượn: cần tra cứu mã độc giả và bản sao sách từ Firestore trước. Không có dữ liệu giả nào được lưu.",
                    Toast.LENGTH_LONG).show();
        });
    }
}
