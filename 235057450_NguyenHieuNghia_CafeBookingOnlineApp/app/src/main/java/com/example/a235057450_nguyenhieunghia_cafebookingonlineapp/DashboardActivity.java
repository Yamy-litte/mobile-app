package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
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
        setContentView(R.layout.activity_dashboard);
        TextView welcome = findViewById(R.id.welcomeText);
        Button menu = findViewById(R.id.menuButton);
        Button history = findViewById(R.id.historyButton);
        Button logout = findViewById(R.id.logoutButton);
        welcome.setText("Xin chào, " + (email == null ? "bạn" : email));
        menu.setOnClickListener(v -> startActivity(new Intent(this, MenuActivity.class)));
        history.setOnClickListener(v -> startActivity(new Intent(this, OrderHistoryActivity.class)));
        logout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            returnToLogin();
        });
    }
    private void returnToLogin() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
