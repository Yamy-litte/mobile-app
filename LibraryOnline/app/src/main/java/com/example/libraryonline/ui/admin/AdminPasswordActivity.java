package com.example.libraryonline.ui.admin;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import com.google.firebase.auth.EmailAuthProvider;
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
    private EditText currentPassword, password, confirm;
    private Button submit;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_auth_change_password);
        ((TextView) findViewById(R.id.textChangePasswordTitle)).setText(R.string.admin_change_password_title);
        ((TextView) findViewById(R.id.textChangePasswordDescription)).setText(R.string.admin_change_password_description);
        currentPassword = new EditText(this);
        currentPassword.setSingleLine(true);
        currentPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        currentPassword.setHint(R.string.admin_current_password_hint);
        currentPassword.setBackgroundResource(R.drawable.bg_auth_input);
        currentPassword.setPadding(dp(14), 0, dp(14), 0);
        android.widget.LinearLayout form = (android.widget.LinearLayout) ((android.widget.ScrollView) findViewById(android.R.id.content).getChildAt(0)).getChildAt(0);
        form.addView(currentPassword, 3, new android.widget.LinearLayout.LayoutParams(-1, dp(52)));
        password = findViewById(R.id.editNewPassword);
        confirm = findViewById(R.id.editConfirmPassword);
        PasswordVisibilityToggle.attach(password);
        PasswordVisibilityToggle.attach(confirm);
        submit = findViewById(R.id.buttonChangePassword);
        submit.setOnClickListener(v -> changePassword());
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    @Override protected SessionProfile.Role expectedRole() { return SessionProfile.Role.ADMIN; }
    @Override protected void onSessionAuthorized(SessionProfile profile) {}
    private void changePassword() {
        if (!isSessionReady()) return;
        String old = currentPassword.getText().toString(), next = password.getText().toString(), repeated = confirm.getText().toString();
        if (old.isEmpty()) { currentPassword.setError("Vui lòng nhập mật khẩu hiện tại"); return; }
        if (next.length() < 8) { password.setError("Mật khẩu phải có ít nhất 8 ký tự"); return; }
        if ("123456".equals(next)) { password.setError("Không được dùng mật khẩu mặc định"); return; }
        if (!next.equals(repeated)) { confirm.setError("Mật khẩu xác nhận không khớp"); return; }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { logoutSession(); return; }
        if (user.getEmail() == null) { Toast.makeText(this, "Tài khoản chưa có email để xác minh.", Toast.LENGTH_LONG).show(); return; }
        submit.setEnabled(false);
        user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), old)).continueWithTask(task -> {
            if (!task.isSuccessful()) return com.google.android.gms.tasks.Tasks.forException(task.getException() == null ? new IllegalStateException("Xác minh mật khẩu cũ thất bại") : task.getException());
            return user.updatePassword(next);
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Đã đổi mật khẩu quản trị.", Toast.LENGTH_LONG).show();
                finish();
            } else {
                submit.setEnabled(true);
                currentPassword.setError("Mật khẩu hiện tại không đúng hoặc phiên cần xác minh lại");
                Toast.makeText(this, "Không đổi được mật khẩu. Hãy kiểm tra mật khẩu hiện tại.", Toast.LENGTH_LONG).show();
            }
        });
    }
}
