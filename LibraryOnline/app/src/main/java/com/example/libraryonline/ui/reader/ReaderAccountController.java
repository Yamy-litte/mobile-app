package com.example.libraryonline.ui.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ScrollView;
import java.util.Locale;

import com.example.libraryonline.R;
import com.example.libraryonline.utils.PasswordVisibilityToggle;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.FirebaseNetworkException;
import com.google.zxing.BarcodeFormat;
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
    private final View overview;
    private final View editor;
    private final View passwordPanel;
    private final View cardPanel;
    private final View settingsPanel;
    private final EditText oldPasswordInput;
    private final EditText newPasswordInput;
    private final EditText confirmPasswordInput;
    private final Button changePasswordButton;
    private boolean cardBrightnessApplied;
    private float originalBrightness;
    private boolean originallyKeepScreenOn;
    private int passwordGeneration;
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
        overview = activity.findViewById(R.id.readerAccountOverview);
        editor = activity.findViewById(R.id.readerAccountEditor);
        passwordPanel = activity.findViewById(R.id.readerAccountPassword);
        cardPanel = activity.findViewById(R.id.readerAccountCard);
        settingsPanel = activity.findViewById(R.id.readerAccountNotificationSettings);
        oldPasswordInput = activity.findViewById(R.id.readerPasswordOld);
        newPasswordInput = activity.findViewById(R.id.readerPasswordNew);
        confirmPasswordInput = activity.findViewById(R.id.readerPasswordConfirm);
        changePasswordButton = activity.findViewById(R.id.readerPasswordSave);
        PasswordVisibilityToggle.attach(oldPasswordInput);
        PasswordVisibilityToggle.attach(newPasswordInput);
        PasswordVisibilityToggle.attach(confirmPasswordInput);

        activity.findViewById(R.id.readerAccountPersonal).setOnClickListener(v -> showEditor());
        activity.findViewById(R.id.readerAccountProfileBack).setOnClickListener(v -> closeEditor());
        activity.findViewById(R.id.readerAccountBack).setOnClickListener(
                v -> ((com.google.android.material.bottomnavigation.BottomNavigationView)
                        activity.findViewById(R.id.readerBottomNavigation))
                        .setSelectedItemId(R.id.navReaderHome));
        activity.findViewById(R.id.readerAccountSave).setOnClickListener(v -> saveContactDetails());
        for (int id : new int[] {R.id.readerAccountDigitalCard, R.id.readerAccountEditCard}) {
            activity.findViewById(id).setOnClickListener(v -> showDigitalCard());
        }
        for (int id : new int[] {R.id.readerAccountChangePassword, R.id.readerAccountEditPassword}) {
            activity.findViewById(id).setOnClickListener(v -> showPassword());
        }
        activity.findViewById(R.id.readerPasswordBack).setOnClickListener(v -> closePassword());
        activity.findViewById(R.id.readerCardBack).setOnClickListener(v -> closeCard());
        changePasswordButton.setOnClickListener(v -> changePassword());
        activity.findViewById(R.id.readerAccountNotificationsSettings).setOnClickListener(v -> showPanel(settingsPanel));
        activity.findViewById(R.id.readerNotificationSettingsBack).setOnClickListener(v -> showPanel(overview));
        activity.findViewById(R.id.readerAccountAbout).setOnClickListener(v ->
                new AlertDialog.Builder(activity)
                        .setTitle("Về MyLibrary")
                        .setMessage("MyLibrary giúp độc giả tra cứu sách, quản lý thông tin tài khoản và thẻ thư viện.")
                        .setPositiveButton("Đóng", null)
                        .show());
        activity.findViewById(R.id.readerAccountLoadStatus).setOnClickListener(v -> load());
    }

    public void load() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) return;
        TextView statusView = activity.findViewById(R.id.readerAccountLoadStatus);
        statusView.setVisibility(View.VISIBLE);
        statusView.setText("Đang tải hồ sơ...");
        firestore.collection("Users").document(firebaseUser.getUid()).get()
                .addOnSuccessListener(doc -> {
                    FirebaseUser current = auth.getCurrentUser();
                    if (!isActive() || current == null || !firebaseUser.getUid().equals(current.getUid()))
                        return;
                    if (!doc.exists()) {
                        statusView.setText("Không tìm thấy hồ sơ. Chạm để thử lại.");
                        return;
                    }
                    fullName = read(doc, "fullName", "Độc giả");
                    dateOfBirth = read(doc, "dateOfBirth", "");
                    email = read(doc, "email", firebaseUser.getEmail());
                    phone = read(doc, "phone", "");
                    barcode = read(doc, "barcode", "");
                    cardStatus = read(doc, "cardStatus", "Chưa cập nhật");
                    cardExpiryDate = read(doc, "cardExpiryDate", "Chưa cập nhật");
                    ((TextView) activity.findViewById(R.id.readerAccountName)).setText(fullName);
                    ((TextView) activity.findViewById(R.id.readerAccountAvatar)).setText(
                            fullName.trim().isEmpty() ? "TV"
                                    : fullName.trim().substring(0, 1).toUpperCase(Locale.ROOT));
                    String details = (email == null ? "" : email.trim())
                            + (phone == null || phone.trim().isEmpty() ? "" : " | " + phone.trim());
                    ((TextView) activity.findViewById(R.id.readerAccountContactSummary))
                            .setText(details.trim().isEmpty() ? "Chưa cập nhật thông tin liên hệ" : details);
                    ((TextView) activity.findViewById(R.id.readerAccountFullName)).setText(fullName);
                    ((TextView) activity.findViewById(R.id.readerAccountBirthDate)).setText(display(dateOfBirth));
                    ((TextView) activity.findViewById(R.id.readerAccountStatus))
                            .setText("Trạng thái: " + display(cardStatus));
                    ((TextView) activity.findViewById(R.id.readerAccountBarcode))
                            .setText("Mã thẻ: " + display(barcode));
                    ((TextView) activity.findViewById(R.id.readerAccountExpiry))
                            .setText("Hạn dùng: " + display(cardExpiryDate));
                    emailInput.setText(email);
                    phoneInput.setText(phone);
                    statusView.setVisibility(View.GONE);
                })
                .addOnFailureListener(error -> {
                    if (!isActive()) return;
                    statusView.setText("Không tải được hồ sơ. Kiểm tra mạng, chạm để thử lại.");
                });
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
                    closeEditor();
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

    private void showPanel(View panel) {
        if (panel != cardPanel) restoreCardBrightness();
        overview.setVisibility(panel == overview ? View.VISIBLE : View.GONE);
        editor.setVisibility(panel == editor ? View.VISIBLE : View.GONE);
        passwordPanel.setVisibility(panel == passwordPanel ? View.VISIBLE : View.GONE);
        cardPanel.setVisibility(panel == cardPanel ? View.VISIBLE : View.GONE);
        settingsPanel.setVisibility(panel == settingsPanel ? View.VISIBLE : View.GONE);
        ((ScrollView) activity.findViewById(R.id.readerAccount)).scrollTo(0, 0);
    }

    private void showEditor() {
        showPanel(editor);
    }

    public boolean closeEditor() {
        if (editor.getVisibility() != View.VISIBLE) return false;
        showPanel(overview);
        return true;
    }

    private void showPassword() {
        clearPasswordInputs();
        showPanel(passwordPanel);
    }

    private boolean closePassword() {
        if (passwordPanel.getVisibility() != View.VISIBLE) return false;
        ++passwordGeneration;
        clearPasswordInputs();
        showPanel(overview);
        return true;
    }

    public boolean closeAccountSubscreen() {
        if (settingsPanel.getVisibility() == View.VISIBLE) { showPanel(overview); return true; }
        if (cardPanel.getVisibility() == View.VISIBLE) return closeCard();
        if (passwordPanel.getVisibility() == View.VISIBLE) return closePassword();
        return closeEditor();
    }

    public void onLeaveAccount() {
        ++passwordGeneration;
        clearPasswordInputs();
        restoreCardBrightness();
        showPanel(overview);
    }

    private void clearPasswordInputs() {
        oldPasswordInput.setText("");
        newPasswordInput.setText("");
        confirmPasswordInput.setText("");
        changePasswordButton.setEnabled(true);
        changePasswordButton.setText("Cập nhật mật khẩu");
        ((TextView) activity.findViewById(R.id.readerPasswordStatus)).setVisibility(View.GONE);
    }

    private void showPasswordError(String message) {
        TextView status = activity.findViewById(R.id.readerPasswordStatus);
        status.setText(message);
        status.setVisibility(View.VISIBLE);
    }

    private boolean sameAuthenticatedReader(String uid, int request) {
        FirebaseUser current = auth.getCurrentUser();
        return request == passwordGeneration
                && isActive()
                && current != null
                && uid.equals(current.getUid())
                && ((ReaderMainActivity) activity).isSessionReady();
    }

    private void setPasswordBusy(boolean busy) {
        changePasswordButton.setEnabled(!busy);
        changePasswordButton.setText(busy ? "Đang cập nhật..." : "Cập nhật mật khẩu");
    }

    private void changePassword() {
        if (!changePasswordButton.isEnabled()
                || !((ReaderMainActivity) activity).isSessionReady()) return;
        String oldPassword = oldPasswordInput.getText().toString();
        String newPassword = newPasswordInput.getText().toString();
        String confirmation = confirmPasswordInput.getText().toString();
        if (oldPassword.isEmpty()) {
            oldPasswordInput.setError("Vui lòng nhập mật khẩu hiện tại");
            oldPasswordInput.requestFocus();
            return;
        }
        if (newPassword.length() < 8) {
            newPasswordInput.setError("Mật khẩu mới phải có ít nhất 8 ký tự");
            newPasswordInput.requestFocus();
            return;
        }
        if (newPassword.equals(oldPassword)) {
            newPasswordInput.setError("Mật khẩu mới phải khác mật khẩu hiện tại");
            newPasswordInput.requestFocus();
            return;
        }
        if (!newPassword.equals(confirmation)) {
            confirmPasswordInput.setError("Mật khẩu nhập lại không khớp");
            confirmPasswordInput.requestFocus();
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            showPasswordError("Không xác định được email đăng nhập. Vui lòng đăng nhập lại.");
            return;
        }
        ((TextView) activity.findViewById(R.id.readerPasswordStatus)).setVisibility(View.GONE);
        setPasswordBusy(true);
        final int request = ++passwordGeneration;
        final String uid = user.getUid();
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPassword);
        // Reauthenticate against Firebase; never compare or store password in Firestore/SQLite.
        user.reauthenticate(credential).addOnCompleteListener(task -> {
            if (!sameAuthenticatedReader(uid, request)) return;
            oldPasswordInput.setText("");
            if (!task.isSuccessful()) {
                setPasswordBusy(false);
                Exception error = task.getException();
                if (error instanceof FirebaseAuthInvalidCredentialsException) {
                    oldPasswordInput.setError("Mật khẩu hiện tại không đúng");
                    showPasswordError("Mật khẩu hiện tại không đúng. Vui lòng thử lại.");
                } else if (error instanceof FirebaseNetworkException) {
                    showPasswordError("Không có kết nối mạng. Hãy kết nối Internet rồi thử lại.");
                } else {
                    showPasswordError("Không xác thực được mật khẩu hiện tại. Vui lòng thử lại.");
                }
                return;
            }
            user.updatePassword(newPassword).addOnCompleteListener(update -> {
                if (!sameAuthenticatedReader(uid, request)) return;
                setPasswordBusy(false);
                if (!update.isSuccessful()) {
                    if (update.getException() instanceof FirebaseNetworkException)
                        showPasswordError("Không có kết nối mạng. Mật khẩu chưa được cập nhật.");
                    else
                        showPasswordError("Không cập nhật được mật khẩu. Vui lòng thử lại.");
                    return;
                }
                clearPasswordInputs();
                showPanel(overview);
                toast("Đổi mật khẩu thành công.");
            });
        });
    }

    private void showDigitalCard() {
        if (!((ReaderMainActivity) activity).isSessionReady() || auth.getCurrentUser() == null) {
            toast("Phiên đăng nhập đã hết. Hãy đăng nhập lại.");
            return;
        }
        if (barcode == null || barcode.trim().isEmpty()) {
            toast("Thẻ chưa có mã barcode. Vui lòng liên hệ thủ thư.");
            return;
        }
        try {
            BitMatrix matrix = new Code128Writer().encode(barcode, BarcodeFormat.CODE_128, 900, 240);
            Bitmap bitmap = Bitmap.createBitmap(
                    matrix.getWidth(), matrix.getHeight(), Bitmap.Config.ARGB_8888);
            int dark = ContextCompat.getColor(activity, R.color.black);
            int light = ContextCompat.getColor(activity, R.color.white);
            int[] pixels = new int[matrix.getWidth() * matrix.getHeight()];
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    pixels[y * matrix.getWidth() + x] = matrix.get(x, y) ? dark : light;
                }
            }
            bitmap.setPixels(pixels, 0, matrix.getWidth(), 0, 0,
                    matrix.getWidth(), matrix.getHeight());
            ((ImageView) activity.findViewById(R.id.readerCardBarcodeImage)).setImageBitmap(bitmap);
        } catch (IllegalArgumentException error) {
            toast("Không tạo được barcode thẻ độc giả.");
            return;
        }
        ((TextView) activity.findViewById(R.id.readerCardName))
                .setText(display(fullName).toUpperCase(Locale.ROOT));
        ((TextView) activity.findViewById(R.id.readerCardBirthDate)).setText(display(dateOfBirth));
        ((TextView) activity.findViewById(R.id.readerCardPhone)).setText(display(phone));
        ((TextView) activity.findViewById(R.id.readerCardExpiryDate)).setText(display(cardExpiryDate));
        ((TextView) activity.findViewById(R.id.readerCardBarcodeNumber)).setText(barcode);
        showPanel(cardPanel);
        applyCardBrightness();
    }

    private boolean closeCard() {
        if (cardPanel.getVisibility() != View.VISIBLE) return false;
        restoreCardBrightness();
        showPanel(overview);
        return true;
    }

    private void applyCardBrightness() {
        if (cardBrightnessApplied) return;
        Window window = activity.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        originalBrightness = params.screenBrightness;
        originallyKeepScreenOn =
                (params.flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
        params.screenBrightness = 1.0f;
        window.setAttributes(params);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        cardBrightnessApplied = true;
    }

    public void applyCardBrightnessIfVisible() {
        if (cardPanel.getVisibility() == View.VISIBLE
                && ((ReaderMainActivity) activity).isSessionReady()) applyCardBrightness();
    }

    public void restoreCardBrightness() {
        if (!cardBrightnessApplied) return;
        Window window = activity.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.screenBrightness = originalBrightness;
        window.setAttributes(params);
        if (!originallyKeepScreenOn)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        cardBrightnessApplied = false;
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
