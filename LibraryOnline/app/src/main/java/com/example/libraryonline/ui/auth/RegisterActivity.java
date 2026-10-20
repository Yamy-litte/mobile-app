package com.example.libraryonline.ui.auth;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
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
import java.security.SecureRandom;

public class RegisterActivity extends AppCompatActivity {
    private EditText nameInput, birthDateInput, emailInput, phoneInput, otpInput;
    private CheckBox acceptTerms;
    private Button submitButton, sendOtpButton;
    private TextView otpStatus;
    private FirebaseFirestore firestore;
    private String birthDateIso = "";
    private String expectedOtp = "";
    private String otpEmail = "";
    private long otpExpiresAt = 0L;
    private boolean emailVerified = false;
    private CountDownTimer resendTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        firestore = FirebaseFirestore.getInstance();
        nameInput = findViewById(R.id.editFullName);
        birthDateInput = findViewById(R.id.editBirthDate);
        emailInput = findViewById(R.id.editRegisterEmail);
        phoneInput = findViewById(R.id.editPhone);
        otpInput = findViewById(R.id.editRegisterOtp);
        otpStatus = findViewById(R.id.textOtpStatus);
        sendOtpButton = findViewById(R.id.buttonSendOtp);
        acceptTerms = findViewById(R.id.checkAcceptTerms);
        submitButton = findViewById(R.id.buttonSubmitRegistration);
        submitButton.setEnabled(true);

        birthDateInput.setOnClickListener(v -> chooseBirthDate());
        sendOtpButton.setOnClickListener(v -> sendRegistrationOtp());
        submitButton.setOnClickListener(v -> submitRequest());
        emailInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String currentEmail = InputValidator.normalizeEmail(s.toString());
                if (!otpEmail.isEmpty() && !otpEmail.equals(currentEmail)) {
                    expectedOtp = "";
                    otpExpiresAt = 0L;
                    emailVerified = false;
                    otpInput.setText("");
                    otpStatus.setText("Email đã thay đổi. Vui lòng gửi OTP mới.");
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
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

    private void sendRegistrationOtp() {
        String email = InputValidator.normalizeEmail(emailInput.getText().toString());
        if (!InputValidator.isValidEmail(email)) {
            emailInput.setError("Vui lòng nhập địa chỉ email/Gmail hợp lệ");
            emailInput.requestFocus();
            return;
        }
        if (resendTimer != null) {
            Toast.makeText(this, "Vui lòng đợi hết thời gian đếm ngược trước khi gửi lại OTP.", Toast.LENGTH_SHORT).show();
            return;
        }

        sendOtpButton.setEnabled(false);
        sendOtpButton.setText("Đang gửi...");
        String fullName = nameInput.getText().toString().trim();
        String otp = String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1_000_000));
        EmailSender.send(email,
                "Library Online - Mã OTP xác minh email",
                "Xin chào " + (fullName.isEmpty() ? "bạn" : fullName) + ",\n\n"
                        + "Mã OTP xác minh email của bạn là: " + otp + "\n"
                        + "Mã có hiệu lực trong 60 giây và chỉ dùng một lần.\n\n"
                        + "Nếu bạn không thực hiện đăng ký, hãy bỏ qua email này.",
                (success, message) -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!success) {
                        sendOtpButton.setEnabled(true);
                        sendOtpButton.setText("Gửi OTP");
                        otpStatus.setText("Không gửi được email. Hãy kiểm tra địa chỉ Gmail, kết nối mạng và thư mục Spam.");
                        new AlertDialog.Builder(this)
                                .setTitle("Không gửi được mã OTP")
                                .setMessage("Vui lòng kiểm tra địa chỉ Gmail đã nhập có chính xác không, kiểm tra kết nối mạng và thư mục Spam/Thư rác, sau đó thử gửi lại.\n\nChi tiết: " + message)
                                .setPositiveButton("Đã hiểu", (dialog, which) -> dialog.dismiss())
                                .show();
                        return;
                    }
                    expectedOtp = otp;
                    otpEmail = email;
                    otpExpiresAt = System.currentTimeMillis() + 60_000L;
                    emailVerified = false;
                    otpInput.setText("");
                    otpStatus.setText("Đã gửi mã đến " + email + ". Mã có hiệu lực 60 giây.");
                    startResendCountdown();
                    Toast.makeText(this, "Đã gửi mã OTP đến email.", Toast.LENGTH_SHORT).show();
                });
    }

    private void startResendCountdown() {
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        sendOtpButton.setEnabled(false);
        sendOtpButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.GRAY));
        resendTimer = new CountDownTimer(60_000L, 1_000L) {
            @Override public void onTick(long millisUntilFinished) {
                sendOtpButton.setText("Gửi lại (" + ((millisUntilFinished + 999) / 1000) + "s)");
            }
            @Override public void onFinish() {
                resendTimer = null;
                sendOtpButton.setEnabled(true);
                sendOtpButton.setText("Gửi OTP");
                sendOtpButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(RegisterActivity.this, R.color.library_blue)));
                if (!emailVerified && System.currentTimeMillis() >= otpExpiresAt) {
                    expectedOtp = "";
                    otpStatus.setText("Mã OTP đã hết hạn. Nhấn Gửi OTP để nhận mã mới.");
                }
            }
        }.start();
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
        if (!email.equals(otpEmail) || expectedOtp.isEmpty()) {
            otpInput.setError("Vui lòng gửi OTP đến email này");
            otpInput.requestFocus();
            return;
        }
        if (System.currentTimeMillis() > otpExpiresAt) {
            emailVerified = false;
            expectedOtp = "";
            otpInput.setError("Mã OTP đã hết hạn. Vui lòng gửi lại.");
            otpStatus.setText("Mã OTP đã hết hạn. Nhấn Gửi OTP để nhận mã mới.");
            otpInput.requestFocus();
            return;
        }
        if (!emailVerified) {
            String enteredOtp = otpInput.getText().toString().trim();
            if (!enteredOtp.matches("[0-9]{6}") || !enteredOtp.equals(expectedOtp)) {
                otpInput.setError("Mã OTP không đúng. Vui lòng kiểm tra lại email.");
                otpInput.requestFocus();
                return;
            }
            emailVerified = true;
            otpStatus.setText("Email đã được xác minh.");
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
        request.put("emailVerified", true);
        request.put("phoneVerified", false);
        request.put("rejectionReason", "");
        request.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> emailIndex = indexData(email, requestRef.getId());
        Map<String, Object> phoneIndex = indexData(phone, requestRef.getId());

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

    @Override
    protected void onDestroy() {
        if (resendTimer != null) {
            resendTimer.cancel();
            resendTimer = null;
        }
        super.onDestroy();
    }
}