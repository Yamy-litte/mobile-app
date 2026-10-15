package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.AdminDao;
import com.example.libraryapp.dao.CustomerDao;

public class LoginActivity extends AppCompatActivity {
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
            String uid = result.getUser().getUid();
            if ("admin".equals(role)) {
                AdminDao dao = new AdminDao(this);
                boolean allowed = dao.getByFirebaseUid(uid) != null;
                dao.close();
                if (!allowed) {
                    auth.logout();
                    Toast.makeText(this, "Tài khoản này chưa được cấp quyền Admin.", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, "Đăng nhập Admin thành công.", Toast.LENGTH_SHORT).show();
            } else {
                CustomerDao dao = new CustomerDao(this);
                boolean profileExists = dao.getByFirebaseUid(uid) != null;
                dao.close();
                if (!profileExists) {
                    auth.logout();
                    Toast.makeText(this, "Không tìm thấy hồ sơ độc giả. Vui lòng đăng ký tài khoản.", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, "Đăng nhập thành công.", Toast.LENGTH_SHORT).show();
            }
            Intent intent = new Intent(this, ReaderHomeActivity.class);
            intent.putExtra("account_role", role);
            startActivity(intent);
            finish();
        }).addOnFailureListener(e ->
            Toast.makeText(this, friendly(e.getMessage()), Toast.LENGTH_LONG).show());
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
        if (message.contains("password")) return "Email hoặc mật khẩu không chính xác.";
        if (message.contains("network")) return "Không có kết nối mạng. Vui lòng thử lại.";
        return message;
    }
    private TextView label(String value) { TextView t=text(value,15,INK,true); t.setPadding(0,dp(18),0,dp(8)); return t; }
    private EditText field(String hint,int type) {
        EditText e=new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setTextSize(15); e.setInputType(type);
        e.setPadding(dp(16),0,dp(16),0); e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE5E8E5)); return e;
    }
    private Button button(String value) { Button b=new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(16); b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(TEAL)); return b; }
    private TextView text(String value,int size,int color,boolean bold) { TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
    private LinearLayout.LayoutParams params(int h,int l,int t,int r,int b) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(h)); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p; }
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
}