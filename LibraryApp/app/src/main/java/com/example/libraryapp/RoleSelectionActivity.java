package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Role selection screen based on the MyLibrary Figma page 2. */
public class RoleSelectionActivity extends AppCompatActivity {
    private static final int CREAM = Color.rgb(255, 249, 240);
    private static final int TEAL = Color.rgb(28, 96, 88);
    private static final int INK = Color.rgb(36, 57, 53);
    private RadioGroup roleGroup;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(CREAM);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(28));

        TextView top = text("‹                         Đăng nhập", 20, INK, true);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView title = text("Chọn vai trò của bạn", 28, TEAL, true);
        title.setPadding(0, dp(28), 0, dp(8));
        root.addView(title);
        TextView intro = text("Vui lòng xác định tài khoản đăng nhập để hệ thống hiển thị đúng tính năng tương ứng.", 15, 0xFF78827F, false);
        root.addView(intro);

        roleGroup = new RadioGroup(this);
        roleGroup.setOrientation(LinearLayout.VERTICAL);
        roleGroup.setPadding(0, dp(26), 0, dp(18));
        roleGroup.addView(roleOption("reader", "Độc giả", "Sinh viên, giảng viên mượn sách & tài liệu học tập", true));
        roleGroup.addView(roleOption("admin", "Admin", "Quản lý kho sách, mượn trả, duyệt đặt trước, độc giả, tài khoản Admin và báo cáo", false));
        root.addView(roleGroup, new LinearLayout.LayoutParams(-1, -2));

        Button next = new Button(this);
        next.setText("Tiếp tục");
        next.setTextColor(Color.WHITE);
        next.setTextSize(16);
        next.setAllCaps(false);
        next.setBackgroundTintList(android.content.res.ColorStateList.valueOf(TEAL));
        root.addView(next, new LinearLayout.LayoutParams(-1, dp(54)));
        next.setOnClickListener(v -> {
            int checked = roleGroup.getCheckedRadioButtonId();
            if (checked == -1) {
                Toast.makeText(this, "Vui lòng chọn vai trò.", Toast.LENGTH_SHORT).show();
                return;
            }
            RadioButton selected = findViewById(checked);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra("account_role", String.valueOf(selected.getTag()));
            startActivity(intent);
        });
        setContentView(root);
    }

    private RadioButton roleOption(String tag, String title, String description, boolean checked) {
        RadioButton rb = new RadioButton(this);
        rb.setId(android.view.View.generateViewId());
        rb.setTag(tag);
        rb.setButtonTintList(android.content.res.ColorStateList.valueOf(TEAL));
        rb.setText(title + "\\n" + description);
        rb.setTextColor(INK);
        rb.setTextSize(16);
        rb.setPadding(dp(14), dp(14), dp(12), dp(14));
        rb.setMinHeight(dp(94));
        rb.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        roleGroup.addView(rb);
        if (checked) rb.setChecked(true);
        return rb;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(size);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}
