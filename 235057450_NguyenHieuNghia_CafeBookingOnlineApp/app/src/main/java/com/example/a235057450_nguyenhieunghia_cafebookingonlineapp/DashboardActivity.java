package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.User;

public class DashboardActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            returnToLogin();
            return;
        }
        String email = getIntent().getStringExtra("user_email");
        String role = getIntent().getStringExtra("user_role");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(28, 24, 28, 24);
        root.setBackgroundColor(0xFFFAF7F2);
        TextView title = new TextView(this);
        title.setText("Café Booking Online");
        title.setTextSize(26);
        title.setTextColor(0xFF744B30);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        root.addView(title, params());
        TextView info = new TextView(this);
        info.setText("Đăng nhập thành công\n\nEmail: " + email + "\nVai trò: " + role
                + "\n\n" + (User.ROLE_ADMIN.equals(role) ? "Khu vực Administrator" : "Khu vực Customer"));
        info.setTextSize(17);
        info.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ip = params();
        ip.topMargin = 24;
        root.addView(info, ip);
        Button menu = new Button(this);
        menu.setText("Xem thực đơn");
        LinearLayout.LayoutParams mp = params();
        mp.topMargin = 20;
        root.addView(menu, mp);
        menu.setOnClickListener(v -> startActivity(new Intent(this, MenuActivity.class)));

        Button history = new Button(this);
        history.setText("Lịch sử đơn hàng");
        LinearLayout.LayoutParams hp = params();
        hp.topMargin = 10;
        root.addView(history, hp);
        history.setOnClickListener(v -> startActivity(new Intent(this, OrderHistoryActivity.class)));

        Button logout = new Button(this);
        logout.setText("Đăng xuất");
        LinearLayout.LayoutParams bp = params();
        bp.topMargin = 24;
        root.addView(logout, bp);
        logout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            returnToLogin();
        });
        setContentView(root);
    }
    private void returnToLogin() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(-1, -2);
    }
}