package com.example.libraryonline.ui.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.libraryonline.R;
import com.example.libraryonline.data.session.SessionProfile;
import com.example.libraryonline.ui.auth.SessionActivity;
import com.example.libraryonline.utils.PasswordVisibilityToggle;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;

/** Dedicated Admin password form. Does not affect first-time Reader password setup. */
public final class AdminPasswordActivity extends SessionActivity {
    private EditText currentPassword;
    private EditText newPassword;
    private EditText confirmPassword;
    private Button submitButton;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_admin_change_password);

        currentPassword = findViewById(R.id.editAdminCurrentPassword);
        newPassword = findViewById(R.id.editAdminNewPassword);
        confirmPassword = findViewById(R.id.editAdminConfirmPassword);
        submitButton = findViewById(R.id.buttonAdminChangePassword);
        statusText = findViewById(R.id.textAdminPasswordStatus);

        PasswordVisibilityToggle.attach(currentPassword);
        PasswordVisibilityToggle.attach(newPassword);
        PasswordVisibilityToggle.attach(confirmPassword);

        findViewById(R.id.adminPasswordBack).setOnClickListener(v -> finish());
        submitButton.setOnClickListener(v -> changePassword());
    }

    @Override
    protected SessionProfile.Role expectedRole() {
        return SessionProfile.Role.ADMIN;
    }

    @Override
    protected void onSessionAuthorized(SessionProfile profile) {
        setBusy(false);
    }

    @Override
    protected void onSessionStopped() {
        // Never keep password values in an inactive Admin screen.
        if (currentPassword != null) currentPassword.setText("");
        if (newPassword != null) newPassword.setText("");
        if (confirmPassword != null) confirmPassword.setText("");
        if (submitButton != null) setBusy(false);
        if (statusText != null) statusText.setVisibility(View.GONE);
    }

    private void setBusy(boolean busy) {
        submitButton.setEnabled(!busy);
        submitButton.setText(
                busy ? R.string.admin_password_submitting : R.string.admin_password_submit);
    }

    private void showError(String message) {
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
    }

    private boolean sessionMatches(String uid) {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        return isSessionReady()
                && !isFinishing()
                && !isDestroyed()
                && current != null
                && uid.equals(current.getUid());
    }

    private void changePassword() {
        if (!isSessionReady() || !submitButton.isEnabled()) return;

        String old = currentPassword.getText().toString();
        String next = newPassword.getText().toString();
        String repeated = confirmPassword.getText().toString();

        if (old.isEmpty()) {
            currentPassword.setError("Vui lòng nhập mật khẩu hiện tại");
            currentPassword.requestFocus();
            return;
        }
        if (next.length() < 8) {
            newPassword.setError("Mật khẩu mới phải có ít nhất 8 ký tự");
            newPassword.requestFocus();
            return;
        }
        if (next.equals(old)) {
            newPassword.setError("Mật khẩu mới phải khác mật khẩu hiện tại");
            newPassword.requestFocus();
            return;
        }
        if ("123456".equals(next)) {
            newPassword.setError("Không được sử dụng mật khẩu mặc định");
            newPassword.requestFocus();
            return;
        }
        if (!next.equals(repeated)) {
            confirmPassword.setError("Mật khẩu xác nhận không khớp");
            confirmPassword.requestFocus();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showError("Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.");
            return;
        }
        String uid = user.getUid();
        String email = user.getEmail();
        if (email == null || email.trim().isEmpty()) {
            showError("Không tìm thấy email đăng nhập để xác minh mật khẩu.");
            return;
        }

        statusText.setVisibility(View.GONE);
        setBusy(true);
        // Verify the existing password with Firebase Auth, not Firestore/SQLite.
        user.reauthenticate(EmailAuthProvider.getCredential(email, old))
                .addOnCompleteListener(this, authTask -> {
                    if (!sessionMatches(uid)) return;
                    currentPassword.setText("");
                    if (!authTask.isSuccessful()) {
                        setBusy(false);
                        Exception cause = authTask.getException();
                        if (cause instanceof FirebaseAuthInvalidCredentialsException) {
                            currentPassword.setError("Mật khẩu hiện tại không đúng");
                            showError("Mật khẩu hiện tại không đúng.");
                        } else if (cause instanceof FirebaseNetworkException) {
                            showError("Không thể kết nối mạng. Vui lòng thử lại.");
                        } else if (cause instanceof FirebaseTooManyRequestsException) {
                            showError("Thao tác quá nhiều lần. Vui lòng thử lại sau.");
                        } else {
                            showError("Không xác minh được mật khẩu hiện tại. Vui lòng thử lại.");
                        }
                        return;
                    }
                    user.updatePassword(next)
                            .addOnCompleteListener(this, updateTask -> {
                                if (!sessionMatches(uid)) return;
                                setBusy(false);
                                if (!updateTask.isSuccessful()) {
                                    Exception cause = updateTask.getException();
                                    if (cause instanceof FirebaseNetworkException) {
                                        showError("Mất kết nối mạng. Mật khẩu chưa được cập nhật.");
                                    } else {
                                        showError("Không cập nhật được mật khẩu. Vui lòng thử lại.");
                                    }
                                    return;
                                }
                                currentPassword.setText("");
                                newPassword.setText("");
                                confirmPassword.setText("");
                                Toast.makeText(
                                        this, "Đổi mật khẩu quản trị thành công.", Toast.LENGTH_LONG)
                                        .show();
                                finish();
                            });
                });
    }
}
