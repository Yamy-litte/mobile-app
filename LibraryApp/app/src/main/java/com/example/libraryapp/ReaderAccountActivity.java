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
import com.example.libraryapp.dao.LibraryCardDao;
import com.example.libraryapp.model.Customer;
import com.example.libraryapp.model.LibraryCard;
import com.example.libraryapp.service.CloudLibraryRepository;
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
        FirebaseAuthManager auth = new FirebaseAuthManager();
        String uid = auth.getCurrentUid();
        if (customer == null || uid == null) {
            Toast.makeText(this, "Không tìm thấy thông tin thẻ độc giả.", Toast.LENGTH_LONG).show();
            return;
        }

        LibraryCardDao cardDao = new LibraryCardDao(this);
        try {
            LibraryCard savedCard = cardDao.getByCustomerId(customer.getId());
            String issueDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            if (savedCard == null) {
                // Create a stable code only once. SQLite row IDs differ between devices,
                // so the Firebase UID is the source for a newly issued card number.
                String newCardCode = "LIB-" + uid.substring(0, Math.min(10, uid.length())).toUpperCase(Locale.ROOT);
                savedCard = new LibraryCard();
                savedCard.setCustomerId(customer.getId());
                savedCard.setCardNumber(newCardCode);
                savedCard.setStatus("active");
                savedCard.setIssueDate(issueDate);
                long insertedId = cardDao.insert(savedCard);
                if (insertedId <= 0) {
                    Toast.makeText(this, "Không thể tạo thẻ thư viện. Hãy thử lại.", Toast.LENGTH_LONG).show();
                    return;
                }
                // Re-read so the card object reflects the persisted record.
                savedCard = cardDao.getByCustomerId(customer.getId());
                if (savedCard == null) {
                    Toast.makeText(this, "Không thể đọc lại thẻ vừa tạo. Hãy thử lại.", Toast.LENGTH_LONG).show();
                    return;
                }
            }

            // Preserve existing card numbers, including older numeric cards, so printed
            // barcodes and Admin lookup remain identical instead of silently changing codes.
            String barcodeValue = savedCard.getCardNumber() == null ? "" : savedCard.getCardNumber().trim();
            if (barcodeValue.isEmpty()) {
                Toast.makeText(this, "Thẻ chưa có mã hợp lệ. Hãy tạo lại thẻ thư viện.", Toast.LENGTH_LONG).show();
                return;
            }
            new CloudLibraryRepository().upsertReaderCardNumber(uid, barcodeValue)
                    .addOnFailureListener(error -> Toast.makeText(this,
                            "Thẻ đã tạo trên máy này nhưng chưa đồng bộ mã thẻ lên hệ thống chung.",
                            Toast.LENGTH_LONG).show());

            // Open the digital card as a dedicated screen so the barcode can use
            // the available screen width instead of being squeezed inside a dialog.
            Intent intent = new Intent(this, DigitalLibraryCardActivity.class);
            intent.putExtra(DigitalLibraryCardActivity.EXTRA_NAME, customer.getFullName());
            intent.putExtra(DigitalLibraryCardActivity.EXTRA_EMAIL, customer.getEmail());
            intent.putExtra(DigitalLibraryCardActivity.EXTRA_CARD_NUMBER, barcodeValue);
            intent.putExtra(DigitalLibraryCardActivity.EXTRA_ISSUE_DATE, savedCard.getIssueDate());
            startActivity(intent);
        } finally {
            cardDao.close();
        }
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