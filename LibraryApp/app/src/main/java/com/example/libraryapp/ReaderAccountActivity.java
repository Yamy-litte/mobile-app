package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.LayoutInflater;
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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
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

        // A Firebase UID is stable across devices; a SQLite row ID is not.
        String cardCode = "LIB-" + uid.substring(0, Math.min(10, uid.length())).toUpperCase(Locale.ROOT);
        LibraryCardDao cardDao = new LibraryCardDao(this);
        try {
            LibraryCard savedCard = cardDao.getByCustomerId(customer.getId());
            String issueDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            if (savedCard == null) {
                savedCard = new LibraryCard();
                savedCard.setCustomerId(customer.getId());
                savedCard.setCardNumber(cardCode);
                savedCard.setStatus("active");
                savedCard.setIssueDate(issueDate);
                long insertedId = cardDao.insert(savedCard);
                if (insertedId <= 0) {
                    Toast.makeText(this, "Không thể tạo thẻ thư viện. Hãy thử lại.", Toast.LENGTH_LONG).show();
                    return;
                }
            } else if (!cardCode.equals(savedCard.getCardNumber())) {
                savedCard.setCardNumber(cardCode);
                savedCard.setStatus("active");
                cardDao.update(savedCard);
            }

            android.view.View card = LayoutInflater.from(this).inflate(R.layout.dialog_digital_library_card, null, false);
            ((TextView) card.findViewById(R.id.digitalCardName)).setText(customer.getFullName());
            ((TextView) card.findViewById(R.id.digitalCardEmail)).setText(customer.getEmail());
            ((TextView) card.findViewById(R.id.digitalCardNumber)).setText(cardCode);
            ((TextView) card.findViewById(R.id.digitalCardIssueDate)).setText("Ngày cấp: " + savedCard.getIssueDate());
            ImageView barcodeView = card.findViewById(R.id.digitalCardBarcode);
            try {
                barcodeView.setImageBitmap(createBarcode(cardCode));
                barcodeView.setContentDescription("Barcode thẻ độc giả " + cardCode);
            } catch (WriterException error) {
                Toast.makeText(this, "Không thể tạo barcode cho thẻ.", Toast.LENGTH_LONG).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Thẻ thư viện số")
                    .setView(card)
                    .setPositiveButton("Đóng", null)
                    .show();
        } finally {
            cardDao.close();
        }
    }

    private Bitmap createBarcode(String value) throws WriterException {
        BitMatrix matrix = new MultiFormatWriter().encode(
                value, BarcodeFormat.CODE_128, 900, 220);
        Bitmap bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.ARGB_8888);
        for (int x = 0; x < matrix.getWidth(); x++) {
            for (int y = 0; y < matrix.getHeight(); y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
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