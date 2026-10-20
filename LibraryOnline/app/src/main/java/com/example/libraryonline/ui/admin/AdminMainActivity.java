package com.example.libraryonline.ui.admin;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AdminMainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView message = new TextView(this);
        message.setText("Library Online\nĐăng nhập quản trị viên thành công.");
        message.setTextSize(22);
        message.setPadding(32, 32, 32, 32);
        setContentView(message);
    }
}
