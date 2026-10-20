package com.example.libraryonline.ui.auth;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;
import com.example.libraryonline.utils.InputValidator;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

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
        String email = emailInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();

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

        submitButton.setEnabled(false);
        firestore.collection("RegistrationRequests")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(emailResults -> {
                    if (!emailResults.isEmpty()) {
                        finishWithMessage("Email đã có yêu cầu đăng ký.", false);
                        return;
                    }
                    firestore.collection("RegistrationRequests")
                            .whereEqualTo("phone", phone)
                            .get()
                            .addOnSuccessListener(phoneResults -> {
                                if (!phoneResults.isEmpty()) {
                                    finishWithMessage("Số điện thoại đã có yêu cầu đăng ký.", false);
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
                        "Đã lưu yêu cầu ở trạng thái chờ xác minh. Email và số điện thoại chưa được xác minh. Không được phê duyệt yêu cầu cho đến khi hoàn tất xác minh.",
                        true))
                .addOnFailureListener(e ->
                        finishWithMessage("Không gửi được yêu cầu. Kiểm tra quyền Firestore.", false));
    }

    private void finishWithMessage(String message, boolean success) {
        submitButton.setEnabled(true);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (success) finish();
    }
}
