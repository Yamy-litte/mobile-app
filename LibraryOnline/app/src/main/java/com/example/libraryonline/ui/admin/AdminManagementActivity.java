package com.example.libraryonline.ui.admin;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.libraryonline.R;
import com.example.libraryonline.data.local.dao.SystemConfigDao;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.model.SystemConfig;
import com.example.libraryonline.ui.auth.SessionActivity;
import com.example.libraryonline.data.session.SessionProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Admin-only tools. Cloud-backed user/config changes are guarded by Firestore Rules. */
public final class AdminManagementActivity extends SessionActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private FirebaseFirestore firestore;
    private String adminUid;
    private LinearLayout content;
    private ListenerRegistration usersListener;
    private java.util.List<DocumentSnapshot> users = new java.util.ArrayList<>();
    private String searchText = "";
    private EditText searchInput;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_admin_management);
        firestore = FirebaseFirestore.getInstance();
        content = findViewById(R.id.adminManagementContent);
        findViewById(R.id.adminManagementBack).setOnClickListener(v -> finish());
        findViewById(R.id.adminManagementReaders).setOnClickListener(v -> showReaders());
        findViewById(R.id.adminManagementConfig).setOnClickListener(v -> showConfig());
        findViewById(R.id.adminManagementReports).setOnClickListener(v -> showReports());
        findViewById(R.id.adminManagementProfile).setOnClickListener(v -> showProfile());
    }

    @Override protected SessionProfile.Role expectedRole() { return SessionProfile.Role.ADMIN; }

    @Override protected void onSessionAuthorized(SessionProfile profile) {
        adminUid = profile.uid;
        showReaders();
    }

    @Override protected void onSessionStopped() {
        if (usersListener != null) { usersListener.remove(); usersListener = null; }
        io.shutdownNow();
        content.removeAllViews();
    }

    private void showReaders() {
        if (!isSessionReady()) return;
        clearContent("Quản lý độc giả", "Tìm theo họ tên hoặc số điện thoại. Thay đổi thẻ được lưu lên Firestore.");
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Tên độc giả hoặc số điện thoại");
        searchInput.setText(searchText);
        content.addView(searchInput, matchWrap());
        Button search = button("Tìm kiếm");
        search.setOnClickListener(v -> {
            searchText = searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
            renderUsers();
        });
        content.addView(search, matchWrap());
        TextView note = text("Trạng thái thẻ và hạn dùng lấy trực tiếp từ hồ sơ cloud.", 13, false);
        content.addView(note, matchWrap());
        if (usersListener != null) usersListener.remove();
        usersListener = firestore.collection("Users").whereEqualTo("role", "Reader")
                .addSnapshotListener((snapshot, error) -> {
                    if (!isSessionReady()) return;
                    if (error != null) {
                        content.addView(text("Không tải được độc giả: " + safe(error.getMessage()), 14, false), matchWrap());
                        return;
                    }
                    if (snapshot == null) return;
                    users = snapshot.getDocuments();
                    renderUsers();
                });
    }

    private void renderUsers() {
        if (searchInput == null || !isSessionReady()) return;
        String query = searchText;
        // Keep the title/search controls; rebuild only result cards.
        while (content.getChildCount() > 3) content.removeViewAt(3);
        int count = 0;
        for (DocumentSnapshot doc : users) {
            String name = value(doc, "fullName");
            String phone = value(doc, "phone");
            if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(query)
                    && !phone.toLowerCase(Locale.ROOT).contains(query)) continue;
            count++;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackgroundResource(R.drawable.bg_admin_card);
            LinearLayout.LayoutParams cardParams = matchWrap();
            cardParams.bottomMargin = dp(10);
            content.addView(card, cardParams);
            card.addView(text(name.isEmpty() ? "Độc giả" : name, 17, true), matchWrap());
            card.addView(text("SĐT: " + (phone.isEmpty() ? "Chưa cập nhật" : phone), 14, false), matchWrap());
            card.addView(text("Email: " + value(doc, "email"), 13, false), matchWrap());
            card.addView(text("Mã thẻ: " + value(doc, "barcode"), 13, false), matchWrap());
            card.addView(text("Trạng thái: " + value(doc, "cardStatus") + "   •   Hạn: " + value(doc, "cardExpiryDate"), 13, false), matchWrap());
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button details = button("Lịch sử");
            details.setOnClickListener(v -> showReaderHistory(doc));
            actions.addView(details, new LinearLayout.LayoutParams(0, dp(48), 1));
            String status = value(doc, "cardStatus");
            Button lock = button("BiKhoa".equals(status) ? "Mở khóa" : "Khóa thẻ");
            lock.setOnClickListener(v -> updateCardStatus(doc, "BiKhoa".equals(status) ? "HoatDong" : "BiKhoa"));
            actions.addView(lock, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button renew = button("Gia hạn 1 năm");
            renew.setOnClickListener(v -> renewCard(doc));
            actions.addView(renew, new LinearLayout.LayoutParams(0, dp(48), 1));
            card.addView(actions, matchWrap());
        }
        if (count == 0) content.addView(text("Không tìm thấy độc giả phù hợp.", 14, false), matchWrap());
    }

    private void updateCardStatus(DocumentSnapshot doc, String status) {
        if (!isSessionReady()) return;
        privateSupportDialog().setTitle(status.equals("BiKhoa") ? "Khóa thẻ độc giả" : "Mở khóa thẻ")
                .setMessage("Cập nhật trạng thái thẻ của " + value(doc, "fullName") + "?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận", (d, w) -> {
                    Map<String,Object> updates = new HashMap<>();
                    updates.put("cardStatus", status);
                    updates.put("updatedAt", FieldValue.serverTimestamp());
                    doc.getReference().update(updates).addOnSuccessListener(x -> toast("Đã cập nhật trạng thái thẻ."))
                            .addOnFailureListener(e -> toast("Không cập nhật được: " + safe(e.getMessage())));
                }).show();
    }

    private void renewCard(DocumentSnapshot doc) {
        Calendar expiry = Calendar.getInstance();
        String current = value(doc, "cardExpiryDate");
        try {
            if (!current.isEmpty()) {
                Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(current);
                if (parsed != null && parsed.after(new Date())) expiry.setTime(parsed);
            }
        } catch (Exception ignored) {}
        expiry.add(Calendar.YEAR, 1);
        String newExpiry = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(expiry.getTime());
        privateSupportDialog().setTitle("Gia hạn thẻ")
                .setMessage("Gia hạn thẻ của " + value(doc, "fullName") + " đến " + newExpiry + "?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Gia hạn", (d,w) -> {
                    Map<String,Object> updates = new HashMap<>();
                    updates.put("cardExpiryDate", newExpiry);
                    updates.put("cardStatus", "HoatDong");
                    updates.put("updatedAt", FieldValue.serverTimestamp());
                    doc.getReference().update(updates).addOnSuccessListener(x -> toast("Đã gia hạn thẻ đến " + newExpiry))
                            .addOnFailureListener(e -> toast("Không gia hạn được: " + safe(e.getMessage())));
                }).show();
    }

    private void showReaderHistory(DocumentSnapshot doc) {
        String uid = doc.getId();
        io.execute(() -> {
            int receipts = 0, activeLoans = 0, unpaid = 0;
            try (DatabaseHelper helper = new DatabaseHelper(getApplicationContext())) {
                android.database.sqlite.SQLiteDatabase db = helper.getReadableDatabase();
                receipts = scalar(db, "SELECT COUNT(*) FROM LoanReceipts WHERE userId=?", uid);
                activeLoans = scalar(db, "SELECT COUNT(*) FROM LoanDetails d JOIN LoanReceipts r ON r.loanReceiptId=d.loanReceiptId WHERE r.userId=? AND d.returnDate IS NULL", uid);
                unpaid = scalar(db, "SELECT COUNT(*) FROM Penalties WHERE userId=? AND penaltyStatus IN ('Unpaid','Partial')", uid);
            } catch (Exception ignored) {}
            final int r = receipts, a = activeLoans, p = unpaid;
            runOnUiThread(() -> {
                if (!isSessionReady()) return;
                privateSupportDialog().setTitle("Lịch sử độc giả")
                        .setMessage(value(doc, "fullName") + "\nPhiếu mượn (bản ghi trên thiết bị): " + r
                                + "\nSách đang mượn (bản ghi trên thiết bị): " + a
                                + "\nKhoản phạt chưa thanh toán (bản ghi trên thiết bị): " + p
                                + "\n\nLưu ý: lịch sử này chỉ phản ánh dữ liệu SQLite hiện có trên thiết bị.")
                        .setPositiveButton("Đóng", null).show();
            });
        });
    }

    private void showConfig() {
        clearContent("Cấu hình hệ thống", "Thông số được lưu trên Firestore và cache SQLite trên thiết bị Admin.");
        EditText maxBooks = numericInput("Số cuốn mượn tối đa / độc giả");
        EditText maxDays = numericInput("Số ngày mượn tối đa");
        EditText onlineHours = numericInput("Giờ giữ sách mượn online");
        EditText reserveHours = numericInput("Giờ giữ chỗ đặt trước");
        EditText fee = numericInput("Tiền phạt mỗi ngày (VNĐ)");
        EditText multiplier = numericInput("Hệ số bồi thường sách mất");
        content.addView(maxBooks, matchWrap()); content.addView(maxDays, matchWrap());
        content.addView(onlineHours, matchWrap()); content.addView(reserveHours, matchWrap());
        content.addView(fee, matchWrap()); content.addView(multiplier, matchWrap());
        SystemConfig local;
        try (com.example.libraryonline.data.repository.SystemConfigRepository repo =
                     new com.example.libraryonline.data.repository.SystemConfigRepository(this)) {
            local = repo.getCurrent();
        } catch (Exception e) { local = null; }
        if (local == null) local = new SystemConfig();
        setNumeric(maxBooks, local.maxBooksPerUser); setNumeric(maxDays, local.maxLoanDays);
        setNumeric(onlineHours, local.onlineHoldHours); setNumeric(reserveHours, local.reservationHoldHours);
        setNumeric(fee, local.lateFeePerDay); setNumeric(multiplier, local.lostBookMultiplier);
        final SystemConfig defaults = local;
        firestore.collection("SystemConfigs").document("default").get()
                .addOnSuccessListener(doc -> {
                    if (!isSessionReady() || !doc.exists()) return;
                    setNumeric(maxBooks, number(doc, "maxBooksPerUser", defaults.maxBooksPerUser));
                    setNumeric(maxDays, number(doc, "maxLoanDays", defaults.maxLoanDays));
                    setNumeric(onlineHours, number(doc, "onlineHoldHours", defaults.onlineHoldHours));
                    setNumeric(reserveHours, number(doc, "reservationHoldHours", defaults.reservationHoldHours));
                    setNumeric(fee, number(doc, "lateFeePerDay", defaults.lateFeePerDay));
                    setNumeric(multiplier, number(doc, "lostBookMultiplier", defaults.lostBookMultiplier));
                });
        Button save = button("Lưu cấu hình");
        save.setOnClickListener(v -> {
            int books = parse(maxBooks), days = parse(maxDays), online = parse(onlineHours),
                    reserve = parse(reserveHours), dailyFee = parse(fee), lost = parse(multiplier);
            if (books < 1 || books > 20 || days < 1 || days > 60 || online < 1 || online > 168
                    || reserve < 1 || reserve > 336 || dailyFee < 0 || dailyFee > 1000000 || lost < 1 || lost > 10) {
                toast("Thông số ngoài giới hạn cho phép. Kiểm tra lại các giá trị.");
                return;
            }
            save.setEnabled(false);
            Map<String,Object> data = new HashMap<>();
            data.put("configId", "default"); data.put("maxBooksPerUser", books); data.put("maxLoanDays", days);
            data.put("onlineHoldHours", online); data.put("reservationHoldHours", reserve);
            data.put("lateFeePerDay", dailyFee); data.put("lostBookMultiplier", lost);
            data.put("updatedBy", adminUid); data.put("updatedAt", FieldValue.serverTimestamp());
            firestore.collection("SystemConfigs").document("default").set(data)
                    .addOnSuccessListener(unused -> {
                        SystemConfig config = new SystemConfig();
                        config.configId = "default"; config.maxBooksPerUser = books; config.maxLoanDays = days;
                        config.onlineHoldHours = online; config.reservationHoldHours = reserve;
                        config.lateFeePerDay = dailyFee; config.lostBookMultiplier = lost;
                        config.updatedBy = adminUid;
                        config.updatedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
                        io.execute(() -> {
                            try (com.example.libraryonline.data.repository.SystemConfigRepository repo =
                                         new com.example.libraryonline.data.repository.SystemConfigRepository(this)) {
                                if (repo.getCurrent() == null) repo.create(config); else repo.update(config);
                            } catch (Exception e) { runOnUiThread(() -> toast("Đã lưu cloud nhưng lỗi cache cục bộ.")); }
                        });
                        save.setEnabled(true);
                        toast("Đã lưu cấu hình hệ thống.");
                    }).addOnFailureListener(e -> {
                        save.setEnabled(true);
                        toast("Không lưu được cấu hình: " + safe(e.getMessage()));
                    });
        });
        content.addView(save, matchWrap());
    }

    private void showReports() {
        clearContent("Báo cáo tiền phạt", "Báo cáo đọc từ SQLite trên thiết bị này; chưa phải báo cáo hợp nhất toàn hệ thống.");
        TextView report = text("Đang tổng hợp dữ liệu…", 15, false);
        content.addView(report, matchWrap());
        io.execute(() -> {
            long today = 0, month = 0, outstanding = 0, payments = 0;
            String dayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
            String monthKey = new SimpleDateFormat("yyyy-MM", Locale.ROOT).format(new Date());
            try (DatabaseHelper helper = new DatabaseHelper(getApplicationContext())) {
                android.database.sqlite.SQLiteDatabase db = helper.getReadableDatabase();
                today = sum(db, "SELECT COALESCE(SUM(amountReceived),0) FROM PenaltyPayments WHERE paymentStatus='Confirmed' AND substr(receivedAt,1,10)=?", dayKey);
                month = sum(db, "SELECT COALESCE(SUM(amountReceived),0) FROM PenaltyPayments WHERE paymentStatus='Confirmed' AND substr(receivedAt,1,7)=?", monthKey);
                outstanding = sum(db, "SELECT COALESCE(SUM(amountDue-amountPaid),0) FROM Penalties WHERE penaltyStatus IN ('Unpaid','Partial')", null);
                payments = scalar(db, "SELECT COUNT(*) FROM PenaltyPayments WHERE paymentStatus='Confirmed'", null);
            } catch (Exception ignored) {}
            final long d = today, m = month, o = outstanding, p = payments;
            runOnUiThread(() -> {
                if (!isSessionReady()) return;
                NumberFormat money = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));
                report.setText("Thu phạt hôm nay: " + money.format(d) + " đ\n"
                        + "Thu phạt tháng này: " + money.format(m) + " đ\n"
                        + "Tiền phạt còn nợ (ước tính): " + money.format(o) + " đ\n"
                        + "Số giao dịch đã xác nhận trên thiết bị: " + p);
            });
        });
        Button refresh = button("Làm mới báo cáo");
        refresh.setOnClickListener(v -> showReports());
        content.addView(refresh, matchWrap());
    }

    private void showProfile() {
        clearContent("Tài khoản Admin", "Email đăng nhập được quản lý bởi Firebase Authentication.");
        EditText name = new EditText(this); name.setHint("Họ và tên"); name.setSingleLine(true);
        EditText phone = new EditText(this); phone.setHint("Số điện thoại"); phone.setSingleLine(true);
        TextView email = text(FirebaseAuth.getInstance().getCurrentUser() == null ? "" :
                FirebaseAuth.getInstance().getCurrentUser().getEmail(), 14, false);
        content.addView(text("Email công vụ", 13, true), matchWrap());
        content.addView(email, matchWrap()); content.addView(name, matchWrap()); content.addView(phone, matchWrap());
        firestore.collection("Admins").document(adminUid).get().addOnSuccessListener(doc -> {
            if (!isSessionReady() || !doc.exists()) return;
            String storedName = value(doc, "fullName");
            if (storedName.isEmpty()) storedName = value(doc, "name");
            name.setText(storedName);
            phone.setText(value(doc, "phone"));
        }).addOnFailureListener(e -> toast("Không tải được hồ sơ Admin: " + safe(e.getMessage())));
        Button save = button("Lưu thông tin liên hệ");
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim(), p = phone.getText().toString().trim();
            if (n.length() < 2 || n.length() > 120) { name.setError("Tên phải từ 2 đến 120 ký tự"); return; }
            if (!p.isEmpty() && !p.matches("^0(3|5|7|8|9)[0-9]{8}$")) { phone.setError("Số điện thoại không hợp lệ"); return; }
            Map<String,Object> update = new HashMap<>();
            update.put("fullName", n); update.put("phone", p); update.put("updatedAt", FieldValue.serverTimestamp());
            save.setEnabled(false);
            firestore.collection("Admins").document(adminUid).update(update)
                    .addOnSuccessListener(x -> { save.setEnabled(true); toast("Đã cập nhật hồ sơ Admin."); })
                    .addOnFailureListener(e -> { save.setEnabled(true); toast("Không lưu được hồ sơ: " + safe(e.getMessage())); });
        });
        content.addView(save, matchWrap());
        Button password = button("Đổi mật khẩu quản trị");
        password.setOnClickListener(v -> startActivity(new android.content.Intent(this, AdminPasswordActivity.class)));
        content.addView(password, matchWrap());
    }

    private void clearContent(String heading, String subtitle) {
        if (usersListener != null) { usersListener.remove(); usersListener = null; }
        searchInput = null;
        content.removeAllViews();
        content.addView(text(heading, 22, true), matchWrap());
        content.addView(text(subtitle, 13, false), matchWrap());
    }

    private EditText numericInput(String hint) {
        EditText input = new EditText(this); input.setHint(hint); input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER); return input;
    }
    private void setNumeric(EditText view, int value) { view.setText(String.valueOf(value)); }
    private int parse(EditText view) { try { return Integer.parseInt(view.getText().toString().trim()); } catch (Exception e) { return -1; } }
    private int number(DocumentSnapshot doc, String field, int fallback) { Long n = doc.getLong(field); return n == null ? fallback : n.intValue(); }
    private static String value(DocumentSnapshot doc, String field) { String v = doc.getString(field); return v == null ? "" : v; }
    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(getColor(R.color.library_text)); view.setPadding(0, dp(5), 0, dp(5));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }
    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.library_blue)));
        b.setTextColor(getColor(R.color.library_surface)); return b;
    }
    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(6); p.bottomMargin = dp(6); return p;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String safe(String value) { return value == null ? "lỗi không xác định" : value; }
    private void toast(String message) { if (!isFinishing() && !isDestroyed()) Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private static int scalar(android.database.sqlite.SQLiteDatabase db, String sql, String arg) {
        try (android.database.Cursor c = arg == null ? db.rawQuery(sql, null) : db.rawQuery(sql, new String[]{arg})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
    private static long sum(android.database.sqlite.SQLiteDatabase db, String sql, String arg) {
        try (android.database.Cursor c = arg == null ? db.rawQuery(sql, null) : db.rawQuery(sql, new String[]{arg})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }
}
