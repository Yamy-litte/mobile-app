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
import com.example.libraryonline.utils.EmailSender;
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
import java.security.SecureRandom;
import java.util.Map;

public class AdminMainActivity extends AppCompatActivity {
    private static final String SECONDARY_APP_NAME = "LibraryOnlineSecondaryAuth";

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private FirebaseAuth secondaryAuth;
    private LinearLayout requestsContainer;
    private TextView pendingCount, emptyMessage;
    private ProgressBar progress;
    private ListenerRegistration requestsListener;\n    private LinearLayout resetRequestsContainer;\n    private TextView resetPendingCount, resetEmptyMessage;\n    private ListenerRegistration resetRequestsListener;

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
        resetRequestsContainer = findViewById(R.id.containerPasswordResetRequests);\n        resetPendingCount = findViewById(R.id.textPasswordResetPendingCount);\n        resetEmptyMessage = findViewById(R.id.textEmptyPasswordResetRequests);\n        listenForRequests();\n        listenForPasswordResetRequests();
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


    private void listenForPasswordResetRequests() {
        resetRequestsListener = firestore.collection("PasswordResetRequests")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        resetEmptyMessage.setVisibility(View.VISIBLE);
                        resetEmptyMessage.setText("Không tải được yêu cầu quên mật khẩu. Kiểm tra Firestore Rules.");
                        return;
                    }
                    if (snapshot == null) return;
                    resetRequestsContainer.removeAllViews();
                    int count = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (!"Pending".equalsIgnoreCase(doc.getString("status"))) continue;
                        count++;
                        resetRequestsContainer.addView(createPasswordResetCard(doc));
                    }
                    resetPendingCount.setText("YÊU CẦU QUÊN MẬT KHẨU ĐANG CHỜ (" + count + ")");
                    resetEmptyMessage.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
                    if (count == 0) resetEmptyMessage.setText("Hiện chưa có yêu cầu quên mật khẩu đang chờ duyệt.");
                });
    }

    private View createPasswordResetCard(DocumentSnapshot doc) {
        int pad = dp(16);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundResource(R.drawable.bg_auth_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        card.setLayoutParams(params);

        card.addView(makeText(value(doc, "fullName", "Chưa có họ tên"), 18, "#172554", true));
        card.addView(makeText("CHỜ XÁC MINH", 11, "#B45309", true));
        card.addView(makeText("Ngày sinh: " + value(doc, "birthDate", "Chưa cung cấp"), 14, "#475569", false));
        card.addView(makeText("Email: " + value(doc, "email", "Chưa cung cấp"), 14, "#475569", false));
        card.addView(makeText("Số điện thoại: " + value(doc, "phone", "Chưa cung cấp"), 14, "#475569", false));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button reject = new Button(this);
        reject.setText("Từ chối");
        reject.setTextColor(android.graphics.Color.rgb(185, 28, 28));
        Button approve = new Button(this);
        approve.setText("Duyệt & gửi email");
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
        reject.setOnClickListener(v -> showPasswordResetRejectDialog(doc, reject, approve));
        approve.setOnClickListener(v -> approvePasswordResetRequest(doc, reject, approve));
        return card;
    }

    private void approvePasswordResetRequest(DocumentSnapshot request, Button reject, Button approve) {
        String email = value(request, "email", "").trim().toLowerCase(Locale.ROOT);
        String name = value(request, "fullName", "").trim().replaceAll("\\s+", " ");
        String birthDate = value(request, "birthDate", "").trim();
        String phone = value(request, "phone", "").trim();
        if (email.isEmpty() || name.isEmpty() || birthDate.isEmpty() || phone.isEmpty()) {
            failResetAction(reject, approve, "Yêu cầu thiếu thông tin xác minh.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Duyệt yêu cầu quên mật khẩu")
                .setMessage("Chỉ duyệt nếu họ tên, ngày sinh và số điện thoại khớp hoàn toàn với hồ sơ độc giả. Firebase sẽ gửi email đặt lại mật khẩu đến " + email + ".")
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Xác minh và gửi", (dialog, which) -> {
                    setResetActionsEnabled(reject, approve, false);
                    firestore.collection("Users").whereEqualTo("email", email).limit(1).get()
                            .addOnSuccessListener(matches -> {
                                if (matches.isEmpty()) {
                                    failResetAction(reject, approve, "Không tìm thấy tài khoản độc giả khớp email.");
                                    return;
                                }
                                DocumentSnapshot user = matches.getDocuments().get(0);
                                String storedName = value(user, "fullName", "").trim().replaceAll("\\s+", " ");
                                String storedBirth = value(user, "dateOfBirth", value(user, "birthDate", "")).trim();
                                String storedPhone = value(user, "phone", "").trim();
                                if (!name.equalsIgnoreCase(storedName)
                                        || !birthDate.equals(storedBirth)
                                        || !phone.equals(storedPhone)
                                        || !"Reader".equalsIgnoreCase(value(user, "role", ""))) {
                                    failResetAction(reject, approve,
                                            "Thông tin không khớp hồ sơ độc giả. Chưa gửi email.");
                                    return;
                                }

                                secondaryAuth.sendPasswordResetEmail(email)
                                        .addOnSuccessListener(unused ->
                                                firestore.collection("PasswordResetRequests").document(request.getId())
                                                        .update(
                                                                "status", "Approved",
                                                                "rejectionReason", "",
                                                                "reviewedBy", auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid(),
                                                                "reviewedAt", FieldValue.serverTimestamp())
                                                        .addOnSuccessListener(done -> {
                                                            setResetActionsEnabled(reject, approve, true);
                                                            Toast.makeText(this,
                                                                    "Đã xác minh và yêu cầu Firebase gửi email đặt lại mật khẩu.",
                                                                    Toast.LENGTH_LONG).show();
                                                        })
                                                        .addOnFailureListener(error -> failResetAction(reject, approve,
                                                                "Firebase đã gửi email nhưng không cập nhật được trạng thái yêu cầu: "
                                                                        + error.getLocalizedMessage())))
                                        .addOnFailureListener(error -> failResetAction(reject, approve,
                                                "Firebase chưa gửi được email đặt lại mật khẩu: "
                                                        + error.getLocalizedMessage()));
                            })
                            .addOnFailureListener(error -> failResetAction(reject, approve,
                                    "Không xác minh được hồ sơ: " + error.getLocalizedMessage()));
                }).show();
    }

    private void showPasswordResetRejectDialog(DocumentSnapshot doc, Button reject, Button approve) {
        EditText reasonInput = new EditText(this);
        reasonInput.setHint("Nhập lý do từ chối");
        reasonInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int pad = dp(20);
        reasonInput.setPadding(pad, dp(12), pad, dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Từ chối yêu cầu quên mật khẩu")
                .setView(reasonInput)
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Xác nhận từ chối", (dialog, which) -> {
                    String reason = reasonInput.getText().toString().trim();
                    if (reason.length() < 3 || reason.length() > 500) {
                        Toast.makeText(this, "Lý do từ chối phải từ 3 đến 500 ký tự.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    setResetActionsEnabled(reject, approve, false);
                    firestore.collection("PasswordResetRequests").document(doc.getId())
                            .update(
                                    "status", "Rejected",
                                    "rejectionReason", reason,
                                    "reviewedBy", auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid(),
                                    "reviewedAt", FieldValue.serverTimestamp())
                            .addOnSuccessListener(unused -> {
                                setResetActionsEnabled(reject, approve, true);
                                Toast.makeText(this, "Đã từ chối yêu cầu quên mật khẩu.", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(error -> failResetAction(reject, approve,
                                    "Không thể từ chối yêu cầu: " + error.getLocalizedMessage()));
                }).show();
    }

    private void setResetActionsEnabled(Button reject, Button approve, boolean enabled) {
        reject.setEnabled(enabled);
        approve.setEnabled(enabled);
        approve.setText(enabled ? "Duyệt & gửi email" : "Đang xử lý...");
    }

    private void failResetAction(Button reject, Button approve, String message) {
        setResetActionsEnabled(reject, approve, true);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
\n    private boolean isPending(String status) {
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
                .setMessage("Lý do sẽ được lưu cùng yêu cầu đăng ký và gửi email thông báo.")
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
                            .addOnSuccessListener(unused -> {
                                String email = value(doc, "email", "").trim();
                                String fullName = value(doc, "fullName", "bạn");
                                EmailSender.send(email,
                                        "Library Online - Kết quả đăng ký",
                                        "Xin chào " + fullName + ",\\n\\n"
                                                + "Rất tiếc, yêu cầu đăng ký thư viện của bạn chưa được chấp thuận.\\n"
                                                + "Lý do: " + reason + "\\n\\n"
                                                + "Bạn có thể liên hệ thư viện để được hỗ trợ.\\n"
                                                + "Đây là email tự động, vui lòng không trả lời.",
                                        (success, message) -> {
                                            if (!success) Toast.makeText(this,
                                                    "Đã lưu từ chối nhưng gửi email thất bại: " + message,
                                                    Toast.LENGTH_LONG).show();
                                        });
                                Toast.makeText(this, "Đã lưu kết quả từ chối.", Toast.LENGTH_LONG).show();
                            })
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
                .setMessage("Duyệt hồ sơ cho " + email
                        + ". Tài khoản dùng mật khẩu tạm ban đầu và bắt buộc đổi ở lần đăng nhập đầu tiên. Tiếp tục?")
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
        String requestEmail = value(request, "email", "").trim();
        if (!email.equalsIgnoreCase(requestEmail)) {
            failAction(reject, approve, "Email hồ sơ không khớp.");
            return;
        }

        // Chỉ tạo tài khoản Firebase Auth sau khi admin đã bấm duyệt.
        // Mỗi độc giả nhận một mật khẩu tạm ngẫu nhiên, không dùng chung mật khẩu cố định.
        String temporaryPassword = generateTemporaryPassword();
        secondaryAuth.createUserWithEmailAndPassword(email, temporaryPassword)
                .addOnSuccessListener(result -> {
                    FirebaseUser createdUser = result.getUser();
                    if (createdUser == null) {
                        secondaryAuth.signOut();
                        failAction(reject, approve, "Firebase không trả về tài khoản vừa tạo.");
                        return;
                    }
                    String uid = createdUser.getUid();
                    String barcode = "TV-" + uid.substring(0, Math.min(8, uid.length()))
                            .toUpperCase(Locale.ROOT);

                    Map<String, Object> userData = new HashMap<>();
                    userData.put("userId", uid);
                    userData.put("fullName", fullName);
                    userData.put("dateOfBirth", value(request, "birthDate", ""));
                    userData.put("email", email);
                    userData.put("phone", phone);
                    userData.put("barcode", barcode);
                    userData.put("role", "Reader");
                    userData.put("cardStatus", "HoatDong");
                    userData.put("mustChangePassword", true);
                    userData.put("createdAt", FieldValue.serverTimestamp());
                    userData.put("updatedAt", FieldValue.serverTimestamp());

                    Map<String, Object> requestChanges = new HashMap<>();
                    requestChanges.put("status", "Approved");
                    requestChanges.put("reviewedBy",
                            auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid());
                    requestChanges.put("reviewedAt", FieldValue.serverTimestamp());
                    requestChanges.put("userId", uid);
                    requestChanges.put("barcode", barcode);

                    WriteBatch batch = firestore.batch();
                    batch.set(firestore.collection("Users").document(uid), userData);
                    batch.update(firestore.collection("RegistrationRequests").document(request.getId()),
                            requestChanges);
                    batch.commit()
                            .addOnSuccessListener(unused -> {
                                secondaryAuth.signOut();
                                setCardActionsEnabled(reject, approve, true);
                                EmailSender.send(email,
                                        "Library Online - Hồ sơ đã được duyệt",
                                        "Xin chào " + fullName + ",\\n\\n"
                                                + "Hồ sơ đăng ký thư viện của bạn đã được duyệt.\\n"
                                                + "Thông tin đăng nhập:\\n"
                                                + "Email: " + email + "\\n"
                                                + "Mật khẩu tạm: " + temporaryPassword + "\\n\\n"
                                                + "Vì lý do bảo mật, vui lòng đổi mật khẩu ngay ở lần đăng nhập đầu tiên.\\n\\n"
                                                + "Đây là email tự động, vui lòng không trả lời.",
                                        (success, message) -> {
                                            if (!success) Toast.makeText(this,
                                                    "Đã tạo tài khoản và duyệt hồ sơ nhưng gửi email thất bại: "
                                                            + message,
                                                    Toast.LENGTH_LONG).show();
                                        });
                                Toast.makeText(this,
                                        "Đã duyệt hồ sơ và tạo tài khoản Firebase. Mật khẩu tạm đã được gửi qua email.",
                                        Toast.LENGTH_LONG).show();
                            })
                            .addOnFailureListener(error -> {
                                // Nếu không lưu được hồ sơ Firestore, xóa tài khoản Auth vừa tạo
                                // để lần duyệt lại không bị kẹt vì email đã tồn tại.
                                createdUser.delete().addOnCompleteListener(deleteTask -> {
                                    secondaryAuth.signOut();
                                    failAction(reject, approve,
                                            "Không thể lưu hồ sơ đã duyệt: " + error.getLocalizedMessage()
                                                    + (deleteTask.isSuccessful() ? ""
                                                    : ". Không xóa được tài khoản Auth tạm; cần kiểm tra Firebase."));
                                });
                            });
                })
                .addOnFailureListener(error -> {
                    secondaryAuth.signOut();
                    String detail = error.getLocalizedMessage() == null ? "" : error.getLocalizedMessage();
                    failAction(reject, approve,
                            "Không thể tạo tài khoản Firebase sau khi duyệt. Email có thể đã có tài khoản Auth. "
                                    + detail);
                });
    }

    private String generateTemporaryPassword() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            password.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return password.toString();
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
        if (requestsListener != null) requestsListener.remove();\n        if (resetRequestsListener != null) resetRequestsListener.remove();
        super.onDestroy();
    }
}
