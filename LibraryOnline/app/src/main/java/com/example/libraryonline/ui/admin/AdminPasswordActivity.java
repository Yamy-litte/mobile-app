package com.example.libraryonline.ui.admin;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.libraryonline.R;
import com.example.libraryonline.data.session.SessionProfile;
import com.example.libraryonline.ui.auth.SessionActivity;
import com.example.libraryonline.utils.PasswordVisibilityToggle;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Dedicated password screen that enforces an Admin session rather than a Reader session. */
public final class AdminPasswordActivity extends SessionActivity {
    private EditText password, confirm;
    private Button submit;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_auth_change_password);
        password = findViewById(R.id.editNewPassword);
        confirm = findViewById(R.id.editConfirmPassword);
        PasswordVisibilityToggle.attach(password);
        PasswordVisibilityToggle.attach(confirm);
        submit = findViewById(R.id.buttonChangePassword);
        submit.setOnClickListener(v -> changePassword());
    }
    @Override protected SessionProfile.Role expectedRole() { return SessionProfile.Role.ADMIN; }
    @Override protected void onSessionAuthorized(SessionProfile profile) {}
    private void changePassword() {
        if (!isSessionReady()) return;
        String next = password.getText().toString(), repeated = confirm.getText().toString();
        if (next.length() < 8) { password.setError("Mật khẩu phải có ít nhất 8 ký tự"); return; }
        if ("123456".equals(next)) { password.setError("Không được dùng mật khẩu mặc định"); return; }
        if (!next.equals(repeated)) { confirm.setError("Mật khẩu xác nhận không khớp"); return; }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { logoutSession(); return; }
        submit.setEnabled(false);
        user.updatePassword(next).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Đã đổi mật khẩu quản trị.", Toast.LENGTH_LONG).show();
                finish();
            } else {
                submit.setEnabled(true);
                Toast.makeText(this, "Không đổi được mật khẩu. Có thể cần đăng nhập lại để xác minh bảo mật.", Toast.LENGTH_LONG).show();
            }
        });
    }
}
