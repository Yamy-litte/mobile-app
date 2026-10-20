package com.example.libraryonline.ui.auth;

import android.app.DatePickerDialog;
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
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Date;

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
        // UX-level duplicate checks only. Firestore rules/backend must also enforce access
        // and uniqueness because two clients can submit at the same time.
        firestore.collection("Users").whereEqualTo("email", email).get()
                .addOnSuccessListener(usersByEmail -> {
                    if (!usersByEmail.isEmpty()) {
                        finishWithMessage("Email đã được sử dụng cho tài khoản độc giả.", false);
                        return;
                    }
                    firestore.collection("Users").whereEqualTo("phone", phone).get()
                            .addOnSuccessListener(usersByPhone -> {
                                if (!usersByPhone.isEmpty()) {
                                    finishWithMessage("Số điện thoại đã được sử dụng.", false);
                                    return;
                                }
                                checkPendingRequests(fullName, birthDateIso, email, phone);
                            })
                            .addOnFailureListener(e ->
                                    finishWithMessage("Không thể kiểm tra số điện thoại. Thử lại sau.", false));
                })
                .addOnFailureListener(e ->
                        finishWithMessage("Không thể kiểm tra email. Thử lại sau.", false));
    }

    private void checkPendingRequests(String fullName, String birthDate, String email, String phone) {
        firestore.collection("RegistrationRequests").whereEqualTo("email", email).get()
                .addOnSuccessListener(emailResults -> {
                    if (containsActiveRequest(emailResults)) {
                        finishWithMessage("Email đã có yêu cầu đăng ký đang xử lý.", false);
                        return;
                    }
                    firestore.collection("RegistrationRequests").whereEqualTo("phone", phone).get()
                            .addOnSuccessListener(phoneResults -> {
                                if (containsActiveRequest(phoneResults)) {
                                    finishWithMessage("Số điện thoại đã có yêu cầu đăng ký đang xử lý.", false);
                                    return;
                                }
                                savePendingRequest(fullName, birthDate, email, phone);
                            })
                            .addOnFailureListener(e ->
                                    finishWithMessage("Không thể kiểm tra số điện thoại. Thử lại sau.", false));
                })
                .addOnFailureListener(e ->
                        finishWithMessage("Không thể kiểm tra email. Thử lại sau.", false));
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

    private void savePendingRequest(String fullName, String birthDate, String email, String phone) {
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
                .addOnSuccessListener(ref -> finishWithMessage(
                        "Đã gửi yêu cầu đăng ký thẻ. Hồ sơ đang chờ xác minh và Admin duyệt; bạn chưa thể đăng nhập lúc này.",
                        true))
                .addOnFailureListener(e ->
                        finishWithMessage("Không gửi được yêu cầu. Kiểm tra quyền Firestore.", false));
    }

    private void setSubmitting(boolean submitting) {
        submitButton.setEnabled(!submitting);
        submitButton.setText(submitting ? "Đang gửi..." : "Gửi yêu cầu đăng ký");
    }

    private void finishWithMessage(String message, boolean success) {
        setSubmitting(false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (success) finish();
    }
}
