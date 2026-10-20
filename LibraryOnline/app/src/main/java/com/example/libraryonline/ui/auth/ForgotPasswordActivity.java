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
        submitButton.setText("Đang gửi yêu cầu...");
        Map<String, Object> request = new HashMap<>();
        request.put("fullName", fullName);
        request.put("birthDate", birthDateIso);
        request.put("phone", phone);
        request.put("email", email);
        request.put("status", "Pending");
        request.put("rejectionReason", "");
        request.put("createdAt", FieldValue.serverTimestamp());

        firestore.collection("PasswordResetRequests").add(request)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this,
                            "Đã gửi yêu cầu. Admin sẽ xác minh thông tin và duyệt trước khi Firebase gửi email đặt lại mật khẩu.",
                            Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(error -> {
                    submitButton.setEnabled(true);
                    submitButton.setText("Gửi yêu cầu quên mật khẩu");
                    Toast.makeText(this,
                            "Không gửi được yêu cầu. Kiểm tra kết nối hoặc Firestore Rules. " + error.getLocalizedMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
