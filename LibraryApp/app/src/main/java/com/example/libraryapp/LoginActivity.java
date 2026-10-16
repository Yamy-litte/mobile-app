package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.AdminDao;
import com.example.libraryapp.dao.CustomerDao;
import com.example.libraryapp.model.Admin;
import com.example.libraryapp.service.CloudLibraryRepository;
import com.google.firebase.messaging.FirebaseMessaging;

public class LoginActivity extends AppCompatActivity {
    private static final String CONFIGURED_ADMIN_EMAIL = "admin@gmail.com";
    private static final String CONFIGURED_ADMIN_UID = "hDZ5s2jS35ah0J98pVKAM2QQNxx2";

    private EditText emailInput, passwordInput;
    private String role = "reader";
    private FirebaseAuthManager auth;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        role = getIntent().getStringExtra("account_role");
        if (role == null) role = "reader";
        auth = new FirebaseAuthManager();
        emailInput = findViewById(R.id.editEmail);
        passwordInput = findViewById(R.id.editPassword);
        findViewById(R.id.btnLogin).setOnClickListener(v -> doLogin());
        findViewById(R.id.btnForgot).setOnClickListener(v -> sendResetEmail());
        findViewById(R.id.btnRegister).setOnClickListener(v -> {
            if ("admin".equals(role)) {
                Toast.makeText(this, "Tài khoản Admin được cấp bởi quản trị hệ thống.", Toast.LENGTH_LONG).show();
            } else {
                startActivity(new Intent(this, RegisterActivity.class));
            }
        });
    }

    private void doLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập email và mật khẩu.", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.login(email, password).addOnSuccessListener(result -> {
            if (result.getUser() == null) {
                Toast.makeText(this, "Không lấy được thông tin tài khoản Firebase.", Toast.LENGTH_LONG).show();
                return;
            }

            String uid = result.getUser().getUid();
            String authenticatedEmail = result.getUser().getEmail();

            if ("admin".equals(role)) {
                boolean allowed = ensureConfiguredAdmin(uid, authenticatedEmail);
                if (!allowed) {
                    auth.logout();
                    Toast.makeText(this, "Tài khoản này chưa được cấp quyền Admin. Hãy kiểm tra đúng tài khoản Firebase Admin đã cấu hình.", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, "Đăng nhập Admin thành công.", Toast.LENGTH_SHORT).show();
            } else {
                if (!ensureReaderProfile(uid, authenticatedEmail)) {
                    auth.logout();
                    Toast.makeText(this, "Không thể khôi phục hồ sơ độc giả trong SQLite. Hãy kiểm tra email hoặc đăng ký lại.", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, "Đăng nhập thành công.", Toast.LENGTH_SHORT).show();
                registerForPushNotifications(uid);
            }

            Intent intent = new Intent(this, "admin".equals(role) ? AdminHomeActivity.class : ReaderHomeActivity.class);
            intent.putExtra("account_role", role);
            startActivity(intent);
            finish();
        }).addOnFailureListener(e ->
            Toast.makeText(this, friendly(e.getMessage()), Toast.LENGTH_LONG).show());
    }

    /**
     * Recreates a minimal local Reader profile when emulator/app data was wiped.
     * Firebase Authentication remains the source of identity; no password is stored in SQLite.
     */
    private boolean ensureReaderProfile(String uid, String authenticatedEmail) {
        if (uid == null || uid.trim().isEmpty() || authenticatedEmail == null
                || authenticatedEmail.trim().isEmpty()) {
            return false;
        }

        CustomerDao dao = new CustomerDao(this);
        try {
            com.example.libraryapp.model.Customer customer = dao.getByFirebaseUid(uid);
            if (customer != null) {
                return "active".equalsIgnoreCase(customer.getStatus());
            }

            // Avoid silently attaching an existing local profile owned by a different Firebase UID.
            com.example.libraryapp.model.Customer sameEmail = dao.getByEmail(authenticatedEmail);
            if (sameEmail != null) {
                return false;
            }

            String email = authenticatedEmail.trim();
            String localPart = email.contains("@") ? email.substring(0, email.indexOf("@")) : email;
            String displayName = localPart.replace('.', ' ').replace('_', ' ').trim();
            if (displayName.isEmpty()) displayName = "Độc giả";

            com.example.libraryapp.model.Customer recovered = new com.example.libraryapp.model.Customer(
                    0, uid, email, displayName, null, null, "active", null, null);
            long id = dao.insert(recovered);
            if (id <= 0) return false;

            customer = dao.getByFirebaseUid(uid);
            return customer != null && "active".equalsIgnoreCase(customer.getStatus());
        } catch (Exception e) {
            android.util.Log.e("LoginActivity", "Unable to restore local Reader profile", e);
            return false;
        } finally {
            dao.close();
        }
    }

    /**
     * Bootstrap only the explicitly configured Firebase administrator.
     * A normal user cannot gain Admin permission by entering an email or choosing the Admin role.
     */
    private boolean ensureConfiguredAdmin(String uid, String authenticatedEmail) {
        if (uid == null || authenticatedEmail == null
                || !CONFIGURED_ADMIN_UID.equals(uid)
                || !CONFIGURED_ADMIN_EMAIL.equalsIgnoreCase(authenticatedEmail.trim())) {
            return false;
        }

        AdminDao dao = new AdminDao(this);
        try {
            Admin admin = dao.getByFirebaseUid(CONFIGURED_ADMIN_UID);
            if (admin == null) {
                // Refuse to attach the configured UID if another local admin already owns this email.
                Admin sameEmail = dao.getByEmail(CONFIGURED_ADMIN_EMAIL);
                if (sameEmail != null) return false;

                admin = new Admin();
                admin.setFirebaseUid(CONFIGURED_ADMIN_UID);
                admin.setEmail(CONFIGURED_ADMIN_EMAIL);
                admin.setFullName("Quản trị viên");
                admin.setStatus("active");
                if (dao.insert(admin) <= 0) return false;

                admin = dao.getByFirebaseUid(CONFIGURED_ADMIN_UID);
            }

            return admin != null
                    && CONFIGURED_ADMIN_EMAIL.equalsIgnoreCase(admin.getEmail())
                    && "active".equalsIgnoreCase(admin.getStatus());
        } finally {
            dao.close();
        }
    }

    private void registerForPushNotifications(String uid) {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 9001);
        }
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (token == null || token.trim().isEmpty()) return;
                    new CloudLibraryRepository().saveCurrentDeviceToken(token)
                            .addOnFailureListener(error -> android.util.Log.e(
                                    "FCM", "Unable to save device token for " + uid, error));
                })
                .addOnFailureListener(error -> android.util.Log.e("FCM", "Unable to get FCM token", error));
    }

    private void sendResetEmail() {
        String email = emailInput.getText().toString().trim();
        if (email.isEmpty()) {
            emailInput.setError("Nhập email để đặt lại mật khẩu");
            return;
        }
        auth.sendPasswordResetEmail(email).addOnSuccessListener(v ->
            Toast.makeText(this, "Đã gửi email đặt lại mật khẩu.", Toast.LENGTH_LONG).show()
        ).addOnFailureListener(e ->
            Toast.makeText(this, friendly(e.getMessage()), Toast.LENGTH_LONG).show());
    }

    private String friendly(String message) {
        if (message == null) return "Không thể xác thực. Vui lòng thử lại.";
        if (message.contains("password") || message.contains("credential")) return "Email hoặc mật khẩu không chính xác.";
        if (message.contains("network")) return "Không có kết nối mạng. Vui lòng thử lại.";
        return message;
    }
}
