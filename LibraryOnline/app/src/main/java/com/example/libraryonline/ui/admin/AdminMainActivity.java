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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.functions.FirebaseFunctions;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class AdminMainActivity extends AppCompatActivity {
    private FirebaseFirestore firestore;
    private FirebaseFunctions functions;
    private FirebaseAuth auth;
    private LinearLayout requestsContainer;
    private TextView pendingCount, emptyMessage;
    private ProgressBar progress;
    private ListenerRegistration requestsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_main);
        firestore = FirebaseFirestore.getInstance();
        functions = FirebaseFunctions.getInstance();
        auth = FirebaseAuth.getInstance();
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
                .setMessage("Lý do sẽ được lưu cùng yêu cầu đăng ký.")
                .setView(reasonInput)
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Xác nhận từ chối", (dialog, which) -> {
                    String reason = reasonInput.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Toast.makeText(this, "Vui lòng nhập lý do từ chối.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    setCardActionsEnabled(reject, approve, false);
                    HashMap<String, Object> data = new HashMap<>();
                    data.put("requestId", doc.getId());
                    data.put("reason", reason);
                    functions.getHttpsCallable("rejectRegistration").call(data)
                            .addOnSuccessListener(result -> {
                                boolean sent = false;
                                Object payload = result.getData();
                                if (payload instanceof java.util.Map) {
                                    Object sentValue = ((java.util.Map<?, ?>) payload).get("emailSent");
                                    sent = Boolean.TRUE.equals(sentValue);
                                }
                                Toast.makeText(this, sent
                                        ? "Đã từ chối hồ sơ và gửi email thông báo."
                                        : "Đã lưu từ chối. Chưa gửi được email, hãy kiểm tra cấu hình SMTP.",
                                        Toast.LENGTH_LONG).show();
                            })
                            .addOnFailureListener(error -> {
                                setCardActionsEnabled(reject, approve, true);
                                Toast.makeText(this, "Không thể từ chối: " + error.getLocalizedMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                }).show();
    }

    private void approveRequest(DocumentSnapshot doc, Button reject, Button approve) {
        new AlertDialog.Builder(this)
                .setTitle("Duyệt hồ sơ thành viên")
                .setMessage("Hệ thống sẽ tạo tài khoản độc giả và gửi email đặt mật khẩu đến "
                        + value(doc, "email", "email đăng ký") + ". Tiếp tục?")
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Duyệt", (dialog, which) -> {
                    setCardActionsEnabled(reject, approve, false);
                    HashMap<String, Object> data = new HashMap<>();
                    data.put("requestId", doc.getId());
                    functions.getHttpsCallable("approveRegistration").call(data)
                            .addOnSuccessListener(result -> {
                                String email = value(doc, "email", "");
                                auth.sendPasswordResetEmail(email)
                                        .addOnSuccessListener(unused -> Toast.makeText(this,
                                                "Đã tạo tài khoản và gửi email đặt mật khẩu.",
                                                Toast.LENGTH_LONG).show())
                                        .addOnFailureListener(error -> Toast.makeText(this,
                                                "Tài khoản đã tạo nhưng chưa gửi được email đặt mật khẩu. Dùng chức năng Quên mật khẩu để gửi lại. "
                                                        + error.getLocalizedMessage(),
                                                Toast.LENGTH_LONG).show());
                            })
                            .addOnFailureListener(error -> {
                                setCardActionsEnabled(reject, approve, true);
                                Toast.makeText(this, "Không thể duyệt hồ sơ: " + error.getLocalizedMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                }).show();
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