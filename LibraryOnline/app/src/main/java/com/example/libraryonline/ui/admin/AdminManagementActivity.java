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
import com.example.libraryonline.ui.scanner.LibraryBarcodeScanner;
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
    private LibraryBarcodeScanner barcodeScanner;
    private String pendingCardBarcode, scanOwnerUid;
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
        barcodeScanner = new LibraryBarcodeScanner(this, this::onScannedReaderCard);
        if (state != null) {
            pendingCardBarcode = state.getString("pendingCardBarcode");
            scanOwnerUid = state.getString("scanOwnerUid");
        }
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
        applyPendingReaderCard();
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

    private void onScannedReaderCard(String barcode) {
        pendingCardBarcode = barcode;
        applyPendingReaderCard();
    }

    private void applyPendingReaderCard() {
        if (pendingCardBarcode == null || !isSessionReady()
                || !"readers".equals(managementSection())) return;
        if (!java.util.Objects.equals(scanOwnerUid, adminUid)) {
            pendingCardBarcode = null;
            return;
        }
        if (searchInput == null || !searchInput.isAttachedToWindow()) return;
        String code = pendingCardBarcode;
        pendingCardBarcode = null;
        readerFilter = "all";
        searchInput.setText(code);
        searchInput.setSelection(code.length());
        renderUsers();
        Toast.makeText(this, "Đã tự điền mã thẻ vừa quét.", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("pendingCardBarcode", pendingCardBarcode);
        state.putString("scanOwnerUid", scanOwnerUid);
        super.onSaveInstanceState(state);
    }

    private void showReaders() {
        if (!isSessionReady()) return;
        clearContent("Tìm độc giả", "Tìm theo họ tên hoặc số điện thoại. Thẻ được quản lý trên Firestore.");
        readerFilter = "all";
        View screen = getLayoutInflater().inflate(R.layout.view_admin_reader_list, content, false);
        content.addView(screen);
        searchInput = screen.findViewById(R.id.adminReaderSearch);
        searchInput.setText(searchText);
        screen.findViewById(R.id.adminReaderScanCard).setOnClickListener(v -> {
            if (!isSessionReady()) return;
            scanOwnerUid = adminUid;
            barcodeScanner.scan("Quét mã thẻ độc giả");
        });
        readersSummary = screen.findViewById(R.id.adminReaderSummary);
        resultsContainer = screen.findViewById(R.id.adminReaderResults);
        String[] keys = {"all", "active", "expired", "locked"};
        int[] ids = {R.id.adminReaderFilter0, R.id.adminReaderFilter1,
                R.id.adminReaderFilter2, R.id.adminReaderFilter3};
        java.util.List<Button> choices = new java.util.ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            Button chip = screen.findViewById(ids[i]);
            choices.add(chip);
            chip.setOnClickListener(v -> {
                readerFilter = key;
                styleReaderFilterChips(choices, keys);
                renderUsers();
            });
        }
        styleReaderFilterChips(choices, keys);
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                searchText = text.toString().trim().toLowerCase(Locale.ROOT);
                renderUsers();
            }
            @Override public void afterTextChanged(android.text.Editable text) {}
        });
        applyPendingReaderCard();
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
            chips.get(i).setSelected(selected);
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
                    && !phone.toLowerCase(Locale.ROOT).contains(searchText)
                    && !value(doc, "barcode").toLowerCase(Locale.ROOT).contains(searchText))
                continue;
            if ("active".equals(readerFilter) && (isLocked || isExpired)) continue;
            if ("expired".equals(readerFilter) && (!isExpired || isLocked)) continue;
            if ("locked".equals(readerFilter) && !isLocked) continue;
            visible++;
            View card = getLayoutInflater().inflate(
                    R.layout.item_admin_reader_profile, resultsContainer, false);
            resultsContainer.addView(card);
            ((TextView) card.findViewById(R.id.adminReaderCardName))
                    .setText(name.isEmpty() ? "Độc giả" : name);
            TextView status = card.findViewById(R.id.adminReaderCardStatus);
            status.setText("Mã thẻ: " + value(doc, "barcode") + " · "
                    + (isLocked ? "Bị khóa" : isExpired ? "Hết hạn" : "Hoạt động"));
            status.setTextColor(getColor(isLocked ? R.color.admin_screen_red
                    : isExpired ? R.color.admin_screen_orange : R.color.admin_screen_green));
            ((TextView) card.findViewById(R.id.adminReaderCardEmail))
                    .setText("Email: " + value(doc, "email"));
            ((TextView) card.findViewById(R.id.adminReaderCardPhone))
                    .setText("SĐT: " + (phone.isEmpty() ? "Chưa cập nhật" : phone));
            ((TextView) card.findViewById(R.id.adminReaderCardExpiry))
                    .setText("Hạn thẻ: " + (expiry.isEmpty() ? "Chưa cập nhật" : expiry));
            card.findViewById(R.id.adminReaderCardOpen)
                    .setOnClickListener(v -> showReaderDetails(doc));
        }
        readersSummary.setText("Tổng: " + users.size()
                + "  |  Hoạt động: " + active
                + "  |  Hết hạn: " + expired
                + "  |  Bị khóa: " + locked);
        if (visible == 0) {
            TextView empty = (TextView) getLayoutInflater().inflate(
                    R.layout.view_ui_text_secondary, resultsContainer, false);
            empty.setText("Không tìm thấy độc giả phù hợp.");
            resultsContainer.addView(empty);
        }
    }

    /** Detail sub-screen from Figma; only card status/expiry are mutable for readers. */
    private void showReaderDetails(DocumentSnapshot doc) {
        if (!isSessionReady()) return;
        clearContent("Hồ sơ độc giả", "Thông tin thẻ trên Firestore và lịch sử mượn/trả theo từng nguồn.");
        View screen = getLayoutInflater().inflate(R.layout.view_admin_reader_detail, content, false);
        content.addView(screen);
        screen.findViewById(R.id.adminReaderDetailBack).setOnClickListener(v -> showReaders());
        ((TextView) screen.findViewById(R.id.adminReaderDetailName))
                .setText(value(doc, "fullName"));
        ((TextView) screen.findViewById(R.id.adminReaderDetailBarcode))
                .setText("Mã thẻ: " + value(doc, "barcode"));
        String status = value(doc, "cardStatus");
        TextView state = screen.findViewById(R.id.adminReaderDetailStatus);
        state.setText("Trạng thái: " + ("BiKhoa".equals(status) ? "Bị khóa"
                : "HoatDong".equals(status) ? "Hoạt động" : status));
        state.setTextColor(getColor("BiKhoa".equals(status)
                ? R.color.admin_screen_red : R.color.admin_screen_green));
        ((TextView) screen.findViewById(R.id.adminReaderDetailExpiry))
                .setText("Hiệu lực đến: " + value(doc, "cardExpiryDate"));
        ((TextView) screen.findViewById(R.id.adminReaderDetailEmail))
                .setText("Email: " + value(doc, "email"));
        ((TextView) screen.findViewById(R.id.adminReaderDetailPhone))
                .setText("Số điện thoại: " + value(doc, "phone"));
        ((TextView) screen.findViewById(R.id.adminReaderDetailBirth))
                .setText("Ngày sinh: " + value(doc, "dateOfBirth"));
        screen.findViewById(R.id.adminReaderDetailHistory)
                .setOnClickListener(v -> showReaderHistory(doc));
        Button lock = screen.findViewById(R.id.adminReaderDetailLock);
        lock.setText("BiKhoa".equals(status) ? "Mở khóa thẻ" : "Khóa thẻ thủ công");
        lock.setOnClickListener(v -> updateCardStatus(doc,
                "BiKhoa".equals(status) ? "HoatDong" : "BiKhoa"));
        screen.findViewById(R.id.adminReaderDetailRenew)
                .setOnClickListener(v -> renewCard(doc));
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

    /** Full per-reader history: Firestore Online receipts and this-device SQLite records. */
    private void showReaderHistory(DocumentSnapshot doc) {
        if (!isSessionReady()) return;
        clearContent("Hồ sơ độc giả · Lịch sử", "Các lần mượn, trả và khoản phạt trả muộn.");
        View screen = getLayoutInflater().inflate(
                R.layout.view_admin_reader_history, content, false);
        content.addView(screen);
        ((TextView) screen.findViewById(R.id.adminReaderHistoryName))
                .setText(value(doc, "fullName") + " · " + value(doc, "barcode"));
        screen.findViewById(R.id.adminReaderHistoryBack)
                .setOnClickListener(v -> showReaderDetails(doc));
        screen.findViewById(R.id.adminReaderHistoryRefresh)
                .setOnClickListener(v -> loadReaderHistory(doc.getId(), screen));
        loadReaderHistory(doc.getId(), screen);
    }

    private static final class ReaderHistoryState {
        com.example.libraryonline.data.repository.ReaderHistoryLocalRepository.Snapshot local;
        java.util.List<DocumentSnapshot> online;
        String localError, cloudError;
        boolean localDone, cloudDone;
    }

    private void loadReaderHistory(String readerUid, View screen) {
        if (!isSessionReady() || !screen.isAttachedToWindow()) return;
        final ReaderHistoryState state = new ReaderHistoryState();
        TextView summary = screen.findViewById(R.id.adminReaderHistorySummary);
        TextView localStatus = screen.findViewById(R.id.adminReaderHistoryFinesStatus);
        TextView onlineStatus = screen.findViewById(R.id.adminReaderHistoryLoansStatus);
        summary.setText("Đang tải lịch sử độc giả…");
        localStatus.setText("Đang tải các khoản phạt trên thiết bị…");
        onlineStatus.setText("Đang tải phiếu mượn trên thiết bị và Firestore…");
        ((LinearLayout) screen.findViewById(R.id.adminReaderHistoryLoans)).removeAllViews();
        ((LinearLayout) screen.findViewById(R.id.adminReaderHistoryFines)).removeAllViews();
        io.execute(() -> {
            com.example.libraryonline.data.repository.ReaderHistoryLocalRepository.Snapshot snapshot = null;
            String errorMessage = null;
            try (com.example.libraryonline.data.repository.ReaderHistoryLocalRepository repo =
                         new com.example.libraryonline.data.repository.ReaderHistoryLocalRepository(this)) {
                snapshot = repo.load(readerUid);
            } catch (RuntimeException failure) {
                errorMessage = safe(failure.getMessage());
            }
            final com.example.libraryonline.data.repository.ReaderHistoryLocalRepository.Snapshot result = snapshot;
            final String error = errorMessage;
            runOnUiThread(() -> {
                if (!historyScreenActive(screen)) return;
                state.localDone = true;
                state.local = result;
                state.localError = error;
                renderReaderHistory(state, screen);
            });
        });
        // This is deliberately SERVER-only: don't silently present stale cached
        // cloud receipts as a complete current account history.
        firestore.collection("OnlineLoanReceipts").whereEqualTo("userId", readerUid)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnCompleteListener(task -> {
                    if (!historyScreenActive(screen)) return;
                    state.cloudDone = true;
                    if (task.isSuccessful() && task.getResult() != null) {
                        state.online = task.getResult().getDocuments();
                    } else {
                        state.cloudError = task.getException() == null
                                ? "Không nhận được dữ liệu từ máy chủ."
                                : safe(task.getException().getMessage());
                    }
                    renderReaderHistory(state, screen);
                });
    }

    private boolean historyScreenActive(View screen) {
        return isSessionReady() && !isFinishing() && !isDestroyed()
                && screen.isAttachedToWindow();
    }

    private void renderReaderHistory(ReaderHistoryState data, View screen) {
        LinearLayout loans = screen.findViewById(R.id.adminReaderHistoryLoans);
        LinearLayout fines = screen.findViewById(R.id.adminReaderHistoryFines);
        loans.removeAllViews();
        fines.removeAllViews();
        TextView loanStatus = screen.findViewById(R.id.adminReaderHistoryLoansStatus);
        TextView fineStatus = screen.findViewById(R.id.adminReaderHistoryFinesStatus);
        int cloudCount = 0, localCount = 0, activeCount = 0, returnedCount = 0;
        int fineCount = 0;
        long remaining = 0;
        if (data.online != null) {
            java.util.List<DocumentSnapshot> sorted = new java.util.ArrayList<>(data.online);
            java.util.Collections.sort(sorted, (left, right) -> Long.compare(
                    timestampMillis(right.getTimestamp("collectedAt")),
                    timestampMillis(left.getTimestamp("collectedAt"))));
            for (DocumentSnapshot receipt : sorted) {
                String status = value(receipt, "status");
                if (!"Open".equals(status) && !"Returned".equals(status)) continue;
                cloudCount++;
                boolean returned = "Returned".equals(status);
                if (returned) returnedCount++; else activeCount++;
                String title = value(receipt, "bookTitle");
                if (title.isEmpty()) title = "Đầu sách chưa có tên";
                String detail = "Phiếu: " + receipt.getId()
                        + "\nMã cuốn: " + value(receipt, "copyId")
                        + "\nNgày giao: " + cloudDate(receipt.getTimestamp("collectedAt"))
                        + "\nHạn trả: " + cloudDate(receipt.getTimestamp("dueAt"))
                        + (returned ? "\nĐã trả: " + cloudDate(receipt.getTimestamp("returnedAt")) : "")
                        + "\nGia hạn: " + number(receipt, "extensionCount", 0) + " lần";
                String label = returned ? "Đã trả"
                        : timestampMillis(receipt.getTimestamp("dueAt")) > 0
                          && timestampMillis(receipt.getTimestamp("dueAt")) < System.currentTimeMillis()
                            ? "Quá hạn" : "Đang mượn";
                historyCard(loans, title, detail, label + " · Firestore");
            }
        }
        if (data.local != null) {
            for (com.example.libraryonline.data.repository.ReaderHistoryLocalRepository.Loan loan
                    : data.local.loans) {
                localCount++;
                boolean returned = loan.returnedAt != null && !loan.returnedAt.isEmpty()
                        && "DaTra".equals(loan.status);
                if (returned) returnedCount++;
                else if (loan.returnedAt == null
                        && ("DangMuon".equals(loan.status) || "QuaHan".equals(loan.status)))
                    activeCount++;
                String label = returned ? "Đã trả"
                        : "QuaHan".equals(loan.status) ? "Quá hạn"
                        : "DangMuon".equals(loan.status) ? "Đang mượn"
                        : loan.status == null ? "Chưa rõ trạng thái" : loan.status;
                historyCard(loans, loan.title == null || loan.title.isEmpty()
                                ? "Mã cuốn: " + loan.copyId : loan.title,
                        "Phiếu: " + loan.receiptId
                        + "\nMã cuốn: " + loan.copyId
                        + (loan.barcode == null || loan.barcode.isEmpty() ? "" : " · " + loan.barcode)
                        + "\nNgày mượn: " + localDate(loan.borrowedAt)
                        + "\nHạn trả: " + localDate(loan.dueAt)
                        + (loan.returnedAt == null ? "" : "\nĐã trả: " + localDate(loan.returnedAt))
                        + "\nGia hạn: " + loan.extensionCount + " lần",
                        label + " · SQLite" + ("Online".equals(loan.loanType)
                                ? " (phiếu Online cũ)" : ""));
            }
            for (com.example.libraryonline.data.repository.ReaderHistoryLocalRepository.Fine fine
                    : data.local.fines) {
                fineCount++;
                remaining += fine.remaining();
                StringBuilder detail = new StringBuilder("Mã phạt: ").append(fine.id)
                        .append("\nMã cuốn: ").append(fine.copyId)
                        .append("\nTrễ: ").append(fine.lateDays).append(" ngày")
                        .append("\nNgày ghi nhận: ").append(localDate(fine.createdAt))
                        .append("\nPhải thu: ").append(vnd(fine.amountDue))
                        .append(" · Đã thu: ").append(vnd(fine.amountPaid))
                        .append("\nCòn nợ: ").append(vnd(fine.remaining()));
                if (!fine.payments.isEmpty()) {
                    detail.append("\nThanh toán:");
                    for (com.example.libraryonline.data.repository.ReaderHistoryLocalRepository.Payment payment
                            : fine.payments) {
                        detail.append("\n  • ").append(localDate(payment.receivedAt))
                                .append(" · ").append(vnd(payment.amount))
                                .append(" · ").append(payment.method == null ? "" : payment.method)
                                .append(" · ").append("Confirmed".equals(payment.status)
                                        ? "Đã xác nhận" : "Đã hủy");
                    }
                }
                String status = fine.remaining() <= 0 ? "Đã thanh toán"
                        : fine.amountPaid > 0 ? "Thanh toán một phần" : "Chưa thanh toán";
                historyCard(fines, fine.title == null || fine.title.isEmpty()
                                ? "Phạt trả muộn" : "Phạt trả muộn · " + fine.title,
                        detail.toString(), status + " · SQLite");
            }
        }
        loanStatus.setText(data.localDone && data.cloudDone
                ? "Firestore: " + cloudCount + " phiếu · SQLite trên máy: " + localCount + " chi tiết"
                : "Đang tổng hợp lịch sử từ hai nguồn…");
        if (data.cloudDone && data.cloudError != null)
            loanStatus.append("\nKhông tải được phiếu Firestore: " + data.cloudError);
        if (data.localDone && data.localError != null)
            loanStatus.append("\nKhông đọc được lịch sử SQLite: " + data.localError);
        if (data.localDone && data.cloudDone && loans.getChildCount() == 0)
            historyCard(loans, "Không có bản ghi mượn/trả",
                    "Chưa ghi nhận khoản mượn trong những nguồn đã tải thành công.",
                    "Không có dữ liệu");
        fineStatus.setText(!data.localDone ? "Đang đọc các khoản phạt tại quầy…"
                : data.localError != null ? "Không đọc được khoản phạt: " + data.localError
                : "Phạt trả muộn tại máy: " + fineCount + " khoản · Còn nợ: " + vnd(remaining));
        if (data.localDone && data.local != null && fines.getChildCount() == 0)
            historyCard(fines, "Không có khoản phạt trả muộn",
                    "Chưa ghi nhận khoản phạt trên thiết bị Admin này.", "SQLite");
        TextView summary = screen.findViewById(R.id.adminReaderHistorySummary);
        summary.setText("Đang mượn: " + activeCount + " · Đã trả: " + returnedCount
                + "\nKhoản phạt trả muộn: " + fineCount
                + " · Còn nợ tại máy: " + vnd(remaining)
                + (data.localDone && data.cloudDone ? ""
                : "\nĐang chờ kết quả tải dữ liệu…")
                + (data.localError == null && data.cloudError == null ? ""
                : "\nMột nguồn dữ liệu chưa tải được; số liệu có thể thiếu."));
    }

    private void historyCard(LinearLayout list, String title, String details, String status) {
        View item = getLayoutInflater().inflate(
                R.layout.item_admin_reader_history_entry, list, false);
        ((TextView) item.findViewById(R.id.adminReaderHistoryEntryTitle)).setText(title);
        ((TextView) item.findViewById(R.id.adminReaderHistoryEntryDescription)).setText(details);
        ((TextView) item.findViewById(R.id.adminReaderHistoryEntryFootnote)).setText(status);
        list.addView(item);
    }

    private static long timestampMillis(Timestamp timestamp) {
        return timestamp == null ? 0 : timestamp.toDate().getTime();
    }

    private String cloudDate(Timestamp time) {
        return time == null ? "Chưa có"
                : new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"))
                .format(time.toDate());
    }

    private String localDate(String time) {
        return time == null || time.isEmpty() ? "Chưa có" : time.replace('T', ' ');
    }

    private String vnd(long amount) {
        return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(amount) + " đ";
    }

    private void showConfig() {
        clearContent("Cấu hình thư viện", "Thay đổi hạn mức mượn; các quy tắc khác chỉ đọc như bản thiết kế.");
        View screen = getLayoutInflater().inflate(R.layout.view_admin_config_panel, content, false);
        content.addView(screen);
        TextView source = screen.findViewById(R.id.adminConfigSource);
        EditText maxBooks = screen.findViewById(R.id.adminConfigBooksInput);
        EditText maxDays = screen.findViewById(R.id.adminConfigDaysInput);
        bindNumberStepper(screen, R.id.adminConfigBooksMinus, R.id.adminConfigBooksPlus,
                maxBooks, 1, 20);
        bindNumberStepper(screen, R.id.adminConfigDaysMinus, R.id.adminConfigDaysPlus,
                maxDays, 1, 60);
        TextView holdOnline = screen.findViewById(R.id.adminConfigOnlineHold);
        TextView holdReservation = screen.findViewById(R.id.adminConfigReserve);
        TextView lateFee = screen.findViewById(R.id.adminConfigLateFee);
        TextView multiplier = screen.findViewById(R.id.adminConfigMultiplier);
        Button save = screen.findViewById(R.id.adminConfigSave);
        save.setEnabled(false);



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

    private void bindNumberStepper(View screen, int minusId, int plusId,
                                   EditText field, int min, int max) {
        Button less = screen.findViewById(minusId);
        Button more = screen.findViewById(plusId);
        less.setOnClickListener(v -> field.setText(
                String.valueOf(Math.max(min, parse(field) - 1))));
        more.setOnClickListener(v -> field.setText(
                String.valueOf(Math.min(max, Math.max(min - 1, parse(field)) + 1))));
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
        View screen = getLayoutInflater().inflate(R.layout.view_admin_reports_panel, content, false);
        content.addView(screen);
        TextView status = screen.findViewById(R.id.adminReportStatus);
        TextView collected = screen.findViewById(R.id.adminReportCollected);
        TextView transactionCount = screen.findViewById(R.id.adminReportTransactions);
        TextView outstanding = screen.findViewById(R.id.adminReportDebtTotal);
        LinearLayout bars = screen.findViewById(R.id.adminReportBars);
        TextView recent = screen.findViewById(R.id.adminReportRecent);
        TextView debts = screen.findViewById(R.id.adminReportUnpaid);
        Button refresh = screen.findViewById(R.id.adminReportRefresh);
        final Button[] choices = {
                screen.findViewById(R.id.adminReportPeriod0),
                screen.findViewById(R.id.adminReportPeriod1),
                screen.findViewById(R.id.adminReportPeriod2)
        };


        final int[] period = {0};
        Runnable fetch = () -> {
            final int requestedPeriod = period[0];
            final int request = ++reportGeneration;
            for (int i = 0; i < choices.length; i++) {
                boolean selected = i == requestedPeriod;
                choices[i].setSelected(selected);
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
        String restriction = "pp.paymentStatus='Confirmed' AND p.penaltyType='LateReturn' "
                + "AND substr(pp.receivedAt,1,10)>=? "
                + "AND substr(pp.receivedAt,1,10)<?";
        try (android.database.Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(pp.amountReceived),0),COUNT(*) FROM PenaltyPayments pp "
                        + "JOIN Penalties p ON p.penaltyId=pp.penaltyId WHERE "
                        + restriction, new String[]{from, to})) {
            if (c.moveToFirst()) {
                result.collected = c.getLong(0);
                result.payments = c.getInt(1);
            }
        }
        result.outstanding = sum(db, "SELECT COALESCE(SUM(amountDue-amountPaid),0) "
                + "FROM Penalties WHERE penaltyType='LateReturn' "
                + "AND penaltyStatus IN ('Unpaid','Partial')", null);

        Calendar days = Calendar.getInstance();
        days.add(Calendar.DAY_OF_MONTH, -6);
        String[] keys = new String[7];
        for (int i = 0; i < 7; i++) {
            keys[i] = formatter.format(days.getTime());
            days.add(Calendar.DAY_OF_MONTH, 1);
        }
        try (android.database.Cursor c = db.rawQuery(
                "SELECT substr(pp.receivedAt,1,10),SUM(pp.amountReceived) "
                        + "FROM PenaltyPayments pp JOIN Penalties p ON p.penaltyId=pp.penaltyId "
                        + "WHERE pp.paymentStatus='Confirmed' AND p.penaltyType='LateReturn' "
                        + "AND substr(pp.receivedAt,1,10)>=? "
                        + "AND substr(pp.receivedAt,1,10)<=? "
                        + "GROUP BY substr(pp.receivedAt,1,10)",
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
                        + "WHERE pp.paymentStatus='Confirmed' AND p.penaltyType='LateReturn' "
                        + "ORDER BY pp.receivedAt DESC LIMIT 5", null)) {
            while (c.moveToNext())
                result.recentPayments.add(c.getString(0) + " · Đã thu " + amount.format(c.getLong(1))
                        + " đ · " + c.getString(2));
        }
        try (android.database.Cursor c = db.rawQuery(
                "SELECT COALESCE(NULLIF(u.fullName,''),'Độc giả'),p.amountDue-p.amountPaid,p.daysLate "
                        + "FROM Penalties p LEFT JOIN Users u ON u.userId=p.userId "
                        + "WHERE p.penaltyType='LateReturn' AND p.penaltyStatus IN ('Unpaid','Partial') "
                        + "ORDER BY p.createdAt DESC LIMIT 5", null)) {
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
            View column = getLayoutInflater().inflate(
                    R.layout.item_admin_report_bar, container, false);
            View bar = column.findViewById(R.id.adminReportBarFill);
            int height = amounts[i] <= 0 ? 0 : Math.max(dp(4),
                    (int) Math.round(dp(90) * amounts[i] / (double) max));
            android.view.ViewGroup.LayoutParams barParams = bar.getLayoutParams();
            barParams.height = height;
            bar.setLayoutParams(barParams);
            ((TextView) column.findViewById(R.id.adminReportBarDay))
                    .setText(formatter.format(day.getTime()));
            container.addView(column);
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



    private void setNumeric(EditText view, int value) { view.setText(String.valueOf(value)); }
    private int parse(EditText view) { try { return Integer.parseInt(view.getText().toString().trim()); } catch (Exception e) { return -1; } }
    private int number(DocumentSnapshot doc, String field, int fallback) { Long n = doc.getLong(field); return n == null ? fallback : n.intValue(); }
    private static String value(DocumentSnapshot doc, String field) { String v = doc.getString(field); return v == null ? "" : v; }
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
