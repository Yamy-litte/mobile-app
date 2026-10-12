package com.example.mylibrary.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylibrary.R;
import com.example.mylibrary.dao.DataCallback;
import com.example.mylibrary.models.Fine;
import com.example.mylibrary.repository.BorrowRecordRepository;
import com.example.mylibrary.repository.FineRepository;
import com.example.mylibrary.util.Constants;

public class ScanReturnActivity extends AppCompatActivity {

    private TextView txtReturnBack;
    private EditText edtReturnCode;
    private RadioGroup rgCondition;
    private Button btnConfirmReturn;

    private BorrowRecordRepository borrowRepository;
    private FineRepository fineRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_return);

        txtReturnBack = findViewById(R.id.txtReturnBack);
        edtReturnCode = findViewById(R.id.edtReturnCode);
        rgCondition = findViewById(R.id.rgCondition);
        btnConfirmReturn = findViewById(R.id.btnConfirmReturn);

        borrowRepository = new BorrowRecordRepository();
        fineRepository = new FineRepository();

        txtReturnBack.setOnClickListener(v -> finish());

        btnConfirmReturn.setOnClickListener(v -> {
            String recordId = edtReturnCode.getText().toString().trim();
            if (recordId.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập mã phiếu mượn", Toast.LENGTH_SHORT).show();
                return;
            }

            int checkedId = rgCondition.getCheckedRadioButtonId();
            String condition = "NORMAL";
            double fineAmt = 0;

            if (checkedId == R.id.rbLightDamage) {
                condition = "DAMAGED";
                fineAmt = Constants.FINE_DAMAGED;
            } else if (checkedId == R.id.rbHeavyDamage) {
                condition = "LOST";
                fineAmt = Constants.FINE_LOST;
            }

            final double finalFineAmt = fineAmt;
            final String finalCondition = condition;

            btnConfirmReturn.setEnabled(false);
            borrowRepository.getBorrowRecordById(recordId, new DataCallback<com.example.mylibrary.models.BorrowRecord>() {
                @Override
                public void onSuccess(com.example.mylibrary.models.BorrowRecord record) {
                    if (record == null || !("BORROWED".equalsIgnoreCase(record.getStatus())
                            || "OVERDUE".equalsIgnoreCase(record.getStatus()))) {
                        btnConfirmReturn.setEnabled(true);
                        Toast.makeText(ScanReturnActivity.this,
                                "Phiếu không tồn tại hoặc sách đã được trả trước đó.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    borrowRepository.returnBorrowRecord(recordId, System.currentTimeMillis(), finalCondition, new DataCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            if (finalFineAmt > 0) {
                                Fine fine = new Fine();
                                fine.setBorrowRecordId(recordId);
                                fine.setFineAmount(finalFineAmt);
                                fine.setFineReason("Tình trạng trả sách: " + finalCondition);
                                fine.setStatus("UNPAID");
                                fine.setCreatedAt(System.currentTimeMillis());

                                fineRepository.addFine(fine, new DataCallback<Fine>() {
                                    @Override
                                    public void onSuccess(Fine f) {
                                        Toast.makeText(ScanReturnActivity.this, "Trả sách thành công! Đã phát sinh phí phạt: " + finalFineAmt + "đ", Toast.LENGTH_LONG).show();
                                        finish();
                                    }

                                    @Override
                                    public void onFailure(String errorMessage) {
                                        btnConfirmReturn.setEnabled(true);
                                        Toast.makeText(ScanReturnActivity.this,
                                                "Sách đã được trả nhưng chưa lưu được khoản phạt: " + errorMessage,
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                            } else {
                                Toast.makeText(ScanReturnActivity.this, "Trả sách thành công!", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            btnConfirmReturn.setEnabled(true);
                            Toast.makeText(ScanReturnActivity.this, "Lỗi trả sách: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onFailure(String errorMessage) {
                    btnConfirmReturn.setEnabled(true);
                    Toast.makeText(ScanReturnActivity.this,
                            "Không tìm thấy phiếu mượn: " + errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
