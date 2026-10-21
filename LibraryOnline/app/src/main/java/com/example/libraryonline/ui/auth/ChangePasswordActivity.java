package com.example.libraryonline.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.libraryonline.R;
import com.example.libraryonline.utils.PasswordVisibilityToggle;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ChangePasswordActivity extends SessionActivity {
    private EditText newPasswordInput;
    private EditText confirmPasswordInput;
    private Button changePasswordButton;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_change_password);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        newPasswordInput = findViewById(R.id.editNewPassword);
        PasswordVisibilityToggle.attach(newPasswordInput);
        confirmPasswordInput = findViewById(R.id.editConfirmPassword);
        PasswordVisibilityToggle.attach(confirmPasswordInput);
        changePasswordButton = findViewById(R.id.buttonChangePassword);
        changePasswordButton.setOnClickListener(v -> changePassword());
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new androidx.activity.OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                logoutSession();
                            }
                        });
    }

    @Override
    protected com.example.libraryonline.data.session.SessionProfile.Role expectedRole() {
        return com.example.libraryonline.data.session.SessionProfile.Role.READER;
    }

    @Override
    protected boolean allowPasswordChange() {
        return true;
    }

    @Override
    protected void onSessionAuthorized(
            com.example.libraryonline.data.session.SessionProfile profile) {}

    private void changePassword() {
        if (!isSessionReady() || !changePasswordButton.isEnabled()) return;
        String password = newPasswordInput.getText().toString();
        String confirmation = confirmPasswordInput.getText().toString();
        if (password.length() < 8) {
            newPasswordInput.setError("Mật khẩu mới phải có ít nhất 8 ký tự");
            newPasswordInput.requestFocus();
            return;
        }
        if (password.equals("123456")) {
            newPasswordInput.setError("Không được dùng lại mật khẩu tạm");
            newPasswordInput.requestFocus();
            return;
        }
        if (!password.equals(confirmation)) {
            confirmPasswordInput.setError("Mật khẩu xác nhận không khớp");
            confirmPasswordInput.requestFocus();
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Phiên đăng nhập đã hết. Hãy đăng nhập lại.", Toast.LENGTH_LONG)
                    .show();
            auth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        changePasswordButton.setEnabled(false);
        changePasswordButton.setText("Đang cập nhật...");
        user.updatePassword(password)
                .addOnCompleteListener(
                        passwordTask -> {
                            if (!passwordTask.isSuccessful()) {
                                changePasswordButton.setEnabled(true);
                                changePasswordButton.setText("Đổi mật khẩu");
                                Toast.makeText(
                                                this,
                                                "Không đổi được mật khẩu. Hãy đăng nhập lại nếu"
                                                    + " phiên đã hết hạn.",
                                                Toast.LENGTH_LONG)
                                        .show();
                                return;
                            }
                            if (auth.getCurrentUser() == null
                                    || !user.getUid().equals(auth.getCurrentUser().getUid()))
                                return;
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("mustChangePassword", false);
                            updates.put("updatedAt", FieldValue.serverTimestamp());
                            firestore
                                    .collection("Users")
                                    .document(user.getUid())
                                    .update(updates)
                                    .addOnSuccessListener(
                                            unused -> {
                                                Toast.makeText(
                                                                this,
                                                                "Đổi mật khẩu thành công.",
                                                                Toast.LENGTH_SHORT)
                                                        .show();
                                                recheckSession();
                                            })
                                    .addOnFailureListener(
                                            error -> {
                                                changePasswordButton.setEnabled(true);
                                                changePasswordButton.setText("Đổi mật khẩu");
                                                Toast.makeText(
                                                                this,
                                                                "Mật khẩu đã đổi nhưng chưa cập"
                                                                    + " nhật được hồ sơ. Hãy thử"
                                                                    + " lại; không thể vào thư viện"
                                                                    + " trước khi hoàn tất.",
                                                                Toast.LENGTH_LONG)
                                                        .show();
                                            });
                        });
    }
}
