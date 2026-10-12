package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.UserDAO;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.User;

public class RegisterActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private EditText name, email, password, confirm;
    private Button submit;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        auth = FirebaseAuth.getInstance();
        setContentView(R.layout.activity_register);
        name = findViewById(R.id.nameInput);
        email = findViewById(R.id.emailInput);
        password = findViewById(R.id.passwordInput);
        confirm = findViewById(R.id.confirmInput);
        submit = findViewById(R.id.registerSubmitButton);
        Button back = findViewById(R.id.backToLoginButton);
        submit.setOnClickListener(v -> register());
        back.setOnClickListener(v -> finish());
    }

    private void register() {
        String fullName = name.getText().toString().trim();
        String mail = email.getText().toString().trim();
        String pass = password.getText().toString();
        if (fullName.isEmpty()) { name.setError("Nhập họ tên"); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) { email.setError("Email không hợp lệ"); return; }
        if (pass.length() < 6) { password.setError("Mật khẩu cần ít nhất 6 ký tự"); return; }
        if (!pass.equals(confirm.getText().toString())) { confirm.setError("Mật khẩu không khớp"); return; }
        submit.setEnabled(false);
        auth.createUserWithEmailAndPassword(mail, pass).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                submit.setEnabled(true);
                Toast.makeText(this, "Đăng ký thất bại. Email có thể đã tồn tại hoặc mật khẩu không hợp lệ.", Toast.LENGTH_LONG).show();
                return;
            }
            User profile = new User();
            profile.setFullName(fullName);
            profile.setEmail(mail);
            profile.setRole(User.ROLE_CUSTOMER);
            UserDAO dao = new UserDAO(getApplicationContext());
            long id = dao.insert(profile);
            dao.close();
            auth.signOut();
            Toast.makeText(this, id > 0 ? "Đăng ký thành công. Hãy đăng nhập." :
                    "Tài khoản Firebase đã tạo nhưng lưu hồ sơ SQLite thất bại.", Toast.LENGTH_LONG).show();
            finish();
        });
    }

}
