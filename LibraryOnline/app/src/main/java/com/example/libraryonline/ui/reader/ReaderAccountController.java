package com.example.libraryonline.ui.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;

import com.example.libraryonline.R;
import com.example.libraryonline.ui.auth.ChangePasswordActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

/** Owns editable reader contact details and account utilities shown in the Account tab. */
public final class ReaderAccountController {
    private final Activity activity;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final EditText emailInput;
    private final EditText phoneInput;
    private String fullName = "Độc giả";
    private String dateOfBirth = "";
    private String email = "";
    private String phone = "";
    private String barcode = "";
    private String cardStatus = "";
    private String cardExpiryDate = "";

    public ReaderAccountController(Activity activity) {
        this.activity = activity;
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        emailInput = activity.findViewById(R.id.readerAccountEmailInput);
        phoneInput = activity.findViewById(R.id.readerAccountPhoneInput);

        activity.findViewById(R.id.readerAccountSave).setOnClickListener(v -> saveContactDetails());
        activity.findViewById(R.id.readerAccountDigitalCard).setOnClickListener(v -> showDigitalCard());
        activity.findViewById(R.id.readerAccountChangePassword)
                .setOnClickListener(v -> activity.startActivity(
                        new Intent(activity, ChangePasswordActivity.class)));
    }

    public void load() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) return;
        firestore.collection("Users").document(firebaseUser.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!isActive() || !doc.exists()) return;
                    fullName = read(doc, "fullName", "Độc giả");
                    dateOfBirth = read(doc, "dateOfBirth", "");
                    email = read(doc, "email", firebaseUser.getEmail());
                    phone = read(doc, "phone", "");
                    barcode = read(doc, "barcode", "");
                    cardStatus = read(doc, "cardStatus", "Chưa cập nhật");
                    cardExpiryDate = read(doc, "cardExpiryDate", "Chưa cập nhật");
                    emailInput.setText(email);
                    phoneInput.setText(phone);
                })
                .addOnFailureListener(error -> toast("Không tải được hồ sơ độc giả. Hãy thử tải lại."));
    }

    private void saveContactDetails() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            toast("Phiên đăng nhập đã hết. Hãy đăng nhập lại.");
            return;
        }
        String newEmail = emailInput.getText().toString().trim();
        String newPhone = phoneInput.getText().toString().trim();
        if (TextUtils.isEmpty(newEmail) || !Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            emailInput.setError("Email chưa hợp lệ");
            emailInput.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(newPhone) || newPhone.length() < 9 || newPhone.length() > 15) {
            phoneInput.setError("Số điện thoại phải có từ 9 đến 15 chữ số");
            phoneInput.requestFocus();
            return;
        }

        activity.findViewById(R.id.readerAccountSave).setEnabled(false);
        Map<String, Object> updates = new HashMap<>();
        updates.put("phone", newPhone);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        Runnable savePhoneAndFinish = () -> firestore.collection("Users").document(user.getUid())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    phone = newPhone;
                    email = newEmail;
                    toast("Đã lưu thông tin liên hệ.");
                    activity.findViewById(R.id.readerAccountSave).setEnabled(true);
                    load();
                })
                .addOnFailureListener(error -> {
                    activity.findViewById(R.id.readerAccountSave).setEnabled(true);
                    toast("Không lưu được hồ sơ. Vui lòng thử lại.");
                });

        if (!newEmail.equalsIgnoreCase(user.getEmail() == null ? "" : user.getEmail())) {
            user.updateEmail(newEmail)
                    .addOnSuccessListener(unused -> {
                        updates.put("email", newEmail);
                        savePhoneAndFinish.run();
                    })
                    .addOnFailureListener(error -> {
                        activity.findViewById(R.id.readerAccountSave).setEnabled(true);
                        toast("Không cập nhật được email. Vì lý do bảo mật, hãy đăng nhập lại rồi thử.");
                    });
        } else {
            updates.put("email", newEmail);
            savePhoneAndFinish.run();
        }
    }

    private void showDigitalCard() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            toast("Phiên đăng nhập đã hết. Hãy đăng nhập lại.");
            return;
        }
        String cardNumber = barcode.isEmpty() ? "Mã thẻ chưa được cấp" : barcode;
        String message = "HỌ VÀ TÊN\n" + fullName
                + "\n\nNGÀY SINH\n" + display(dateOfBirth)
                + "\n\nEMAIL\n" + display(email)
                + "\n\nSỐ ĐIỆN THOẠI\n" + display(phone)
                + "\n\nMÃ THẺ ĐỘC GIẢ\n" + cardNumber
                + "\n\nTRẠNG THÁI\n" + display(cardStatus)
                + "\n\nHẠN DÙNG\n" + display(cardExpiryDate)
                + "\n\nĐưa mã thẻ cho thủ thư đối chiếu tại quầy.";
        new AlertDialog.Builder(activity)
                .setTitle("Thẻ thư viện số")
                .setMessage(message)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private boolean isActive() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    private String read(DocumentSnapshot doc, String key, String fallback) {
        String value = doc.getString(key);
        return value == null || value.trim().isEmpty() ? (fallback == null ? "" : fallback) : value;
    }

    private String display(String value) {
        return value == null || value.trim().isEmpty() ? "Chưa cập nhật" : value;
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}
