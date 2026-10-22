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
    private int reportGeneration;
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
        reportGeneration++;
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
                    doc.getReference().update(updates).addOnSuccessListener(x -> {
                        toast("Đã cập nhật trạng thái thẻ.");
                        reloadReaderDetails(doc);
                    })
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
                    doc.getReference().update(updates).addOnSuccessListener(x -> {
                        toast("Đã gia hạn thẻ đến " + newExpiry);
                        reloadReaderDetails(doc);
                    })
                            .addOnFailureListener(e -> toast("Không gia hạn được: " + safe(e.getMessage())));
                }).show();
    }

    private void reloadReaderDetails(DocumentSnapshot previous) {
        if (!isSessionReady()) return;
        previous.getReference().get().addOnSuccessListener(fresh -> {
            if (isSessionReady() && fresh.exists() && searchInput == null
                    && "readers".equals(managementSection())) showReaderDetails(fresh);
        });
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
        clearContent("Cấu hình thư viện", "Thay đổi hạn mức mượn; các quy tắc khác chỉ đọc như bản thiết kế.");
        TextView source = text("Đang kiểm tra cấu hình Firestore...", 13, false);
        source.setBackgroundResource(R.drawable.bg_admin_screen_readonly);
        source.setPadding(dp(12), dp(11), dp(12), dp(11));
        content.addView(source, matchWrap());

        LinearLayout form = panel();
        form.addView(text("Hạn mức mượn", 19, true), matchWrap());
        EditText maxBooks = numericInput("Số cuốn tối đa / độc giả");
        EditText maxDays = numericInput("Số ngày mượn tối đa");
        addNumberStepper(form, "Số cuốn tối đa / độc giả", maxBooks, 1, 20);
        addNumberStepper(form, "Số ngày mượn tối đa", maxDays, 1, 60);
        TextView help = text(
                "Tính cả sách đang mượn và yêu cầu Online chờ lấy. Tổng không được vượt hạn mức.",
                12, false);
        form.addView(help, matchWrap());
        content.addView(form, matchWrap());

        LinearLayout fixed = panel();
        fixed.addView(text("Quy tắc cố định · Chỉ đọc", 18, true), matchWrap());
        TextView holdOnline = text("", 14, false);
        TextView holdReservation = text("", 14, false);
        TextView lateFee = text("", 14, false);
        TextView multiplier = text("", 14, false);
        // Reservation priority and the old automatic lost-book multiplier are
        // intentionally not exposed in the revised UI. Keep the corresponding
        // legacy config fields intact so existing Firestore/SQLite data survives.
        for (TextView v : new TextView[]{holdOnline, lateFee})
            fixed.addView(v, matchWrap());
        content.addView(fixed, matchWrap());

        SystemConfig local;
        try (com.example.libraryonline.data.repository.SystemConfigRepository repository =
                     new com.example.libraryonline.data.repository.SystemConfigRepository(this)) {
            local = repository.getCurrent();
        } catch (RuntimeException error) {
            local = null;
        }
        if (local == null) {
            local = new SystemConfig();
            local.maxBooksPerUser = 5;
            local.maxLoanDays = 14;
            local.onlineHoldHours = 24;
            local.reservationHoldHours = 48;
            local.lateFeePerDay = 5000;
            local.lostBookMultiplier = 2;
        }
        setNumeric(maxBooks, local.maxBooksPerUser);
        setNumeric(maxDays, local.maxLoanDays);
        final int[] fixedValues = {
                local.onlineHoldHours, local.reservationHoldHours,
                local.lateFeePerDay, local.lostBookMultiplier
        };
        updateFixedConfigLabels(holdOnline, holdReservation, lateFee, multiplier, fixedValues);
        source.setText("Đang hiển thị cấu hình cục bộ · Đang đồng bộ...");

        // Never overwrite legacy/hidden fields with stale local defaults.
        // The Admin must first verify server configuration before saving.
        final boolean[] existingCloudConfig = {false};
        Button save = button("Lưu cấu hình");
        save.setEnabled(false);
        content.addView(save, matchWrap());
        TextView notice = text("Giới hạn mới chỉ có hiệu lực sau khi Firestore xác nhận lưu.",
                12, false);
        content.addView(notice, matchWrap());

        firestore.collection("SystemConfigs").document("default")
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (!isSessionReady()) return;
                    if (!doc.exists()) {
                        source.setText("Chưa có cấu hình Firestore · Lần lưu đầu sẽ tạo cấu hình mặc định");
                        save.setEnabled(true);
                        return;
                    }
                    existingCloudConfig[0] = true;
                    save.setEnabled(true);
                    setNumeric(maxBooks, number(doc, "maxBooksPerUser", parse(maxBooks)));
                    setNumeric(maxDays, number(doc, "maxLoanDays", parse(maxDays)));
                    fixedValues[0] = number(doc, "onlineHoldHours", fixedValues[0]);
                    fixedValues[1] = number(doc, "reservationHoldHours", fixedValues[1]);
                    fixedValues[2] = number(doc, "lateFeePerDay", fixedValues[2]);
                    fixedValues[3] = number(doc, "lostBookMultiplier", fixedValues[3]);
                    updateFixedConfigLabels(
                            holdOnline, holdReservation, lateFee, multiplier, fixedValues);
                    source.setText("Trực tuyến · Đã tải cấu hình Firestore");
                })
                .addOnFailureListener(error -> {
                    if (isSessionReady()) {
                        save.setEnabled(false);
                        source.setText("Không kết nối được Firestore. Chưa thể lưu cấu hình khi ngoại tuyến.");
                    }
                });
        save.setOnClickListener(v -> {
            if (!isSessionReady()) return;
            int books = parse(maxBooks), days = parse(maxDays),
                    online = fixedValues[0], reserve = fixedValues[1],
                    fee = fixedValues[2], lost = fixedValues[3];
            if (books < 1 || books > 20 || days < 1 || days > 60
                    || online < 1 || reserve < 1 || fee < 0 || lost < 1) {
                toast("Hạn mức ngoài giới hạn cho phép. Kiểm tra lại thông tin.");
                return;
            }
            save.setEnabled(false);
            source.setText("Đang lưu cấu hình lên Firestore...");
            Map<String,Object> data = new HashMap<>();
            data.put("maxBooksPerUser", books);
            data.put("maxLoanDays", days);
            data.put("updatedBy", adminUid);
            data.put("updatedAt", FieldValue.serverTimestamp());
            com.google.firebase.firestore.DocumentReference configDoc =
                    firestore.collection("SystemConfigs").document("default");
            com.google.android.gms.tasks.Task<Void> operation;
            if (existingCloudConfig[0]) {
                // The only two business settings editable in the revised UI.
                // Do not rewrite old reservation/loss multiplier fields.
                operation = configDoc.update(data);
            } else {
                // Only initial creation needs all required legacy schema fields.
                data.put("configId", "default");
                data.put("onlineHoldHours", online);
                data.put("reservationHoldHours", reserve);
                data.put("lateFeePerDay", fee);
                data.put("lostBookMultiplier", lost);
                operation = configDoc.set(data);
            }
            operation.addOnSuccessListener(unused -> {
                        if (!isSessionReady()) return;
                        SystemConfig config = new SystemConfig();
                        config.configId = "default"; config.maxBooksPerUser = books; config.maxLoanDays = days;
                        config.onlineHoldHours = online; config.reservationHoldHours = reserve;
                        config.lateFeePerDay = fee; config.lostBookMultiplier = lost;
                        config.updatedBy = null;
                        config.updatedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
                        io.execute(() -> {
                            try (com.example.libraryonline.data.repository.SystemConfigRepository repository =
                                         new com.example.libraryonline.data.repository.SystemConfigRepository(this)) {
                                if (repository.getCurrent() == null) repository.create(config);
                                else repository.update(config);
                            } catch (RuntimeException error) {
                                runOnUiThread(() -> { if (isSessionReady())
                                    toast("Đã lưu Firestore nhưng chưa cập nhật cache SQLite."); });
                            }
                        });
                        save.setEnabled(true);
                        source.setText("Trực tuyến · Đã lưu cấu hình Firestore");
                        toast("Đã lưu hạn mức mượn.");
                    })
                    .addOnFailureListener(error -> {
                        if (!isSessionReady()) return;
                        save.setEnabled(true);
                        source.setText("Lưu thất bại · Không có thay đổi được xác nhận");
                        toast("Không lưu được cấu hình: " + safe(error.getMessage()));
                    });
        });
    }

    private void addNumberStepper(LinearLayout parent, String title,
                                  EditText field, int min, int max) {
        parent.addView(text(title, 14, true), matchWrap());
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button less = button("−");
        Button more = button("+");
        less.setContentDescription("Giảm " + title);
        more.setContentDescription("Tăng " + title);
        controls.addView(less, new LinearLayout.LayoutParams(dp(46), dp(48)));
        field.setGravity(android.view.Gravity.CENTER);
        field.setBackgroundResource(R.drawable.bg_admin_screen_field);
        controls.addView(field, new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(more, new LinearLayout.LayoutParams(dp(46), dp(48)));
        less.setOnClickListener(v -> field.setText(String.valueOf(Math.max(min, parse(field) - 1))));
        more.setOnClickListener(v -> field.setText(String.valueOf(Math.min(max,
                Math.max(min - 1, parse(field)) + 1))));
        parent.addView(controls, matchWrap());
    }

    private void updateFixedConfigLabels(TextView online, TextView reservation,
                                          TextView fee, TextView multiplier, int[] values) {
        online.setText("Giữ sách Online: " + values[0] + " giờ");
        reservation.setText("Ưu tiên đặt trước: " + values[1] + " giờ");
        fee.setText("Phạt trả chậm: " + NumberFormat.getIntegerInstance(new Locale("vi", "VN"))
                .format(values[2]) + " đ / ngày");
        multiplier.setText("Bồi thường sách mất: giá bìa × " + values[3]);
    }

    private void showReports() {
        clearContent("Báo cáo thu phạt", "Dữ liệu SQLite trên thiết bị · Không phải tổng hợp toàn hệ thống");
        TextView status = text("Đang tải các khoản thu trên thiết bị...", 13, false);
        status.setBackgroundResource(R.drawable.bg_admin_screen_readonly);
        status.setPadding(dp(11), dp(10), dp(11), dp(10));
        content.addView(status, matchWrap());

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundResource(R.drawable.bg_admin_screen_readonly);
        final Button[] choices = new Button[3];
        final String[] labels = {"Ngày", "Tuần", "Tháng"};
        content.addView(tabs, matchWrap());

        LinearLayout totals = panel();
        TextView heading = text("Tiền phạt đã thu", 16, true);
        TextView collected = text("—", 29, true);
        collected.setTextColor(getColor(R.color.admin_screen_primary));
        TextView transactionCount = text("Đang tải số giao dịch...", 13, false);
        TextView outstanding = text("Nợ chờ thu: —", 14, true);
        totals.addView(heading, matchWrap());
        totals.addView(collected, matchWrap());
        totals.addView(transactionCount, matchWrap());
        totals.addView(outstanding, matchWrap());
        content.addView(totals, matchWrap());

        LinearLayout trend = panel();
        trend.addView(text("Đã thu trong 7 ngày gần nhất", 17, true), matchWrap());
        LinearLayout bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setGravity(android.view.Gravity.BOTTOM);
        trend.addView(bars, matchWrap());
        content.addView(trend, matchWrap());

        LinearLayout recentCard = panel();
        recentCard.addView(text("Khoản đã thu", 17, true), matchWrap());
        TextView recent = text("Đang tải...", 13, false);
        recent.setLineSpacing(dp(5), 1);
        recentCard.addView(recent, matchWrap());
        content.addView(recentCard, matchWrap());

        LinearLayout debtCard = panel();
        debtCard.addView(text("Nợ trễ hạn chờ thu", 17, true), matchWrap());
        TextView debts = text("Đang tải...", 13, false);
        debts.setLineSpacing(dp(5), 1);
        debtCard.addView(debts, matchWrap());
        content.addView(debtCard, matchWrap());

        Button refresh = button("Làm mới báo cáo");
        content.addView(refresh, matchWrap());

        final int[] period = {0};
        Runnable fetch = () -> {
            final int requestedPeriod = period[0];
            final int request = ++reportGeneration;
            for (int i = 0; i < choices.length; i++) {
                boolean selected = i == requestedPeriod;
                choices[i].setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                getColor(selected ? R.color.admin_screen_primary
                                        : R.color.library_surface)));
                choices[i].setTextColor(getColor(selected ? R.color.white : R.color.admin_screen_ink));
            }
            status.setText("Đang tổng hợp các giao dịch trên thiết bị...");
            io.execute(() -> {
                final ReportSnapshot data;
                try (DatabaseHelper helper = new DatabaseHelper(getApplicationContext())) {
                    data = queryPenaltyReport(helper.getReadableDatabase(), requestedPeriod);
                } catch (RuntimeException error) {
                    runOnUiThread(() -> {
                        if (isSessionReady() && !isFinishing() && request == reportGeneration) {
                            status.setText("Không đọc được báo cáo SQLite: " + safe(error.getMessage()));
                            collected.setText("—");
                            transactionCount.setText("Không có dữ liệu xác thực");
                            outstanding.setText("Nợ chờ thu: —");
                            bars.removeAllViews();
                            recent.setText("Không tải được");
                            debts.setText("Không tải được");
                        }
                    });
                    return;
                }
                runOnUiThread(() -> {
                    if (!isSessionReady() || isFinishing() || isDestroyed()
                            || request != reportGeneration) return;
                    status.setText("Dữ liệu tại thiết bị · " + data.periodLabel);
                    NumberFormat currency = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));
                    collected.setText(currency.format(data.collected) + " đ");
                    transactionCount.setText("Giao dịch đã xác nhận trong kỳ: " + data.payments);
                    outstanding.setText("Nợ chờ thu (tách riêng): " +
                            currency.format(data.outstanding) + " đ");
                    recent.setText(data.recentPayments.isEmpty()
                            ? "Chưa có khoản thu được ghi trên thiết bị."
                            : android.text.TextUtils.join("\n", data.recentPayments));
                    debts.setText(data.unpaidPenalties.isEmpty()
                            ? "Chưa ghi nhận khoản nợ trễ hạn trên thiết bị."
                            : android.text.TextUtils.join("\n", data.unpaidPenalties));
                    buildWeeklyPenaltyBars(bars, data.lastSevenDays);
                });
            });
        };
        for (int i = 0; i < choices.length; i++) {
            final int index = i;
            choices[i] = button(labels[i]);
            choices[i].setTextSize(12);
            tabs.addView(choices[i], new LinearLayout.LayoutParams(0, dp(44), 1));
            choices[i].setOnClickListener(v -> {
                period[0] = index;
                fetch.run();
            });
        }
        refresh.setOnClickListener(v -> fetch.run());
        fetch.run();
    }

    private static final class ReportSnapshot {
        long collected, outstanding;
        int payments;
        String periodLabel;
        long[] lastSevenDays = new long[7];
        java.util.List<String> recentPayments = new java.util.ArrayList<>();
        java.util.List<String> unpaidPenalties = new java.util.ArrayList<>();
    }

    private ReportSnapshot queryPenaltyReport(
            android.database.sqlite.SQLiteDatabase db, int period) {
        ReportSnapshot result = new ReportSnapshot();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        Calendar clock = Calendar.getInstance();
        String today = formatter.format(clock.getTime());
        Calendar start = (Calendar) clock.clone();
        if (period == 1) start.add(Calendar.DAY_OF_MONTH,
                -((start.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        else if (period == 2) start.set(Calendar.DAY_OF_MONTH, 1);
        String from = formatter.format(start.getTime());
        Calendar until = (Calendar) clock.clone();
        if (period == 0) until.add(Calendar.DAY_OF_MONTH, 1);
        else if (period == 1) {
            until = (Calendar) start.clone();
            until.add(Calendar.DAY_OF_MONTH, 7);
        } else {
            until.set(Calendar.DAY_OF_MONTH, 1);
            until.add(Calendar.MONTH, 1);
        }
        String to = formatter.format(until.getTime());
        result.periodLabel = period == 0 ? "Ngày " + today
                : period == 1 ? "Tuần bắt đầu " + from : "Tháng " + from.substring(0, 7);
        String restriction = "paymentStatus='Confirmed' AND substr(receivedAt,1,10)>=? "
                + "AND substr(receivedAt,1,10)<?";
        try (android.database.Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(amountReceived),0),COUNT(*) FROM PenaltyPayments WHERE "
                        + restriction, new String[]{from, to})) {
            if (c.moveToFirst()) {
                result.collected = c.getLong(0);
                result.payments = c.getInt(1);
            }
        }
        result.outstanding = sum(db, "SELECT COALESCE(SUM(amountDue-amountPaid),0) "
                + "FROM Penalties WHERE penaltyStatus IN ('Unpaid','Partial')", null);

        Calendar days = Calendar.getInstance();
        days.add(Calendar.DAY_OF_MONTH, -6);
        String[] keys = new String[7];
        for (int i = 0; i < 7; i++) {
            keys[i] = formatter.format(days.getTime());
            days.add(Calendar.DAY_OF_MONTH, 1);
        }
        try (android.database.Cursor c = db.rawQuery(
                "SELECT substr(receivedAt,1,10),SUM(amountReceived) FROM PenaltyPayments "
                        + "WHERE paymentStatus='Confirmed' AND substr(receivedAt,1,10)>=? "
                        + "AND substr(receivedAt,1,10)<=? GROUP BY substr(receivedAt,1,10)",
                new String[]{keys[0], keys[6]})) {
            while (c.moveToNext()) {
                String key = c.getString(0);
                for (int i = 0; i < keys.length; i++)
                    if (keys[i].equals(key)) {
                        result.lastSevenDays[i] = c.getLong(1);
                        break;
                    }
            }
        }

        NumberFormat amount = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));
        try (android.database.Cursor c = db.rawQuery(
                "SELECT COALESCE(NULLIF(u.fullName,''),'Độc giả'),pp.amountReceived,pp.receivedAt "
                        + "FROM PenaltyPayments pp JOIN Penalties p ON p.penaltyId=pp.penaltyId "
                        + "LEFT JOIN Users u ON u.userId=p.userId "
                        + "WHERE pp.paymentStatus='Confirmed' ORDER BY pp.receivedAt DESC LIMIT 5", null)) {
            while (c.moveToNext())
                result.recentPayments.add(c.getString(0) + " · Đã thu " + amount.format(c.getLong(1))
                        + " đ · " + c.getString(2));
        }
        try (android.database.Cursor c = db.rawQuery(
                "SELECT COALESCE(NULLIF(u.fullName,''),'Độc giả'),p.amountDue-p.amountPaid,p.daysLate "
                        + "FROM Penalties p LEFT JOIN Users u ON u.userId=p.userId "
                        + "WHERE p.penaltyStatus IN ('Unpaid','Partial') ORDER BY p.createdAt DESC LIMIT 5", null)) {
            while (c.moveToNext())
                result.unpaidPenalties.add(c.getString(0) + " · Còn nợ " +
                        amount.format(c.getLong(1)) + " đ · " + c.getInt(2) + " ngày trễ");
        }
        return result;
    }

    private void buildWeeklyPenaltyBars(LinearLayout container, long[] amounts) {
        container.removeAllViews();
        long max = 1;
        for (long value : amounts) max = Math.max(max, value);
        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_MONTH, -6);
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM", Locale.ROOT);
        for (int i = 0; i < 7; i++) {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            int height = amounts[i] <= 0 ? 0 : Math.max(dp(4),
                    (int) Math.round(dp(90) * amounts[i] / (double) max));
            View bar = new View(this);
            bar.setBackgroundColor(getColor(R.color.admin_screen_primary));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(22), height);
            barParams.bottomMargin = dp(6);
            column.addView(bar, barParams);
            TextView label = text(formatter.format(day.getTime()), 10, false);
            label.setGravity(android.view.Gravity.CENTER);
            column.addView(label, new LinearLayout.LayoutParams(-1, dp(28)));
            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, dp(130), 1);
            container.addView(column, columnParams);
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
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
