package com.example.libraryapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.AdminDao;
import com.example.libraryapp.dao.CustomerDao;

public class LoginActivity extends AppCompatActivity {
    private static final int CREAM = Color.rgb(255, 249, 240);
    private static final int TEAL = Color.rgb(28, 96, 88);
    private static final int INK = Color.rgb(36, 57, 53);
    private EditText emailInput, passwordInput;
    private String role = "reader";
    private FirebaseAuthManager auth;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        role = getIntent().getStringExtra("account_role");
        if (role == null) role = "reader";
        auth = new FirebaseAuthManager();
        getWindow().getDecorView().setBackgroundColor(CREAM);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(36), dp(24), dp(24));

        TextView logo = text("▣\nMyLibrary", 30, TEAL, true);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo, new LinearLayout.LayoutParams(-1, dp(130)));
        TextView subtitle = text("Hệ thống thư viện hiện đại UEF", 15, 0xFF78827F, false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(34)));

        root.addView(label("Email thư viện"));
        emailInput = field("Nhập email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(emailInput, params(54, 0, 0, 0, 20));
        root.addView(label("Mật khẩu"));
        passwordInput = field("Nhập mật khẩu", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(passwordInput, params(54, 0, 0, 0, 8));

        LinearLayout links = new LinearLayout(this);
        links.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox remember = new CheckBox(this);
        remember.setText("Ghi nhớ đăng nhập");
        remember.setTextColor(0xFF78827F);
        remember.setButtonTintList(android.content.res.ColorStateList.valueOf(TEAL));
        links.addView(remember, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView forgot = text("Quên mật khẩu?", 14, 0xFFB76C4C, true);
        links.addView(forgot);
        root.addView(links);
        forgot.setOnClickListener(v -> sendResetEmail());

        android.widget.Space spacer = new android.widget.Space(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        Button login = button("Đăng nhập");
        root.addView(login, params(54, 0, 0, 0, 12));
        login.setOnClickListener(v -> doLogin());

        TextView register = text("Bạn chưa có tài khoản?  Đăng ký ngay", 14, INK, false);
        register.setGravity(Gravity.CENTER);
        register.setPadding(0, dp(16), 0, dp(12));
        root.addView(register);
        register.setOnClickListener(v -> {
            if ("admin".equals(role)) {
                Toast.makeText(this, "Tài khoản Admin được cấp bởi quản trị hệ thống.", Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(this, RegisterActivity.class));
        });
        setContentView(root);
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