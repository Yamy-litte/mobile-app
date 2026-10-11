package com.example.mylibrary.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylibrary.R;
import com.example.mylibrary.dao.DataCallback;
import com.example.mylibrary.models.BorrowRecord;
import com.example.mylibrary.models.Extension;
import com.example.mylibrary.models.Fine;
import com.example.mylibrary.repository.BorrowRecordRepository;
import com.example.mylibrary.repository.ExtensionRepository;
import com.example.mylibrary.repository.FineRepository;
import com.example.mylibrary.util.DateUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class BorrowHistoryActivity extends AppCompatActivity {

    private TextView txtBorrowBack;
    private LinearLayout layoutActiveBorrows;
    private LinearLayout layoutBorrowHistory;
    private LinearLayout layoutFines;

    private BorrowRecordRepository borrowRepository;
    private ExtensionRepository extensionRepository;
    private FineRepository fineRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_borrow_history);

        txtBorrowBack = findViewById(R.id.txtBorrowBack);
        layoutActiveBorrows = findViewById(R.id.layoutActiveBorrows);
        layoutBorrowHistory = findViewById(R.id.layoutBorrowHistory);
        layoutFines = findViewById(R.id.layoutFines);

        borrowRepository = new BorrowRecordRepository();
        extensionRepository = new ExtensionRepository();
        fineRepository = new FineRepository();

        txtBorrowBack.setOnClickListener(v -> finish());

        loadData();
    }

    private void loadData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        borrowRepository.getBorrowRecordsByCustomerId(user.getUid(), new DataCallback<List<BorrowRecord>>() {
            @Override
            public void onSuccess(List<BorrowRecord> result) {
                layoutActiveBorrows.removeAllViews();
                layoutBorrowHistory.removeAllViews();

                boolean hasActive = false;
                boolean hasHistory = false;

                for (BorrowRecord rec : result) {
                    if ("BORROWED".equals(rec.getStatus()) || "OVERDUE".equals(rec.getStatus())) {
                        layoutActiveBorrows.addView(createActiveCard(rec));
                        hasActive = true;
                    } else {
                        layoutBorrowHistory.addView(createHistoryRow(rec));
                        hasHistory = true;
                    }
                }

                if (!hasActive) addEmptyMessage(layoutActiveBorrows, "Bạn không có sách nào đang mượn.");
                if (!hasHistory) addEmptyMessage(layoutBorrowHistory, "Chưa có lịch sử mượn trả.");
            }

            @Override
            public void onFailure(String errorMessage) {
                addEmptyMessage(layoutActiveBorrows, "Không tải được danh sách.");
            }
        });

        fineRepository.getFinesByCustomerId(user.getUid(), new DataCallback<List<Fine>>() {
            @Override
            public void onSuccess(List<Fine> result) {
                layoutFines.removeAllViews();
                if (result.isEmpty()) {
                    addEmptyMessage(layoutFines, "Bạn không có khoản phạt nào.");
                    return;
                }
                for (Fine fine : result) {
                    layoutFines.addView(createFineRow(fine));
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                addEmptyMessage(layoutFines, "Không tải được khoản phạt.");
            }
        });
    }

    private View createActiveCard(BorrowRecord rec) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.rgb(230, 233, 231));
        card.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText(rec.getBookTitle() != null ? rec.getBookTitle() : "Tên sách");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(36, 59, 100));

        TextView dateInfo = new TextView(this);
        dateInfo.setText("Ngày mượn: " + DateUtils.formatDate(rec.getBorrowDate()) + " • Hạn trả: " + DateUtils.formatDate(rec.getDueDate()));
        dateInfo.setTextSize(13);
        dateInfo.setTextColor(Color.rgb(102, 112, 133));

        Button btnExtend = new Button(this);
        btnExtend.setText("Gia hạn mượn");
        btnExtend.setAllCaps(false);
        btnExtend.setBackgroundColor(Color.rgb(36, 59, 100));
        btnExtend.setTextColor(Color.WHITE);
        btnExtend.setOnClickListener(v -> requestExtension(rec));

        card.addView(title);
        card.addView(dateInfo);
        card.addView(btnExtend);
        return card;
    }

    private void requestExtension(BorrowRecord rec) {
        Extension ext = new Extension();
        ext.setBorrowRecordId(rec.getId());
        ext.setRequestDate(System.currentTimeMillis());
        ext.setExtendedDays(7);
        ext.setStatus("PENDING");
        ext.setNotes("Độc giả xin gia hạn 7 ngày");

        extensionRepository.addExtension(ext, new DataCallback<Extension>() {
            @Override
            public void onSuccess(Extension result) {
                Toast.makeText(BorrowHistoryActivity.this, "Đã gửi yêu cầu gia hạn! Đang chờ thủ thư duyệt.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(BorrowHistoryActivity.this, "Gia hạn thất bại: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private View createHistoryRow(BorrowRecord rec) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), Color.rgb(230, 233, 231));
        row.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText(rec.getBookTitle() != null ? rec.getBookTitle() : "Tên sách");
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(36, 59, 100));

        TextView info = new TextView(this);
        info.setText("Trạng thái: " + rec.getStatus() + " • Trả ngày: " + (rec.getReturnDate() > 0 ? DateUtils.formatDate(rec.getReturnDate()) : "N/A"));
        info.setTextSize(13);
        info.setTextColor(Color.rgb(102, 112, 133));

        row.addView(title);
        row.addView(info);
        return row;
    }

    private View createFineRow(Fine fine) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), Color.rgb(230, 233, 231));
        row.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);

        TextView amount = new TextView(this);
        amount.setText("Mức phạt: " + String.format("%,.0fđ", fine.getFineAmount()) + " - " + ("PAID".equals(fine.getStatus()) ? "Đã thanh toán" : "Chưa trả"));
        amount.setTextSize(15);
        amount.setTypeface(null, Typeface.BOLD);
        amount.setTextColor("PAID".equals(fine.getStatus()) ? Color.rgb(46, 125, 50) : Color.rgb(217, 45, 32));

        TextView reason = new TextView(this);
        reason.setText("Lý do: " + (fine.getFineReason() != null ? fine.getFineReason() : "Trễ hạn"));
        reason.setTextSize(13);
        reason.setTextColor(Color.rgb(102, 112, 133));

        row.addView(amount);
        row.addView(reason);

        if ("UNPAID".equals(fine.getStatus())) {
            Button btnPay = new Button(this);
            btnPay.setText("Thanh toán ngay");
            btnPay.setAllCaps(false);
            btnPay.setBackgroundColor(Color.rgb(217, 45, 32));
            btnPay.setTextColor(Color.WHITE);
            btnPay.setOnClickListener(v -> {
                fineRepository.payFine(fine.getId(), new DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Toast.makeText(BorrowHistoryActivity.this, "Thanh toán phí phạt thành công!", Toast.LENGTH_SHORT).show();
                        loadData();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(BorrowHistoryActivity.this, "Lỗi thanh toán: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            });
            row.addView(btnPay);
        }

        return row;
    }

    private void addEmptyMessage(LinearLayout parent, String message) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(14);
        tv.setTextColor(Color.rgb(102, 112, 133));
        tv.setPadding(0, dp(10), 0, dp(10));
        parent.addView(tv);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
