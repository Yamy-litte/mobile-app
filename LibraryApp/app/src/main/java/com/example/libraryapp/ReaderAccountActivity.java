package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.CustomerDao;
import com.example.libraryapp.model.Customer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReaderAccountActivity extends AppCompatActivity {
    private CustomerDao customerDao;
    private Customer customer;
    private EditText fullNameInput, phoneInput;
    private TextView emailText;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseAuthManager auth = new FirebaseAuthManager();
        if (!auth.isLoggedIn()) { goToLogin(); return; }
        setContentView(R.layout.activity_reader_account);
        ReaderTabNavigation.bind(this);
        fullNameInput = findViewById(R.id.editFullName);
        phoneInput = findViewById(R.id.editPhone);
        emailText = findViewById(R.id.textAccountEmail);
        customerDao = new CustomerDao(this);
        customer = customerDao.getByFirebaseUid(auth.getCurrentUid());
        if (customer == null) {
            Toast.makeText(this, "Không tìm thấy hồ sơ độc giả. Vui lòng đăng nhập lại.", Toast.LENGTH_LONG).show();
            auth.logout(); goToLogin(); return;
        }
        fullNameInput.setText(customer.getFullName());
        phoneInput.setText(customer.getPhoneNumber());
        emailText.setText(customer.getEmail());
        findViewById(R.id.btnSaveProfile).setOnClickListener(v -> saveProfile());
        findViewById(R.id.btnAccountLogout).setOnClickListener(v -> {
            auth.logout();
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent); finish();
        });
    }

    private void saveProfile() {
        String name = fullNameInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        if (name.isEmpty()) { fullNameInput.setError("Vui lòng nhập họ tên"); fullNameInput.requestFocus(); return; }
        customer.setFullName(name);
        customer.setPhoneNumber(phone);
        customer.setUpdatedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        if (customerDao.updateProfile(customer) > 0) {
            Toast.makeText(this, "Đã cập nhật thông tin tài khoản.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Không thể cập nhật. Hãy thử lại.", Toast.LENGTH_SHORT).show();
        }
    }

    private void goToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent); finish();
    }
    @Override protected void onDestroy() { if (customerDao != null) customerDao.close(); super.onDestroy(); }
}