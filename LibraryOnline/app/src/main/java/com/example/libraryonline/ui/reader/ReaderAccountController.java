package com.example.libraryonline.ui.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;

import com.example.libraryonline.R;
import com.example.libraryonline.ui.auth.ChangePasswordActivity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
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
                    ((TextView) activity.findViewById(R.id.readerAccountExpiry))
                            .setText("Hạn dùng: " + cardExpiryDate);
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
        if (barcode.trim().isEmpty()) {
            toast("Thẻ chưa có mã barcode. Vui lòng liên hệ thủ thư.");
            return;
        }

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(22);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundColor(android.graphics.Color.WHITE);

        TextView name = cardText(fullName, 22, true);
        name.setGravity(android.view.Gravity.CENTER);
        card.addView(name);
        TextView subtitle = cardText("THẺ THƯ VIỆN MYLIBRARY", 12, true);
        subtitle.setGravity(android.view.Gravity.CENTER);
        card.addView(subtitle);

        ImageView barcodeImage = new ImageView(activity);
        barcodeImage.setContentDescription("Mã barcode thẻ độc giả");
        barcodeImage.setAdjustViewBounds(true);
        barcodeImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            BitMatrix matrix = new Code128Writer().encode(barcode, BarcodeFormat.CODE_128, 900, 220);
            Bitmap bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.ARGB_8888);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y)
                            ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                }
            }
            barcodeImage.setImageBitmap(bitmap);
            LinearLayout.LayoutParams barcodeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(92));
            barcodeParams.topMargin = dp(12);
            card.addView(barcodeImage, barcodeParams);
        } catch (WriterException | IllegalArgumentException error) {
            toast("Không tạo được barcode thẻ độc giả.");
            return;
        }

        TextView number = cardText(barcode, 17, true);
        number.setGravity(android.view.Gravity.CENTER);
        card.addView(number);
        card.addView(cardText("Ngày sinh: " + display(dateOfBirth), 14, false));
        card.addView(cardText("Điện thoại: " + display(phone), 14, false));
        card.addView(cardText("Hạn dùng: " + display(cardExpiryDate), 14, false));
        card.addView(cardText("Trạng thái: " + display(cardStatus), 14, true));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Thẻ thư viện số")
                .setView(card)
                .setPositiveButton("Đóng", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.screenBrightness = 1.0f;
                window.setAttributes(params);
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
        dialog.setOnDismissListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                window.setAttributes(params);
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
        dialog.show();
    }

    private TextView cardText(String value, int sizeSp, boolean strong) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextColor(android.graphics.Color.BLACK);
        text.setTextSize(sizeSp);
        if (strong) text.setTypeface(null, android.graphics.Typeface.BOLD);
        text.setPadding(0, dp(7), 0, dp(7));
        return text;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
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
