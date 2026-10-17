package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.AdminDao;
import com.example.libraryapp.model.Admin;

public class AdminAccountActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseAuthManager auth = new FirebaseAuthManager();
        if (!auth.isLoggedIn()) { goToLogin(); return; }
        AdminDao dao = new AdminDao(this);
        Admin admin = dao.getByFirebaseUid(auth.getCurrentUid());
        dao.close();
        if (admin == null || !"active".equalsIgnoreCase(admin.getStatus())) {
            auth.logout();
            goToLogin();
            return;
        }
        setContentView(R.layout.activity_admin_account);
        AdminTabNavigation.bind(this);
        ((TextView) findViewById(R.id.textAdminAccountEmail)).setText(
                auth.getCurrentEmail() == null ? "Email chưa cập nhật" : auth.getCurrentEmail());
        ((TextView) findViewById(R.id.textAdminAccountUid)).setText("UID: " + auth.getCurrentUid());
        findViewById(R.id.btnAdminAccountLogout).setOnClickListener(v -> {
            auth.logout();
            goToLogin();
        });
    }

    private void goToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
