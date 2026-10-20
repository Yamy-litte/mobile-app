package com.example.libraryonline.ui.admin;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AdminMainActivity extends AppCompatActivity {
    private static final String SECONDARY_APP_NAME = "LibraryOnlineSecondaryAuth";

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private FirebaseAuth secondaryAuth;
    private LinearLayout requestsContainer;
    private TextView pendingCount, emptyMessage;
    private ProgressBar progress;
    private ListenerRegistration requestsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_main);
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        FirebaseApp secondaryApp;
        try {
            secondaryApp = FirebaseApp.getInstance(SECONDARY_APP_NAME);
        } catch (IllegalStateException notInitialized) {
            secondaryApp = FirebaseApp.initializeApp(this,
                    FirebaseApp.getInstance().getOptions(), SECONDARY_APP_NAME);
        }
        secondaryAuth = FirebaseAuth.getInstance(secondaryApp);
        requestsContainer = findViewById(R.id.containerRegistrationRequests);
        pendingCount = findViewById(R.id.textPendingCount);
        emptyMessage = findViewById(R.id.textEmptyRequests);
        progress = findViewById(R.id.progressRequests);
        listenForRequests();
    }

    private void listenForRequests() {
        requestsListener = firestore.collection("RegistrationRequests")
                .addSnapshotListener((snapshot, error) -> {
                    progress.setVisibility(View.GONE);
                    if (error != null) {
                        emptyMessage.setVisibility(View.VISIBLE);
                        emptyMessage.setText("Không tải được yêu cầu. Kiểm tra kết nối và Firestore Rules dành cho Admin.");
                        Toast.makeText(this, "Lỗi tải yêu cầu: " + error.getCode(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snapshot == null) return;
                    requestsContainer.removeAllViews();
                    int count = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (!isPending(doc.getString("status"))) continue;
                        count++;
                        requestsContainer.addView(createRequestCard(doc));
                    }
                    pendingCount.setText("YÊU CẦU ĐANG CHỜ (" + count + ")");
                    emptyMessage.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
                    if (count == 0) emptyMessage.setText("Hiện chưa có yêu cầu đăng ký đang chờ duyệt.");
                });
    }

    private boolean isPending(String status) {
        return "Pending".equalsIgnoreCase(status) || "PendingVerification".equalsIgnoreCase(status);
    }

    private View createRequestCard(DocumentSnapshot doc) {
        int pad = dp(16);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundResource(R.drawable.bg_auth_card);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(12);
        card.setLayoutParams(cardParams);

        card.addView(makeText(value(doc, "fullName", "Chưa có họ tên"), 18, "#172554", true));
        TextView status = makeText("CHỜ DUYỆT", 11, "#B45309", true);
        status.setPadding(0, dp(4), 0, dp(10));
        card.addView(status);
        card.addView(makeText("Ngày sinh: " + value(doc, "birthDate",
                value(doc, "dateOfBirth", "Chưa cung cấp")), 14, "#475569", false));
        card.addView(makeText("Email: " + value(doc, "email", "Chưa cung cấp"), 14, "#475569", false));
        card.addView(makeText("Số điện thoại: " + value(doc, "phone", "Chưa cung cấp"), 14, "#475569", false));

        Object createdAt = doc.get("createdAt");
        String created = "Thời gian gửi: Chưa rõ";
        if (createdAt instanceof com.google.firebase.Timestamp) {
            Date date = ((com.google.firebase.Timestamp) createdAt).toDate();
            created = "Thời gian gửi: " + DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT, new Locale("vi", "VN")).format(date);
        }
        TextView time = makeText(created, 12, "#94A3B8", false);
        time.setPadding(0, dp(8), 0, dp(12));
        card.addView(time);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button reject = new Button(this);
        reject.setText("Từ chối");
        reject.setTextColor(android.graphics.Color.rgb(185, 28, 28));
        Button approve = new Button(this);
        approve.setText("Duyệt hồ sơ");
        approve.setTextColor(android.graphics.Color.WHITE);
        approve.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                android.graphics.Color.rgb(37, 99, 235)));
        LinearLayout.LayoutParams rejectParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        rejectParams.rightMargin = dp(8);
        actions.addView(reject, rejectParams);
        actions.addView(approve, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(actions);
        reject.setOnClickListener(v -> showRejectDialog(doc, reject, approve));
        approve.setOnClickListener(v -> approveRequest(doc, reject, approve));
        return card;
    }

    private void showRejectDialog(DocumentSnapshot doc, Button reject, Button approve) {
        EditText reasonInput = new EditText(this);
        reasonInput.setHint("Nhập lý do từ chối");
        reasonInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int pad = dp(20);
        reasonInput.setPadding(pad, dp(12), pad, dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Từ chối hồ sơ")
                .setMessage("Lý do sẽ được lưu cùng yêu cầu đăng ký. Email thông báo tự động chưa được hỗ trợ ở chế độ Spark.")
                .setView(reasonInput)
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Xác nhận từ chối", (dialog, which) -> {
                    String reason = reasonInput.getText().toString().trim();
                    if (reason.length() < 3 || reason.length() > 500) {
                        Toast.makeText(this, "Lý do từ chối phải từ 3 đến 500 ký tự.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    setCardActionsEnabled(reject, approve, false);
                    Map<String, Object> changes = new HashMap<>();
                    changes.put("status", "Rejected");
                    changes.put("rejectionReason", reason);
                    changes.put("reviewedBy", auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid());
                    changes.put("reviewedAt", FieldValue.serverTimestamp());
                    firestore.collection("RegistrationRequests").document(doc.getId()).update(changes)
                            .addOnSuccessListener(unused -> Toast.makeText(this,
                                    "Đã lưu kết quả từ chối. Ở gói Spark, hãy thông báo cho người đăng ký thủ công.",
                                    Toast.LENGTH_LONG).show())
                            .addOnFailureListener(error -> {
                                setCardActionsEnabled(reject, approve, true);
                                Toast.makeText(this, "Không thể từ chối: " + error.getLocalizedMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                }).show();
    }

    private void approveRequest(DocumentSnapshot doc, Button reject, Button approve) {
        String email = value(doc, "email", "").trim().toLowerCase(Locale.ROOT);
        String phone = value(doc, "phone", "").trim();
        String fullName = value(doc, "fullName", "").trim();
        if (email.isEmpty() || phone.isEmpty() || fullName.isEmpty()) {
            Toast.makeText(this, "Hồ sơ thiếu họ tên, email hoặc số điện thoại.", Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Duyệt hồ sơ thành viên")
                .setMessage("Tạo tài khoản độc giả cho " + email
                        + " và gửi email đặt mật khẩu. Tiếp tục?")
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Duyệt", (dialog, which) -> {
                    setCardActionsEnabled(reject, approve, false);
                    checkDuplicatesThenCreate(doc, fullName, email, phone, reject, approve);
                }).show();
    }

    private void checkDuplicatesThenCreate(DocumentSnapshot doc, String fullName, String email,
                                           String phone, Button reject, Button approve) {
        firestore.collection("Users").whereEqualTo("email", email).limit(1).get()
                .addOnSuccessListener(emailMatches -> {
                    if (!emailMatches.isEmpty()) {
                        failAction(reject, approve, "Email đã được sử dụng bởi một độc giả.");
                        return;
                    }
                    firestore.collection("Users").whereEqualTo("phone", phone).limit(1).get()
                            .addOnSuccessListener(phoneMatches -> {
                                if (!phoneMatches.isEmpty()) {
                                    failAction(reject, approve, "Số điện thoại đã được sử dụng.");
                                    return;
                                }
                                createApprovedAccount(doc, fullName, email, phone, reject, approve);
                            })
                            .addOnFailureListener(error -> failAction(reject, approve,
                                    "Không kiểm tra được số điện thoại: " + error.getLocalizedMessage()));
                })
                .addOnFailureListener(error -> failAction(reject, approve,
                        "Không kiểm tra được email: " + error.getLocalizedMessage()));
    }

    private void createApprovedAccount(DocumentSnapshot request, String fullName, String email,
                                       String phone, Button reject, Button approve) {
        secondaryAuth.createUserWithEmailAndPassword(email, createTemporaryPassword())
                .addOnSuccessListener(result -> {
                    FirebaseUser newUser = result.getUser();
                    if (newUser == null) {
                        failAction(reject, approve, "Firebase không trả về tài khoản vừa tạo.");
                        return;
                    }
                    String uid = newUser.getUid();
                    String barcode = "TV-" + uid.substring(0, Math.min(8, uid.length())).toUpperCase(Locale.ROOT);
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("userId", uid);
                    userData.put("fullName", fullName);
                    userData.put("dateOfBirth", value(request, "birthDate", ""));
                    userData.put("email", email);
                    userData.put("phone", phone);
                    userData.put("barcode", barcode);
                    userData.put("role", "Reader");
                    userData.put("cardStatus", "HoatDong");
                    userData.put("createdAt", FieldValue.serverTimestamp());
                    userData.put("updatedAt", FieldValue.serverTimestamp());

                    Map<String, Object> requestChanges = new HashMap<>();
                    requestChanges.put("status", "Approved");
                    requestChanges.put("reviewedBy", auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid());
                    requestChanges.put("reviewedAt", FieldValue.serverTimestamp());
                    requestChanges.put("userId", uid);
                    requestChanges.put("barcode", barcode);

                    WriteBatch batch = firestore.batch();
                    batch.set(firestore.collection("Users").document(uid), userData);
                    batch.update(firestore.collection("RegistrationRequests").document(request.getId()), requestChanges);
                    batch.commit()
                            .addOnSuccessListener(unused -> secondaryAuth.sendPasswordResetEmail(email)
                                    .addOnSuccessListener(ignored -> {
                                        Toast.makeText(this, "Đã duyệt hồ sơ, tạo tài khoản và gửi email đặt mật khẩu.",
                                                Toast.LENGTH_LONG).show();
                                    })
                                    .addOnFailureListener(error -> Toast.makeText(this,
                                            "Tài khoản đã tạo nhưng chưa gửi được email đặt mật khẩu. Người dùng có thể dùng Quên mật khẩu. "
                                                    + error.getLocalizedMessage(),
                                            Toast.LENGTH_LONG).show())
                            )
                            .addOnFailureListener(error -> {
                                FirebaseUser createdUser = secondaryAuth.getCurrentUser();
                                if (createdUser == null) {
                                    failAction(reject, approve, "Không lưu được hồ sơ: " + error.getLocalizedMessage());
                                } else {
                                    createdUser.delete().addOnCompleteListener(cleanup ->
                                            failAction(reject, approve,
                                                    "Không lưu được hồ sơ; đã thử dọn tài khoản vừa tạo. "
                                                            + error.getLocalizedMessage()));
                                }
                            });
                })
                .addOnFailureListener(error -> {
                    String message = error.getLocalizedMessage();
                    if (message != null && message.toLowerCase(Locale.ROOT).contains("already")) {
                        message = "Email đã tồn tại trong Firebase Authentication.";
                    }
                    failAction(reject, approve, "Không thể tạo tài khoản: " + message);
                });
    }

    private String createTemporaryPassword() {
        // A random initial secret is immediately replaced through Firebase's password-reset email.
        return java.util.UUID.randomUUID().toString().replace("-", "")
                + "aA1!";
    }

    private void failAction(Button reject, Button approve, String message) {
        setCardActionsEnabled(reject, approve, true);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setCardActionsEnabled(Button reject, Button approve, boolean enabled) {
        reject.setEnabled(enabled);
        approve.setEnabled(enabled);
        approve.setText(enabled ? "Duyệt hồ sơ" : "Đang xử lý...");
    }

    private String value(DocumentSnapshot doc, String key, String fallback) {
        String value = doc.getString(key);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private TextView makeText(String text, int sizeSp, String color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(android.graphics.Color.parseColor(color));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(4);
        view.setLayoutParams(params);
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        if (requestsListener != null) requestsListener.remove();
        super.onDestroy();
    }
}
