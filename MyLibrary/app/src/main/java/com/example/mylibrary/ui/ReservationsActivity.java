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
import com.example.mylibrary.models.Reservation;
import com.example.mylibrary.repository.ReservationRepository;
import com.example.mylibrary.util.DateUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class ReservationsActivity extends AppCompatActivity {

    private TextView txtResBack;
    private LinearLayout layoutReservationsList;
    private ReservationRepository reservationRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reservations);

        txtResBack = findViewById(R.id.txtResBack);
        layoutReservationsList = findViewById(R.id.layoutReservationsList);
        reservationRepository = new ReservationRepository();

        txtResBack.setOnClickListener(v -> finish());

        loadReservations();
    }

    private void loadReservations() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        reservationRepository.getReservationsByCustomerId(user.getUid(), new DataCallback<List<Reservation>>() {
            @Override
            public void onSuccess(List<Reservation> result) {
                layoutReservationsList.removeAllViews();
                if (result.isEmpty()) {
                    TextView empty = new TextView(ReservationsActivity.this);
                    empty.setText("Bạn chưa có yêu cầu đặt trước nào.");
                    empty.setTextColor(Color.rgb(102, 112, 133));
                    empty.setPadding(0, dp(12), 0, dp(12));
                    layoutReservationsList.addView(empty);
                    return;
                }

                for (Reservation res : result) {
                    layoutReservationsList.addView(createReservationCard(res));
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(ReservationsActivity.this, "Không tải được danh sách: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private View createReservationCard(Reservation res) {
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
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText(res.getBookTitle() != null ? res.getBookTitle() : "Sách đặt trước");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(36, 59, 100));

        TextView code = new TextView(this);
        code.setText("Mã đặt chỗ: " + (res.getReservationCode() != null ? res.getReservationCode() : "N/A"));
        code.setTextSize(13);
        code.setTextColor(Color.rgb(102, 112, 133));

        TextView dates = new TextView(this);
        dates.setText("Ngày đặt: " + DateUtils.formatDate(res.getReservationDate()) + " • Nơi nhận: " + (res.getPickupLocation() != null ? res.getPickupLocation() : "Tầng 2"));
        dates.setTextSize(13);
        dates.setTextColor(Color.rgb(102, 112, 133));

        TextView status = new TextView(this);
        status.setText("Trạng thái: " + (res.getStatus() != null ? res.getStatus() : "PENDING"));
        status.setTextSize(13);
        status.setTypeface(null, Typeface.BOLD);
        status.setTextColor("APPROVED".equals(res.getStatus()) ? Color.rgb(46, 125, 50) : Color.rgb(217, 119, 6));

        card.addView(title);
        card.addView(code);
        card.addView(dates);
        card.addView(status);

        if ("PENDING".equals(res.getStatus())) {
            Button btnCancel = new Button(this);
            btnCancel.setText("Hủy đặt chỗ");
            btnCancel.setAllCaps(false);
            btnCancel.setBackgroundColor(Color.rgb(217, 45, 32));
            btnCancel.setTextColor(Color.WHITE);
            btnCancel.setOnClickListener(v -> {
                reservationRepository.cancelReservation(res.getId(), new DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Toast.makeText(ReservationsActivity.this, "Đã hủy đặt chỗ!", Toast.LENGTH_SHORT).show();
                        loadReservations();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(ReservationsActivity.this, "Hủy thất bại: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            });
            card.addView(btnCancel);
        }

        return card;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
