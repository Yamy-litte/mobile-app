package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class RoleSelectionActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_role_selection);
        RadioGroup roleGroup = findViewById(R.id.roleGroup);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnContinue).setOnClickListener(v -> {
            int checked = roleGroup.getCheckedRadioButtonId();
            if (checked == -1) {
                Toast.makeText(this, "Vui lòng chọn vai trò.", Toast.LENGTH_SHORT).show();
                return;
            }
            String role = checked == R.id.radioAdmin ? "admin" : "reader";
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra("account_role", role);
            startActivity(intent);
        });
    }
}