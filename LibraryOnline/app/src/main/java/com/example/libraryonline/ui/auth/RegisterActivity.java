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
import com.example.libraryonline.utils.InputValidator;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

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
        // These reads require matching Firestore rules. Do not make private user records
        // publicly readable just to make client-side duplicate checks work.
        firestore.collection("Users").whereEqualTo("email", email).get()
                .addOnSuccessListener(usersByEmail -> {
                    if (!usersByEmail.isEmpty()) {
                        finishWithMessage("Email đã được sử dụng cho tài khoản độc giả.");
                        return;
                    }
                    firestore.collection("Users").whereEqualTo("phone", phone).get()
                            .addOnSuccessListener(usersByPhone -> {
                                if (!usersByPhone.isEmpty()) {
                                    finishWithMessage("Số điện thoại đã được sử dụng.");
                                    return;
                                }
                                checkPendingRequests(fullName, birthDateIso,
                                        birthDateInput.getText().toString(), email, phone);
                            })
                            .addOnFailureListener(e -> showFirestoreError("Kiểm tra số điện thoại", e));
                })
                .addOnFailureListener(e -> showFirestoreError("Kiểm tra email trong danh sách độc giả", e));
    }

    private void checkPendingRequests(String fullName, String birthDate, String birthDateDisplay,
                                      String email, String phone) {
        firestore.collection("RegistrationRequests").whereEqualTo("email", email).get()
                .addOnSuccessListener(emailResults -> {
                    if (containsActiveRequest(emailResults)) {
                        finishWithMessage("Email đã có yêu cầu đăng ký đang xử lý.");
                        return;
                    }
                    firestore.collection("RegistrationRequests").whereEqualTo("phone", phone).get()
                            .addOnSuccessListener(phoneResults -> {
                                if (containsActiveRequest(phoneResults)) {
                                    finishWithMessage("Số điện thoại đã có yêu cầu đăng ký đang xử lý.");
                                    return;
                                }
                                savePendingRequest(fullName, birthDate, birthDateDisplay, email, phone);
                            })
                            .addOnFailureListener(e -> showFirestoreError("Kiểm tra yêu cầu theo số điện thoại", e));
                })
                .addOnFailureListener(e -> showFirestoreError("Kiểm tra yêu cầu theo email", e));
    }

    private boolean containsActiveRequest(QuerySnapshot results) {
        for (DocumentSnapshot document : results.getDocuments()) {
            String status = document.getString("status");
            if (!"Rejected".equalsIgnoreCase(status)
                    && !"TuChoi".equalsIgnoreCase(status)
                    && !"Cancelled".equalsIgnoreCase(status)) {
                return true;
            }
        }
        return false;
    }

    private void savePendingRequest(String fullName, String birthDate, String birthDateDisplay,
                                    String email, String phone) {
        Map<String, Object> request = new HashMap<>();
        request.put("fullName", fullName);
        request.put("birthDate", birthDate);
        request.put("email", email);
        request.put("phone", phone);
        request.put("termsAccepted", true);
        request.put("termsAcceptedAt", FieldValue.serverTimestamp());
        request.put("status", "PendingVerification");
        request.put("emailVerified", false);
        request.put("phoneVerified", false);
        request.put("rejectionReason", "");
        request.put("createdAt", FieldValue.serverTimestamp());

        firestore.collection("RegistrationRequests").add(request)
                .addOnSuccessListener(ref -> {
                    Intent intent = new Intent(this, RegistrationSubmittedActivity.class);
                    intent.putExtra("fullName", fullName);
                    intent.putExtra("birthDateDisplay", birthDateDisplay);
                    intent.putExtra("phone", phone);
                    intent.putExtra("email", email);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> showFirestoreError("Gửi yêu cầu đăng ký", e));
    }

    private void showFirestoreError(String operation, Exception error) {
        setSubmitting(false);
        String detail = error.getMessage();
        String code = error instanceof FirebaseFirestoreException
                ? ((FirebaseFirestoreException) error).getCode().name()
                : error.getClass().getSimpleName();
        android.util.Log.e("RegisterActivity", operation + " failed (" + code + ")", error);

        String message;
        if ("PERMISSION_DENIED".equals(code)) {
            message = operation + " thất bại: Firestore từ chối quyền truy cập (PERMISSION_DENIED). Cần kiểm tra Firestore Rules hoặc chuyển việc kiểm tra trùng email sang backend an toàn.";
        } else if ("UNAVAILABLE".equals(code) || "DEADLINE_EXCEEDED".equals(code)) {
            message = operation + " thất bại do kết nối Firebase không khả dụng. Hãy kiểm tra mạng rồi thử lại.";
        } else {
            message = operation + " thất bại (" + code + "). " + (detail == null ? "Vui lòng kiểm tra cấu hình Firebase." : detail);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setSubmitting(boolean submitting) {
        submitButton.setEnabled(!submitting);
        submitButton.setText(submitting ? "Đang gửi..." : "Gửi yêu cầu đăng ký");
    }

    private void finishWithMessage(String message) {
        setSubmitting(false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
