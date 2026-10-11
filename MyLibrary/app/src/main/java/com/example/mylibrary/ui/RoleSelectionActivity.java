
package com.example.mylibrary.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylibrary.R;

public class RoleSelectionActivity extends AppCompatActivity {

    private RadioGroup roleGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        roleGroup = findViewById(R.id.roleGroup);

        Button btnContinue = findViewById(R.id.btnContinue);
        TextView txtBackWelcome = findViewById(R.id.txtBackWelcome);

        btnContinue.setOnClickListener(v -> {
            int selectedId = roleGroup.getCheckedRadioButtonId();

            if (selectedId == -1) {
                Toast.makeText(
                        this,
                        "Vui lòng chọn vai trò.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            String selectedRole;

            if (selectedId == R.id.radioCustomer) {
                selectedRole = "Customer";
            } else if (selectedId == R.id.radioAdmin) {
                selectedRole = "Admin";
            } else {
                return;
            }

            Intent intent = new Intent(
                    RoleSelectionActivity.this,
                    LoginActivity.class
            );

            intent.putExtra("selected_role", selectedRole);
            startActivity(intent);
        });

        txtBackWelcome.setOnClickListener(v -> finish());
    }
}
