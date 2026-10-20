package com.example.libraryonline.ui.reader;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class ReaderMainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView message = new TextView(this);
        message.setText("Library Online\nĐăng nhập độc giả thành công.");
        message.setTextSize(22);
        message.setPadding(32, 32, 32, 32);
        setContentView(message);
    }
}
