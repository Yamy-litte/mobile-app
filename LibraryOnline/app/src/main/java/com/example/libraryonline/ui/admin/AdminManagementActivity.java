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
import com.example.libraryonline.data.model.User;
import com.example.libraryonline.ui.auth.SessionActivity;
import com.example.libraryonline.data.session.SessionProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.Timestamp;

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
public class AdminManagementActivity extends SessionActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private FirebaseFirestore firestore;
    private String adminUid;
    private LinearLayout content;
    private ListenerRegistration usersListener;
    private java.util.List<DocumentSnapshot> users = new java.util.ArrayList<>();
    private String searchText = "";
    private EditText searchInput;
    private TextView readersSummary;
    private String readerFilter = "all";
    private LinearLayout resultsContainer;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_admin_management);
        firestore = FirebaseFirestore.getInstance();
        content = findViewById(R.id.adminManagementContent);
        findViewById(R.id.adminManagementBack).setOnClickListener(v -> finish());
        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                findViewById(R.id.adminBottomNavigation);
        nav.getMenu().findItem(R.id.navAdminRequests).setChecked(true);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navAdminRequests) {
                finish();  // Back to Management landing page.
            } else if (id == R.id.navAdminInventory) {
                startActivity(new android.content.Intent(this,
                        com.example.libraryonline.ui.admin.inventory.InventoryActivity.class));
                finish();
            } else {
                int tab = id == R.id.navAdminHome ? AdminHomeView.HOME
                        : id == R.id.navAdminAccount ? AdminHomeView.ACCOUNT
                        : id == R.id.navAdminCirculation ? AdminHomeView.CIRCULATION
                        : AdminHomeView.REQUESTS;
                AdminNavigation.returnToMain(this, tab);
            }
            return false;
        });
    }

    @Override protected SessionProfile.Role expectedRole() { return SessionProfile.Role.ADMIN; }

    /** Distinct Activity subclasses select their fixed screen. */
    protected String managementSection() {
        String input = getIntent().getStringExtra("section");
        return "config".equals(input) || "reports".equals(input) ? input : "readers";
    }

    @Override protected void onSessionAuthorized(SessionProfile profile) {
        adminUid = profile.uid;
        String section = managementSection();
        if ("config".equals(section)) showConfig();
        else if ("reports".equals(section)) showReports();
        else showReaders();
    }

    @Override protected void onSessionStopped() {
        if (usersListener != null) { usersListener.remove(); usersListener = null; }
        content.removeAllViews();
    }

    @Override protected void onDestroy() {
        if (usersListener != null) { usersListener.remove(); usersListener = null; }
        io.shutdownNow();
        super.onDestroy();
    }

    private void showReaders() {
        if (!isSessionReady()) return;
        clearContent("Tìm độc giả", "Tìm theo họ tên hoặc số điện thoại. Thẻ được quản lý trên Firestore.");
        readerFilter = "all";
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Tìm theo tên / SĐT");
        searchInput.setText(searchText);
        searchInput.setTextColor(getColor(R.color.admin_screen_ink));
        searchInput.setTextSize(14);
        searchInput.setBackgroundResource(R.drawable.bg_admin_screen_field);
        searchInput.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_inventory_search, 0, 0, 0);
        searchInput.setCompoundDrawablePadding(dp(10));
        searchInput.setPadding(dp(16), 0, dp(16), 0);
        content.addView(searchInput, new LinearLayout.LayoutParams(-1, dp(52)));

        readersSummary = text("Đang tải danh sách độc giả...", 13, false);
        readersSummary.setBackgroundResource(R.drawable.bg_admin_screen_readonly);
        readersSummary.setPadding(dp(12), dp(10), dp(12), dp(10));
        content.addView(readersSummary, matchWrap());

        android.widget.HorizontalScrollView filterScroll = new android.widget.HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        String[] keys = {"all", "active", "expired", "locked"};
        String[] labels = {"Tất cả", "Hoạt động", "Hết hạn", "Bị khóa"};
        java.util.List<Button> choices = new java.util.ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            Button chip = button(labels[i]);
            chip.setTextSize(12);
            chip.setMinHeight(dp(42));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
            lp.setMargins(0, dp(7), dp(8), dp(8));
            chips.addView(chip, lp);
            choices.add(chip);
            chip.setOnClickListener(v -> {
                readerFilter = key;
                styleReaderFilterChips(choices, keys);
                renderUsers();
            });
        }
        styleReaderFilterChips(choices, keys);
        filterScroll.addView(chips);
        content.addView(filterScroll, matchWrap());

        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(resultsContainer, matchWrap());

        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                searchText = text.toString().trim().toLowerCase(Locale.ROOT);
                renderUsers();
            }
            @Override public void afterTextChanged(android.text.Editable text) {}
        });
        if (usersListener != null) { usersListener.remove(); usersListener = null; }
        usersListener = firestore.collection("Users").whereEqualTo("role", "Reader")
                .addSnapshotListener((snapshot, error) -> {
                    if (!isSessionReady() || searchInput == null) return;
                    if (error != null) {
                        readersSummary.setText("Không tải được hồ sơ. Kiểm tra kết nối và quyền truy cập.");
                        return;
                    }
                    if (snapshot == null) return;
                    users = snapshot.getDocuments();
                    cacheReaderProfiles(users);
                    renderUsers();
                });
    }

    private void styleReaderFilterChips(java.util.List<Button> chips, String[] keys) {
        for (int i = 0; i < keys.length; i++) {
            boolean selected = keys[i].equals(readerFilter);
            chips.get(i).setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getColor(selected ? R.color.admin_screen_primary : R.color.library_surface)));
            chips.get(i).setTextColor(getColor(
                    selected ? R.color.white : R.color.admin_screen_ink));
        }
    }

    private void cacheReaderProfiles(java.util.List<DocumentSnapshot> documents) {
        java.util.List<User> profiles = new java.util.ArrayList<>();
        for (DocumentSnapshot doc : documents) {
            User user = new User();
            user.userId = doc.getId();
            user.fullName = value(doc, "fullName");
            user.email = value(doc, "email");
            user.dateOfBirth = value(doc, "dateOfBirth");
            user.phone = value(doc, "phone");
            user.barcode = value(doc, "barcode");
            user.role = value(doc, "role");
            user.cardStatus = value(doc, "cardStatus");
            user.cardExpiryDate = value(doc, "cardExpiryDate");
            Object deleted = doc.get("isDeleted");
            user.isDeleted = Boolean.TRUE.equals(deleted) || (deleted instanceof Number && ((Number) deleted).intValue() == 1);
            user.createdAt = timestampText(doc.get("createdAt"));
            user.updatedAt = timestampText(doc.get("updatedAt"));
            // The local schema requires identity, email and audit timestamps; skip incomplete legacy rows.
            if (!user.fullName.isEmpty() && !user.email.isEmpty()
                    && !user.createdAt.isEmpty() && !user.updatedAt.isEmpty()) profiles.add(user);
        }
        io.execute(() -> {
            try (DatabaseHelper helper = new DatabaseHelper(getApplicationContext())) {
                android.database.sqlite.SQLiteDatabase db = helper.getWritableDatabase();
                db.beginTransaction();
                try {
                    com.example.libraryonline.data.local.dao.UserDao dao =
                            new com.example.libraryonline.data.local.dao.UserDao(db);
                    for (User user : profiles) {
                        try { dao.upsertFromCloud(user); } catch (RuntimeException ignored) {
                            // Keep other readers cacheable if a legacy email collision exists.
                        }
                    }
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
            } catch (RuntimeException ignored) {
                // A cache failure must not hide cloud-backed account management.
            }
        });
    }

    private String timestampText(Object value) {
        if (value instanceof Timestamp) {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
                    .format(((Timestamp) value).toDate());
        }
        return value instanceof String ? ((String) value).trim() : "";
    }

    private void renderUsers() {
        if (searchInput == null || resultsContainer == null || !isSessionReady()) return;
        resultsContainer.removeAllViews();
        int active = 0, locked = 0, expired = 0, visible = 0;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        for (DocumentSnapshot doc : users) {
            String cardStatus = value(doc, "cardStatus");
            String expiry = value(doc, "cardExpiryDate");
            boolean isLocked = "BiKhoa".equals(cardStatus);
            boolean isExpired = !expiry.isEmpty() && expiry.compareTo(today) < 0;
            if (isLocked) locked++;
            else if (isExpired) expired++;
            else active++;
            String name = value(doc, "fullName");
            String phone = value(doc, "phone");
            if (!searchText.isEmpty()
                    && !name.toLowerCase(Locale.ROOT).contains(searchText)
                    && !phone.toLowerCase(Locale.ROOT).contains(searchText)) continue;
            if ("active".equals(readerFilter) && (isLocked || isExpired)) continue;
            if ("expired".equals(readerFilter) && (!isExpired || isLocked)) continue;
            if ("locked".equals(readerFilter) && !isLocked) continue;
            visible++;
            LinearLayout card = panel();
            LinearLayout.LayoutParams margins = matchWrap();
            margins.bottomMargin = dp(10);
            resultsContainer.addView(card, margins);
            TextView heading = text(name.isEmpty() ? "Độc giả" : name, 17, true);
            card.addView(heading, matchWrap());
            TextView status = text("Mã thẻ: " + value(doc, "barcode") + " · "
                            + (isLocked ? "Bị khóa" : isExpired ? "Hết hạn" : "Hoạt động"),
                    13, true);
            status.setTextColor(getColor(isLocked ? R.color.admin_screen_red
                    : isExpired ? R.color.admin_screen_orange : R.color.admin_screen_green));
            card.addView(status, matchWrap());
            card.addView(text("Email: " + value(doc, "email"), 13, false), matchWrap());
            card.addView(text("SĐT: " + (phone.isEmpty() ? "Chưa cập nhật" : phone), 13, false), matchWrap());
            card.addView(text("Hạn thẻ: " + (expiry.isEmpty() ? "Chưa cập nhật" : expiry), 12, false), matchWrap());
            Button open = button("Xem hồ sơ");
            open.setOnClickListener(v -> showReaderDetails(doc));
            card.addView(open, matchWrap());
        }
        readersSummary.setText("Tổng: " + users.size()
                + "  |  Hoạt động: " + active
                + "  |  Hết hạn: " + expired
                + "  |  Bị khóa: " + locked);
        if (visible == 0)
            resultsContainer.addView(text("Không tìm thấy độc giả phù hợp.", 14, false), matchWrap());
    }

    /** Detail sub-screen from Figma; only card status/expiry are mutable for readers. */
    private void showReaderDetails(DocumentSnapshot doc) {
        if (!isSessionReady()) return;
        clearContent("Hồ sơ độc giả", "Chi tiết thẻ và lịch sử được ghi trên thiết bị.");
        Button back = button("← Trở về danh sách độc giả");
        back.setOnClickListener(v -> showReaders());
        content.addView(back, matchWrap());

        LinearLayout card = panel();
        content.addView(card, matchWrap());
        card.addView(text(value(doc, "fullName"), 21, true), matchWrap());
        card.addView(text("Mã thẻ: " + value(doc, "barcode"), 14, true), matchWrap());
        String status = value(doc, "cardStatus");
        TextView state = text("Trạng thái: " + ("BiKhoa".equals(status) ? "Bị khóa" : "HoatDong".equals(status) ? "Hoạt động" : status),
                14, true);
        state.setTextColor(getColor("BiKhoa".equals(status)
                ? R.color.admin_screen_red : R.color.admin_screen_green));
        card.addView(state, matchWrap());
        card.addView(text("Hiệu lực đến: " + value(doc, "cardExpiryDate"), 13, false), matchWrap());

        LinearLayout identity = panel();
        content.addView(identity, matchWrap());
        identity.addView(text("Thông tin độc giả", 17, true), matchWrap());
        identity.addView(text("Email: " + value(doc, "email"), 14, false), matchWrap());
        identity.addView(text("Số điện thoại: " + value(doc, "phone"), 14, false), matchWrap());
        identity.addView(text("Ngày sinh: " + value(doc, "dateOfBirth"), 14, false), matchWrap());

        Button history = button("Xem lịch sử mượn và các khoản phạt");
        history.setOnClickListener(v -> showReaderHistory(doc));
        content.addView(history, matchWrap());

        Button lock = button("BiKhoa".equals(status) ? "Mở khóa thẻ" : "Khóa thẻ thủ công");
        lock.setOnClickListener(v -> updateCardStatus(doc, "BiKhoa".equals(status) ? "HoatDong" : "BiKhoa"));
        content.addView(lock, matchWrap());
        Button renew = button("Gia hạn thẻ thêm 1 năm");
        renew.setOnClickListener(v -> renewCard(doc));
        content.addView(renew, matchWrap());

        TextView note = text("Hồ sơ cloud và lịch sử SQLite có phạm vi dữ liệu khác nhau. "
                + "Việc khóa thẻ và gia hạn cần xác nhận trước khi cập nhật Firestore.", 12, false);
        content.addView(note, matchWrap());
    }

    private LinearLayout panel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(15), dp(15), dp(15));
        box.setBackgroundResource(R.drawable.bg_admin_screen_card);
        return box;
    }

    private void updateCardStatus(DocumentSnapshot doc, String status) {
        if (!isSessionReady()) return;
        if ("HoatDong".equals(status)) {
            String expiry = value(doc, "cardExpiryDate");
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
            if (!expiry.isEmpty() && expiry.compareTo(today) < 0) {
                toast("Thẻ đã hết hạn. Hãy dùng chức năng Gia hạn 1 năm trước khi mở khóa.");
                return;
            }
        }
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
                        config.updatedBy = null; // Bootstrap Admins may not have a local parent row.
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

    private void clearContent(String heading, String subtitle) {
        if (usersListener != null) { usersListener.remove(); usersListener = null; }
        searchInput = null;
        content.removeAllViews();
        ((TextView) findViewById(R.id.adminManagementTitle)).setText(heading);
        ((TextView) findViewById(R.id.adminManagementSubtitle)).setText(subtitle);
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
        view.setTextColor(getColor(R.color.admin_screen_ink)); view.setPadding(0, dp(5), 0, dp(5));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }
    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.admin_screen_primary)));
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
