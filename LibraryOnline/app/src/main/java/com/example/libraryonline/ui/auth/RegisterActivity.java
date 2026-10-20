package com.example.libraryonline.ui.auth;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;
import com.example.libraryonline.utils.EmailSender;
import com.example.libraryonline.utils.InputValidator;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private EditText nameInput, birthDateInput, emailInput, phoneInput;
    private CheckBox acceptTerms;
    private Button submitButton;
    private FirebaseFirestore firestore;
    private String birthDateIso = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        firestore = FirebaseFirestore.getInstance();
        nameInput = findViewById(R.id.editFullName);
        birthDateInput = findViewById(R.id.editBirthDate);
        emailInput = findViewById(R.id.editRegisterEmail);
        phoneInput = findViewById(R.id.editPhone);
        acceptTerms = findViewById(R.id.checkAcceptTerms);
        submitButton = findViewById(R.id.buttonSubmitRegistration);
        submitButton.setEnabled(true);
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
        String fullName = nameInput.getText().toString().trim();
        String email = InputValidator.normalizeEmail(emailInput.getText().toString());
        String phone = InputValidator.normalizeVietnamPhone(phoneInput.getText().toString());

        if (!InputValidator.isNonEmpty(fullName)) {
            nameInput.setError("Vui lòng nhập họ và tên");
            nameInput.requestFocus();
            return;
        }
        if (birthDateIso.isEmpty()) {
            birthDateInput.setError("Vui lòng chọn ngày sinh");
            birthDateInput.requestFocus();
            return;
        }
        if (!InputValidator.isValidEmail(email)) {
            emailInput.setError("Email không đúng định dạng");
            emailInput.requestFocus();
            return;
        }
        if (!InputValidator.isValidVietnamPhone(phone)) {
            phoneInput.setError("Số điện thoại Việt Nam không đúng định dạng");
            phoneInput.requestFocus();
            return;
        }
        if (!acceptTerms.isChecked()) {
            acceptTerms.setError("Bạn cần đồng ý với điều khoản để tiếp tục");
            acceptTerms.requestFocus();
            return;
        }
        acceptTerms.setError(null);
        setSubmitting(true);
        // Chỉ lưu hồ sơ đăng ký ở bước này. Tài khoản Firebase Auth sẽ được tạo
        // sau khi quản trị viên duyệt hồ sơ.
        createRegistrationRequest(fullName, email, phone);
    }

    private void createRegistrationRequest(String fullName, String email, String phone) {
        DocumentReference requestRef = firestore.collection("RegistrationRequests").document();
        Map<String, Object> request = new HashMap<>();
        request.put("fullName", fullName);
        request.put("birthDate", birthDateIso);
        request.put("email", email);
        request.put("phone", phone);
        request.put("termsAccepted", true);
        request.put("termsAcceptedAt", FieldValue.serverTimestamp());
        request.put("status", "Pending");
        request.put("emailVerified", false);
        request.put("phoneVerified", false);
        request.put("rejectionReason", "");
        request.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> emailIndex = indexData(email, requestRef.getId());
        Map<String, Object> phoneIndex = indexData(phone, requestRef.getId());

        // Reserve email and phone in the same atomic batch as the request. The rules
        // permit index creation only once, preventing simultaneous duplicate submissions.
        WriteBatch batch = firestore.batch();
        batch.set(requestRef, request);
        batch.set(firestore.collection("RegistrationEmailIndex").document(email), emailIndex);
        batch.set(firestore.collection("RegistrationPhoneIndex").document(phone), phoneIndex);
        batch.commit()
                .addOnSuccessListener(unused -> {
                    EmailSender.send(email,
                            "Library Online - Đã nhận yêu cầu đăng ký",
                            "Xin chào " + fullName + ",\n\n"
                                    + "Thư viện đã nhận yêu cầu đăng ký tài khoản của bạn. "
                                    + "Hồ sơ đang ở trạng thái chờ quản trị viên duyệt. "
                                    + "Bạn sẽ nhận email khác khi có kết quả.\n\n"
                                    + "Đây là email tự động, vui lòng không trả lời.",
                            (success, message) -> {
                                if (!success) android.util.Log.w("RegisterActivity",
                                        "Không gửi được email thông báo: " + message);
                            });
                    Intent intent = new Intent(this, RegistrationSubmittedActivity.class);
                    intent.putExtra("fullName", fullName);
                    intent.putExtra("birthDateDisplay", birthDateInput.getText().toString());
                    intent.putExtra("phone", phone);
                    intent.putExtra("email", email);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(error -> {
                    setSubmitting(false);
                    Toast.makeText(this,
                            "Không gửi được yêu cầu. Email hoặc số điện thoại có thể đã được dùng, "
                                    + "hoặc Firestore Rules chưa được cập nhật. "
                                    + (error.getLocalizedMessage() == null ? "" : error.getLocalizedMessage()),
                            Toast.LENGTH_LONG).show();
                });
    }

    private Map<String, Object> indexData(String value, String requestId) {
        Map<String, Object> data = new HashMap<>();
        data.put("value", value);
        data.put("requestId", requestId);
        data.put("createdAt", FieldValue.serverTimestamp());
        return data;
    }

    private void setSubmitting(boolean submitting) {
        submitButton.setEnabled(!submitting);
        submitButton.setText(submitting ? "Đang gửi..." : "Gửi yêu cầu đăng ký");
    }
}
