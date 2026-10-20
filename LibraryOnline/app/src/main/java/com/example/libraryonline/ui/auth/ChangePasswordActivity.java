package com.example.libraryonline.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;
import com.example.libraryonline.utils.PasswordVisibilityToggle;
import com.example.libraryonline.ui.reader.ReaderMainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ChangePasswordActivity extends AppCompatActivity {
    private EditText newPasswordInput;
    private EditText confirmPasswordInput;
    private Button changePasswordButton;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        newPasswordInput = findViewById(R.id.editNewPassword);
        PasswordVisibilityToggle.attach(newPasswordInput);
        confirmPasswordInput = findViewById(R.id.editConfirmPassword);
        PasswordVisibilityToggle.attach(confirmPasswordInput);
        changePasswordButton = findViewById(R.id.buttonChangePassword);
        changePasswordButton.setOnClickListener(v -> changePassword());
    }

    private void changePassword() {
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
            Toast.makeText(this, "Phiên đăng nhập đã hết. Hãy đăng nhập lại.", Toast.LENGTH_LONG).show();
            auth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        changePasswordButton.setEnabled(false);
        changePasswordButton.setText("Đang cập nhật...");
        user.updatePassword(password).addOnCompleteListener(passwordTask -> {
            if (!passwordTask.isSuccessful()) {
                changePasswordButton.setEnabled(true);
                changePasswordButton.setText("Đổi mật khẩu");
                Toast.makeText(this, "Không đổi được mật khẩu. Hãy đăng nhập lại nếu phiên đã hết hạn.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("mustChangePassword", false);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            firestore.collection("Users").document(user.getUid()).update(updates)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, "Đổi mật khẩu thành công.", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, ReaderMainActivity.class));
                        finish();
                    })
                    .addOnFailureListener(error -> {
                        changePasswordButton.setEnabled(true);
                        changePasswordButton.setText("Đổi mật khẩu");
                        Toast.makeText(this,
                                "Mật khẩu đã đổi nhưng chưa cập nhật được hồ sơ. Hãy thử lại; không thể vào thư viện trước khi hoàn tất.",
                                Toast.LENGTH_LONG).show();
                    });
        });
    }

    @Override
    public void onBackPressed() {
        // The first-login password change is mandatory.
        auth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
