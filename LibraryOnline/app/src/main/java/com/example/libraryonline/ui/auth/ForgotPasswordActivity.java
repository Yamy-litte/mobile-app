package com.example.libraryonline.ui.auth;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;
import com.example.libraryonline.utils.InputValidator;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ForgotPasswordActivity extends AppCompatActivity {
    private EditText nameInput, birthDateInput, phoneInput, emailInput;
    private Button submitButton;
    private FirebaseFirestore firestore;
    private String birthDateIso = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        firestore = FirebaseFirestore.getInstance();
        nameInput = findViewById(R.id.editForgotFullName);
        birthDateInput = findViewById(R.id.editForgotBirthDate);
        phoneInput = findViewById(R.id.editForgotPhone);
        emailInput = findViewById(R.id.editForgotEmail);
        submitButton = findViewById(R.id.buttonSubmitPasswordResetRequest);

        birthDateInput.setOnClickListener(v -> chooseBirthDate());
        submitButton.setOnClickListener(v -> submitRequest());
    }

    private void chooseBirthDate() {
        Calendar initial = Calendar.getInstance();
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(year, month, day, 0, 0, 0);
            chosen.set(Calendar.MILLISECOND, 0);
            birthDateIso = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(chosen.getTime());
            birthDateInput.setText(new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"))
                    .format(chosen.getTime()));
        }, initial.get(Calendar.YEAR) - 18, initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.show();
    }

    private void submitRequest() {
        String fullName = nameInput.getText().toString().trim().replaceAll("\\s+", " ");
        String phone = InputValidator.normalizeVietnamPhone(phoneInput.getText().toString());
        String email = InputValidator.normalizeEmail(emailInput.getText().toString());

        if (fullName.length() < 2 || fullName.length() > 120) {
            nameInput.setError("Vui lòng nhập họ và tên hợp lệ");
            nameInput.requestFocus();
            return;
        }
        if (birthDateIso.isEmpty()) {
            birthDateInput.setError("Vui lòng chọn ngày sinh");
            birthDateInput.requestFocus();
            return;
        }
        if (!InputValidator.isValidVietnamPhone(phone)) {
            phoneInput.setError("Số điện thoại Việt Nam không đúng định dạng");
            phoneInput.requestFocus();
            return;
        }
        if (!InputValidator.isValidEmail(email)) {
            emailInput.setError("Email không đúng định dạng");
            emailInput.requestFocus();
            return;
        }

        submitButton.setEnabled(false);
        submitButton.setText("Đang xác minh thông tin...");

        // Chỉ tạo yêu cầu khi cả bốn trường khớp hồ sơ độc giả đã được duyệt.
        firestore.collection("Users")
                .whereEqualTo("email", email)
                .limit(2)
                .get()
                .addOnSuccessListener(matches -> {
                    if (matches.isEmpty()) {
                        rejectUnmatchedInformation();
                        return;
                    }

                    boolean matched = false;
                    for (com.google.firebase.firestore.DocumentSnapshot user : matches.getDocuments()) {
                        String savedName = normalizeName(user.getString("fullName"));
                        String savedBirthDate = user.getString("dateOfBirth");
                        String savedPhone = InputValidator.normalizeVietnamPhone(user.getString("phone"));
                        String savedEmail = InputValidator.normalizeEmail(user.getString("email"));
                        String role = user.getString("role");

                        if (fullName.equalsIgnoreCase(savedName)
                                && birthDateIso.equals(savedBirthDate)
                                && phone.equals(savedPhone)
                                && email.equals(savedEmail)
                                && "Reader".equalsIgnoreCase(role)) {
                            matched = true;
                            break;
                        }
                    }

                    if (!matched) {
                        rejectUnmatchedInformation();
                        return;
                    }

                    createPasswordResetRequest(fullName, birthDateIso, phone, email);
                })
                .addOnFailureListener(error -> {
                    submitButton.setEnabled(true);
                    submitButton.setText("Gửi yêu cầu quên mật khẩu");
                    Toast.makeText(this,
                            "Không thể xác minh thông tin lúc này. Vui lòng kiểm tra kết nối và thử lại.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private void rejectUnmatchedInformation() {
        submitButton.setEnabled(true);
        submitButton.setText("Gửi yêu cầu quên mật khẩu");
        Toast.makeText(this,
                "Thông tin không trùng khớp với hồ sơ độc giả trên hệ thống. Không gửi yêu cầu.",
                Toast.LENGTH_LONG).show();
    }

    private void createPasswordResetRequest(String fullName, String birthDate, String phone, String email) {
        submitButton.setText("Đang gửi yêu cầu...");
        Map<String, Object> request = new HashMap<>();
        request.put("fullName", fullName);
        request.put("birthDate", birthDate);
        request.put("phone", phone);
        request.put("email", email);
        request.put("status", "Pending");
        request.put("rejectionReason", "");
        request.put("createdAt", FieldValue.serverTimestamp());

        firestore.collection("PasswordResetRequests").add(request)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this,
                            "Thông tin đã khớp. Yêu cầu đã gửi cho Admin xét duyệt.",
                            Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(error -> {
                    submitButton.setEnabled(true);
                    submitButton.setText("Gửi yêu cầu quên mật khẩu");
                    Toast.makeText(this,
                            "Không gửi được yêu cầu. Kiểm tra kết nối hoặc Firestore Rules.",
                            Toast.LENGTH_LONG).show();
                });
    }
}
