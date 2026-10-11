package com.example.mylibrary.ui;

import android.content.Intent;
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
import com.example.mylibrary.models.Reservation;
import com.example.mylibrary.repository.ReservationRepository;
import com.example.mylibrary.util.DateUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class ManageReservationsActivity extends AppCompatActivity {

    private TextView txtBack;
    private LinearLayout listContainer;
    private ReservationRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_reservations);

        txtBack = findViewById(R.id.txtManageResBack);
        listContainer = findViewById(R.id.layoutManageResList);
        repository = new ReservationRepository();
        txtBack.setOnClickListener(v -> finish());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Vui lòng đăng nhập tài khoản Admin.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra("selected_role", "Admin");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        loadReservations();
    }

    private void loadReservations() {
        listContainer.removeAllViews();
        repository.getActiveReservations(new DataCallback<List<Reservation>>() {
            @Override
            public void onSuccess(List<Reservation> reservations) {
                listContainer.removeAllViews();
                if (reservations == null || reservations.isEmpty()) {
                    addMessage("Không có yêu cầu đặt trước đang chờ xử lý.");
                    return;
                }
                for (Reservation reservation : reservations) {
                    if (reservation != null && "PENDING".equalsIgnoreCase(reservation.getStatus())) {
                        listContainer.addView(createCard(reservation));
                    }
                }
                if (listContainer.getChildCount() == 0) {
                    addMessage("Không có yêu cầu đặt trước đang chờ xử lý.");
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                addMessage("Không tải được dữ liệu đặt trước.");
                Toast.makeText(ManageReservationsActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private View createCard(Reservation reservation) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), Color.rgb(230, 233, 231));
        card.setBackground(background);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText(reservation.getBookTitle() == null ? "Sách đặt trước" : reservation.getBookTitle());
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(36, 59, 100));

        TextView details = new TextView(this);
        details.setText("Độc giả: " + safe(reservation.getCustomerName())
                + "\nMã độc giả: " + safe(reservation.getCustomerId())
                + "\nNgày đặt: " + DateUtils.formatDate(reservation.getReservationDate())
                + "\nMã đặt chỗ: " + safe(reservation.getReservationCode())
                + "\nNơi nhận: " + safe(reservation.getPickupLocation()));
        details.setTextSize(14);
        details.setTextColor(Color.rgb(80, 88, 102));

        card.addView(title);
        card.addView(details);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button approve = new Button(this);
        approve.setText("Duyệt");
        approve.setAllCaps(false);
        approve.setOnClickListener(v -> updateStatus(reservation, "APPROVED"));

        Button cancel = new Button(this);
        cancel.setText("Từ chối");
        cancel.setAllCaps(false);
        cancel.setOnClickListener(v -> updateStatus(reservation, "CANCELLED"));

        actions.addView(approve, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        card.addView(actions);
        return card;
    }

    private void updateStatus(Reservation reservation, String status) {
        reservation.setStatus(status);
        repository.updateReservation(reservation, new DataCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(ManageReservationsActivity.this,
                        "Đã cập nhật trạng thái: " + status, Toast.LENGTH_SHORT).show();
                loadReservations();
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(ManageReservationsActivity.this,
                        "Cập nhật thất bại: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addMessage(String message) {
        TextView text = new TextView(this);
        text.setText(message);
        text.setTextColor(Color.rgb(102, 112, 133));
        text.setPadding(0, dp(12), 0, dp(12));
        listContainer.addView(text);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Chưa cập nhật" : value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
