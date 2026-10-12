package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(28, 24, 28, 24);
        root.setBackgroundColor(0xFFFAF7F2);
        TextView title = new TextView(this);
        title.setText("Tạo tài khoản");
        title.setTextSize(25);
        title.setTextColor(0xFF744B30);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, params());
        name = field(root, "Họ và tên", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        email = field(root, "Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        password = field(root, "Mật khẩu (ít nhất 6 ký tự)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm = field(root, "Nhập lại mật khẩu", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        submit = new Button(this);
        submit.setText("Đăng ký");
        root.addView(submit, params());
        submit.setOnClickListener(v -> register());
        Button back = new Button(this);
        back.setText("Quay lại đăng nhập");
        root.addView(back, params());
        back.setOnClickListener(v -> finish());
        setContentView(root);
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

    private EditText field(LinearLayout root, String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        LinearLayout.LayoutParams p = params();
        p.topMargin = 8;
        root.addView(e, p);
        return e;
    }
    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(-1, -2);
    }
}