package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.text.InputType;
import androidx.appcompat.app.AlertDialog;
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
        findViewById(R.id.btnChangePassword).setOnClickListener(v -> showChangePasswordDialog(auth));
        findViewById(R.id.btnDigitalLibraryCard).setOnClickListener(v -> showDigitalCard());
        findViewById(R.id.btnAccountLogout).setOnClickListener(v -> {
            auth.logout();
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent); finish();
        });
    }

    private void showChangePasswordDialog(FirebaseAuthManager auth) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad / 2, pad, 0);
        EditText password = new EditText(this);
        password.setHint("Mật khẩu mới (ít nhất 6 ký tự)");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(password);
        EditText confirm = new EditText(this);
        confirm.setHint("Xác nhận mật khẩu mới");
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(confirm);
        new AlertDialog.Builder(this).setTitle("Đổi mật khẩu").setView(form)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Cập nhật", (dialog, which) -> {
                    String next = password.getText().toString();
                    String repeated = confirm.getText().toString();
                    if (next.length() < 6) {
                        Toast.makeText(this, "Mật khẩu phải có ít nhất 6 ký tự.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!next.equals(repeated)) {
                        Toast.makeText(this, "Mật khẩu xác nhận không khớp.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        auth.updatePassword(next).addOnSuccessListener(unused ->
                                Toast.makeText(this, "Đổi mật khẩu thành công.", Toast.LENGTH_LONG).show())
                                .addOnFailureListener(error -> Toast.makeText(this,
                                        "Không thể đổi mật khẩu. Có thể bạn cần đăng nhập lại: " +
                                                (error.getMessage() == null ? "" : error.getMessage()),
                                        Toast.LENGTH_LONG).show());
                    } catch (Exception error) {
                        Toast.makeText(this, "Không thể đổi mật khẩu: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void showDigitalCard() {
        String name = customer == null ? "" : customer.getFullName();
        String email = customer == null ? "" : customer.getEmail();
        String cardCode = "LIB-" + (customer == null ? "" : String.valueOf(customer.getId()));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundColor(getColor(R.color.mylibrary_cream));
        TextView brand = new TextView(this);
        brand.setText("MyLibrary  ·  THẺ ĐỘC GIẢ");
        brand.setTextSize(16);
        brand.setTextColor(getColor(R.color.mylibrary_teal));
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(brand);
        TextView nameView = new TextView(this);
        nameView.setText("\\n" + name);
        nameView.setTextSize(22);
        nameView.setTextColor(getColor(R.color.mylibrary_ink));
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(nameView);
        TextView emailView = new TextView(this);
        emailView.setText(email);
        emailView.setTextColor(getColor(R.color.mylibrary_muted));
        card.addView(emailView);
        TextView codeView = new TextView(this);
        codeView.setText("\\nMã độc giả\\n" + cardCode + "\\n\\nĐưa mã này cho thủ thư để đối chiếu tài khoản.");
        codeView.setTextSize(15);
        codeView.setTextColor(getColor(R.color.mylibrary_ink));
        card.addView(codeView);
        new AlertDialog.Builder(this).setTitle("Thẻ thư viện số").setView(card)
                .setPositiveButton("Đóng", null).show();
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