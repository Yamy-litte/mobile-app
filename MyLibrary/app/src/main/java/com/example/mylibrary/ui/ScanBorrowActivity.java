package com.example.mylibrary.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylibrary.R;
import com.example.mylibrary.dao.DataCallback;
import com.example.mylibrary.models.BorrowRecord;
import com.example.mylibrary.repository.BorrowRecordRepository;
import com.google.firebase.auth.FirebaseAuth;

public class ScanBorrowActivity extends AppCompatActivity {

    private TextView txtScanBorrowBack;
    private EditText edtBarcodeManual;
    private Button btnConfirmScan;
    private BorrowRecordRepository borrowRecordRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_borrow);

        txtScanBorrowBack = findViewById(R.id.txtScanBorrowBack);
        edtBarcodeManual = findViewById(R.id.edtBarcodeManual);
        btnConfirmScan = findViewById(R.id.btnConfirmScan);
        borrowRecordRepository = new BorrowRecordRepository();

        txtScanBorrowBack.setOnClickListener(v -> finish());

        btnConfirmScan.setOnClickListener(v -> {
            String code = edtBarcodeManual.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập mã vạch hoặc mã thẻ", Toast.LENGTH_SHORT).show();
                return;
            }

            BorrowRecord rec = new BorrowRecord();
            rec.setCustomerId("CUST-" + (int)(Math.random() * 9000 + 1000));
            rec.setBookCopyId("COPY-" + code);
            rec.setBookTitle("Sách mượn qua QR (" + code + ")");
            rec.setBorrowDate(System.currentTimeMillis());
            rec.setDueDate(System.currentTimeMillis() + (14 * 24 * 60 * 60 * 1000L));
            rec.setAdminBorrowId(FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "ADMIN");
            rec.setStatus("BORROWED");

            borrowRecordRepository.addBorrowRecord(rec, new DataCallback<BorrowRecord>() {
                @Override
                public void onSuccess(BorrowRecord result) {
                    Toast.makeText(ScanBorrowActivity.this, "Xác nhận mượn sách thành công!", Toast.LENGTH_LONG).show();
                    finish();
                }

                @Override
                public void onFailure(String errorMessage) {
                    Toast.makeText(ScanBorrowActivity.this, "Lỗi tạo phiếu mượn: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
