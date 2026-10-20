package com.example.libraryonline.ui.auth;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;
import com.example.libraryonline.utils.InputValidator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
    private Button submitButton, verifyEmailButton;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private String verifiedEmail = "";
    private boolean verificationInProgress = false;
    private String birthDateIso = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        nameInput = findViewById(R.id.editFullName);
        birthDateInput = findViewById(R.id.editBirthDate);
        emailInput = findViewById(R.id.editRegisterEmail);
        phoneInput = findViewById(R.id.editPhone);
        acceptTerms = findViewById(R.id.checkAcceptTerms);
        submitButton = findViewById(R.id.buttonSubmitRegistration);
        verifyEmailButton = findViewById(R.id.buttonVerifyEmail);
        submitButton.setEnabled(false);
        verifyEmailButton.setOnClickListener(v -> verifyEmail());
        emailInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String current = InputValidator.normalizeEmail(s.toString());
                if (!current.equals(verifiedEmail)) {
                    verifiedEmail = "";
                    submitButton.setEnabled(false);
                    verifyEmailButton.setText("Xác thực email");
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
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

    private void verifyEmail() {
        String email = InputValidator.normalizeEmail(emailInput.getText().toString());
        if (!InputValidator.isValidEmail(email)) {
            emailInput.setError("Nhập địa chỉ email hợp lệ trước khi xác thực");
            emailInput.requestFocus();
            return;
        }
        if (verificationInProgress) return;
        verificationInProgress = true;
        verifyEmailButton.setEnabled(false);

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null
                && !email.equalsIgnoreCase(currentUser.getEmail())) {
            currentUser.delete().addOnCompleteListener(deleteTask -> {
                auth.signOut();
                if (!deleteTask.isSuccessful()) {
                    verificationInProgress = false;
                    verifyEmailButton.setEnabled(true);
                    Toast.makeText(this, "Không thể đổi email xác thực. Hãy đăng xuất và thử lại.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                createVerificationAccount(email);
            });
            return;
        }
        if (currentUser == null) {
            createVerificationAccount(email);
            return;
        }
        currentUser.reload().addOnCompleteListener(reloadTask -> {
            FirebaseUser refreshed = auth.getCurrentUser();
            if (reloadTask.isSuccessful() && refreshed != null
                    && email.equalsIgnoreCase(refreshed.getEmail()) && refreshed.isEmailVerified()) {
                markEmailVerified(email);
            } else if (reloadTask.isSuccessful() && refreshed != null
                    && email.equalsIgnoreCase(refreshed.getEmail())) {
                refreshed.sendEmailVerification().addOnCompleteListener(sendTask -> {
                    verificationInProgress = false;
                    verifyEmailButton.setEnabled(true);
                    if (sendTask.isSuccessful()) {
                        Toast.makeText(this, "Đã gửi liên kết xác thực. Mở email, bấm liên kết rồi quay lại ứng dụng và bấm Xác thực email lần nữa.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Chưa gửi được email xác thực. Vui lòng thử lại.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                verificationInProgress = false;
                verifyEmailButton.setEnabled(true);
                Toast.makeText(this, "Không thể kiểm tra trạng thái email. Hãy thử lại.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void createVerificationAccount(String email) {
        auth.createUserWithEmailAndPassword(email, "123456")
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || auth.getCurrentUser() == null) {
                        verificationInProgress = false;
                        verifyEmailButton.setEnabled(true);
                        String message = task.getException() == null ? "" : task.getException().getLocalizedMessage();
                        Toast.makeText(this, "Không thể bắt đầu xác thực email. Email có thể đã có tài khoản. "
                                + message, Toast.LENGTH_LONG).show();
                        return;
                    }
                    auth.getCurrentUser().sendEmailVerification().addOnCompleteListener(sendTask -> {
                        verificationInProgress = false;
                        verifyEmailButton.setEnabled(true);
                        if (sendTask.isSuccessful()) {
                            Toast.makeText(this, "Đã gửi liên kết xác thực đến " + email
                                    + ". Mở email, bấm liên kết rồi quay lại ứng dụng và bấm Xác thực email lần nữa.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Không gửi được email xác thực. Vui lòng thử lại.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                });
    }

    private void markEmailVerified(String email) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            verificationInProgress = false;
            verifyEmailButton.setEnabled(true);
            return;
        }
        user.getIdToken(true).addOnCompleteListener(tokenTask -> {
            verificationInProgress = false;
            verifyEmailButton.setEnabled(true);
            FirebaseUser refreshed = auth.getCurrentUser();
            if (tokenTask.isSuccessful() && refreshed != null && refreshed.isEmailVerified()
                    && refreshed.getEmail() != null && email.equalsIgnoreCase(refreshed.getEmail())) {
                verifiedEmail = email;
                submitButton.setEnabled(true);
                verifyEmailButton.setText("Email đã xác thực");
                Toast.makeText(this, "Xác thực email thành công.", Toast.LENGTH_SHORT).show();
            } else {
                verifiedEmail = "";
                submitButton.setEnabled(false);
                Toast.makeText(this, "Chưa xác nhận được email đã xác thực. Hãy thử lại.", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser currentUser = auth == null ? null : auth.getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            currentUser.reload().addOnCompleteListener(task -> {
                FirebaseUser refreshed = auth.getCurrentUser();
                String entered = InputValidator.normalizeEmail(emailInput.getText().toString());
                if (task.isSuccessful() && refreshed != null && refreshed.isEmailVerified()
                        && refreshed.getEmail() != null
                        && entered.equalsIgnoreCase(refreshed.getEmail())) {
                    markEmailVerified(entered);
                }
            });
        }
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
        FirebaseUser verifiedUser = auth.getCurrentUser();
        if (!email.equals(verifiedEmail) || verifiedUser == null || !verifiedUser.isEmailVerified()
                || verifiedUser.getEmail() == null || !email.equalsIgnoreCase(verifiedUser.getEmail())) {
            verifiedEmail = "";
            submitButton.setEnabled(false);
            Toast.makeText(this, "Bạn cần xác thực đúng địa chỉ email đang nhập trước khi gửi yêu cầu.", Toast.LENGTH_LONG).show();
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

        DocumentReference requestRef = firestore.collection("RegistrationRequests").document();
        Map<String, Object> request = new HashMap<>();
        request.put("fullName", fullName);
        request.put("birthDate", birthDateIso);
        request.put("email", email);
        request.put("phone", phone);
        request.put("termsAccepted", true);
        request.put("termsAcceptedAt", FieldValue.serverTimestamp());
        request.put("status", "Pending");
        request.put("emailVerified", true);
        request.put("uid", verifiedUser.getUid());
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
                    auth.signOut();
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
        submitButton.setEnabled(!submitting && !verifiedEmail.isEmpty());
        submitButton.setText(submitting ? "Đang gửi..." : "Gửi yêu cầu đăng ký");
    }
}
