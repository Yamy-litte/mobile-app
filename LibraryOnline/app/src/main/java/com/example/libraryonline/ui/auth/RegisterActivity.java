package com.example.libraryonline.ui.auth;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;
import com.example.libraryonline.utils.InputValidator;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private EditText nameInput, emailInput, phoneInput;
    private Button submitButton;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        firestore = FirebaseFirestore.getInstance();
        nameInput = findViewById(R.id.editFullName);
        emailInput = findViewById(R.id.editRegisterEmail);
        phoneInput = findViewById(R.id.editPhone);
        submitButton = findViewById(R.id.buttonSubmitRegistration);
        submitButton.setOnClickListener(v -> submitRequest());
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
                                checkPendingRequests(fullName, email, phone);
                            })
                            .addOnFailureListener(e ->
                                    finishWithMessage("Không thể kiểm tra số điện thoại. Thử lại sau.", false));
                })
                .addOnFailureListener(e ->
                        finishWithMessage("Không thể kiểm tra email. Thử lại sau.", false));
    }

    private void checkPendingRequests(String fullName, String email, String phone) {
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
                                savePendingRequest(fullName, email, phone);
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

    private void savePendingRequest(String fullName, String email, String phone) {
        Map<String, Object> request = new HashMap<>();
        request.put("fullName", fullName);
        request.put("email", email);
        request.put("phone", phone);
        request.put("status", "PendingVerification");
        request.put("emailVerified", false);
        request.put("phoneVerified", false);
        request.put("rejectionReason", "");
        request.put("createdAt", FieldValue.serverTimestamp());

        firestore.collection("RegistrationRequests").add(request)
                .addOnSuccessListener(ref -> finishWithMessage(
                        "Đã lưu yêu cầu chờ xác minh. Email và số điện thoại chưa được xác minh; quản trị viên không được phê duyệt trước khi hoàn tất xác minh.",
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
