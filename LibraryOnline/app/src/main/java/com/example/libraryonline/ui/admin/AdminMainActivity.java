package com.example.libraryonline.ui.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.libraryonline.R;
import com.example.libraryonline.data.local.dao.AdminDashboardStatsDao;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.utils.PasswordVisibilityToggle;
import com.example.libraryonline.data.remote.firebase.BookCatalogueSyncManager;
import com.example.libraryonline.ui.admin.inventory.InventoryActivity;
import com.example.libraryonline.utils.EmailSender;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.security.SecureRandom;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AdminMainActivity extends com.example.libraryonline.ui.auth.SessionActivity {
    private static final String SECONDARY_APP_NAME = "LibraryOnlineSecondaryAuth";

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private FirebaseAuth secondaryAuth;
    private boolean inventoryOpening;
    private LinearLayout requestsContainer;
    private TextView pendingCount, emptyMessage;
    private ProgressBar progress;
    private ListenerRegistration requestsListener;
    private LinearLayout resetRequestsContainer;
    private TextView resetPendingCount, resetEmptyMessage;
    private ListenerRegistration resetRequestsListener;
    private BookCatalogueSyncManager catalogueSyncManager;
    private Button syncButton;
    private AdminHomeView homeView;
    private CirculationPanelController circulationController;
    private long dashboardGeneration, requestsGeneration;
    private final java.util.concurrent.ExecutorService dashboardLocalWorker =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private String adminUid, adminPhone = "", adminBirthDate = "", adminDisplayName = "";
    private Integer registrationCount, resetCount;
    private boolean registrationCached, resetCached, registrationError, resetError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_admin_main);
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        FirebaseApp secondaryApp;
        try {
            secondaryApp = FirebaseApp.getInstance(SECONDARY_APP_NAME);
        } catch (IllegalStateException notInitialized) {
            secondaryApp =
                    FirebaseApp.initializeApp(
                            this, FirebaseApp.getInstance().getOptions(), SECONDARY_APP_NAME);
        }
        secondaryAuth = FirebaseAuth.getInstance(secondaryApp);
        requestsContainer = findViewById(R.id.containerRegistrationRequests);
        pendingCount = findViewById(R.id.textPendingCount);
        emptyMessage = findViewById(R.id.textEmptyRequests);
        progress = findViewById(R.id.progressRequests);
        resetRequestsContainer = findViewById(R.id.containerPasswordResetRequests);
        resetPendingCount = findViewById(R.id.textPasswordResetPendingCount);
        resetEmptyMessage = findViewById(R.id.textEmptyPasswordResetRequests);
        homeView =
                new AdminHomeView(
                        this,
                        savedInstanceState == null
                                ? getIntent()
                                        .getIntExtra(AdminNavigation.EXTRA_TAB, AdminHomeView.HOME)
                                : savedInstanceState.getInt("adminTab", AdminHomeView.HOME),
                        this::openInventory);
        getIntent().removeExtra(AdminNavigation.EXTRA_TAB);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.adminRoot),
                (view, insets) -> {
                    androidx.core.graphics.Insets bars =
                            insets.getInsets(
                                    androidx.core.view.WindowInsetsCompat.Type.systemBars());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return androidx.core.view.WindowInsetsCompat.CONSUMED;
                });
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new androidx.activity.OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                if (homeView.selectedTab() != AdminHomeView.HOME)
                                    homeView.show(AdminHomeView.HOME);
                                else {
                                    setEnabled(false);
                                    getOnBackPressedDispatcher().onBackPressed();
                                    setEnabled(true);
                                }
                            }
                        });
        findViewById(R.id.actionAdminInventory).setOnClickListener(v -> openInventory());
        findViewById(R.id.actionAdminBorrow).setOnClickListener(v -> openCounter());
        findViewById(R.id.actionAdminReturn).setOnClickListener(v -> openCounter());
        findViewById(R.id.actionAdminOnline).setOnClickListener(v -> {
            if (isSessionReady())
                Toast.makeText(this,
                        "Giao Online / Đặt trước chưa được hỗ trợ tại quầy. Không tạo giao dịch giả.",
                        Toast.LENGTH_LONG).show();
        });
        findViewById(R.id.buttonAdminAccountBack).setOnClickListener(
                v -> homeView.show(AdminHomeView.HOME));
        bindCatalogueSyncButton();
        findViewById(R.id.actionAdminSync)
                .setOnClickListener(
                        v -> {
                            if (isSessionReady() && syncButton.isEnabled())
                                syncButton.performClick();
                        });
        findViewById(R.id.buttonAdminRefresh)
                .setOnClickListener(
                        v -> {
                            if (isSessionReady()) {
                                loadDashboard();
                                stopRequestListeners();
                                startRequests();
                            }
                        });
        findViewById(R.id.buttonAdminRefreshRequests)
                .setOnClickListener(
                        v -> {
                            if (!isSessionReady()) return;
                            stopRequestListeners();
                            startRequests();
                        });
        findViewById(R.id.actionAdminReaders).setOnClickListener(v -> openManagementSection("readers"));
        findViewById(R.id.actionAdminConfig).setOnClickListener(v -> openManagementSection("config"));
        findViewById(R.id.actionAdminReports).setOnClickListener(v -> openManagementSection("reports"));
        findViewById(R.id.buttonAdminLogout).setOnClickListener(v -> logoutSession());
        findViewById(R.id.buttonAdminEditProfile).setOnClickListener(v -> showEditAdminContact());
        findViewById(R.id.buttonAdminChangePassword).setOnClickListener(v -> {
            if (isSessionReady())
                startActivity(new android.content.Intent(this, AdminPasswordActivity.class));
        });
    }

    private void openManagementSection(String section) {
        if (!isSessionReady()) return;
        android.content.Intent intent = new android.content.Intent(this, AdminManagementActivity.class);
        intent.putExtra("section", section);
        startActivity(intent);
    }

    private void loadAdminProfile(com.example.libraryonline.data.session.SessionProfile profile) {
        if (!isSessionReady()) return;
        firestore.collection("Admins").document(profile.uid).get()
                .addOnSuccessListener(doc -> {
                    if (!isSessionReady() || !profile.uid.equals(adminUid)) return;
                    String name = doc.getString("fullName");
                    String email = doc.getString("email");
                    String phone = doc.getString("phone");
                    String dob = doc.getString("dateOfBirth");
                    if (dob == null || dob.trim().isEmpty()) dob = doc.getString("birthDate");
                    if (dob == null || dob.trim().isEmpty()) dob = doc.getString("dob");
                    adminDisplayName = name == null || name.trim().isEmpty()
                            ? (profile.displayName == null || profile.displayName.trim().isEmpty()
                                    ? "Quản trị viên" : profile.displayName.trim())
                            : name.trim();
                    adminPhone = phone == null ? "" : phone.trim();
                    adminBirthDate = dob == null ? "" : dob.trim();
                    ((TextView) findViewById(R.id.textAdminProfileName)).setText(adminDisplayName);
                    ((TextView) findViewById(R.id.textAdminProfileImmutableName)).setText(adminDisplayName);
                    ((TextView) findViewById(R.id.textAdminSession)).setText("Admin " + adminDisplayName);
                    String initial = adminDisplayName.trim().isEmpty()
                            ? "AD" : adminDisplayName.trim().substring(0, 1).toUpperCase(Locale.ROOT);
                    ((TextView) findViewById(R.id.textAdminProfileAvatar)).setText(initial);
                    ((TextView) findViewById(R.id.buttonAdminAccount)).setText(initial);
                    ((TextView) findViewById(R.id.textAdminProfilePhone)).setText(
                            adminPhone.isEmpty() ? getString(R.string.admin_profile_unavailable) : adminPhone);
                    ((TextView) findViewById(R.id.textAdminProfileDob)).setText(
                            adminBirthDate.isEmpty() ? getString(R.string.admin_profile_unavailable) : adminBirthDate);
                    String authEmail = auth.getCurrentUser() == null ? profile.email : auth.getCurrentUser().getEmail();
                    String shownEmail = email == null || email.trim().isEmpty() ? authEmail : email.trim();
                    ((TextView) findViewById(R.id.textAdminProfileEmail)).setText(
                            shownEmail == null || shownEmail.isEmpty()
                                    ? getString(R.string.admin_profile_unavailable) : shownEmail);
                })
                .addOnFailureListener(error -> {
                    if (!isSessionReady()) return;
                    ((TextView) findViewById(R.id.textAdminProfilePhone))
                            .setText(getString(R.string.admin_profile_unavailable));
                    ((TextView) findViewById(R.id.textAdminProfileDob))
                            .setText(getString(R.string.admin_profile_unavailable));
                    Toast.makeText(this, "Không tải được hồ sơ quản trị từ Firestore.", Toast.LENGTH_LONG).show();
                });
    }

    /** Inline contact fields match Admin Account Figma; email changes require reauthentication. */
    private void showEditAdminContact() {
        if (!isSessionReady()) return;
        EditText phoneField = findViewById(R.id.textAdminProfilePhone);
        EditText emailField = findViewById(R.id.textAdminProfileEmail);
        String phone = phoneField.getText().toString().trim();
        String email = emailField.getText().toString().trim();
        if (!phone.isEmpty() && !phone.matches("^0(3|5|7|8|9)[0-9]{8}$")) {
            phoneField.setError("Số điện thoại phải có 10 số hợp lệ");
            phoneField.requestFocus();
            return;
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailField.setError("Email công vụ không hợp lệ");
            emailField.requestFocus();
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { logoutSession(); return; }
        String oldEmail = user.getEmail() == null ? "" : user.getEmail().trim();
        if (email.equalsIgnoreCase(oldEmail)) {
            saveAdminContact(phone, email, "");
            return;
        }
        if (oldEmail.isEmpty()) {
            Toast.makeText(this, "Tài khoản không có email xác minh. Hãy đăng nhập lại.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        EditText password = new EditText(this);
        password.setHint(R.string.admin_current_password_hint);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSingleLine(true);
        password.setSaveEnabled(false);
        PasswordVisibilityToggle.attach(password);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        password.setPadding(pad, pad / 2, pad, pad / 2);
        privateDialog()
                .setTitle("Xác minh để đổi email")
                .setMessage("Nhập mật khẩu hiện tại trước khi cập nhật email công vụ.")
                .setView(password)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác minh và lưu", (dialog, which) -> {
                    String currentPassword = password.getText().toString();
                    password.setText("");
                    if (currentPassword.isEmpty()) {
                        Toast.makeText(this, "Cần nhập mật khẩu hiện tại để đổi email.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    saveAdminContact(phone, email, currentPassword);
                })
                .show();
    }

    private void saveAdminContact(String phoneValue, String emailValue, String currentPassword) {
        if (!isSessionReady()) return;
        if (!phoneValue.isEmpty() && !phoneValue.matches("^0(3|5|7|8|9)[0-9]{8}$")) {
            Toast.makeText(this, "Số điện thoại phải gồm 10 số hợp lệ.", Toast.LENGTH_LONG).show();
            return;
        }
        if (emailValue.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
            Toast.makeText(this, "Vui lòng nhập email hợp lệ.", Toast.LENGTH_LONG).show();
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || adminUid == null) { logoutSession(); return; }
        String oldEmail = user.getEmail() == null ? "" : user.getEmail().trim();
        String newEmail = emailValue.trim();
        boolean emailChanged = !oldEmail.equalsIgnoreCase(newEmail);
        if (emailChanged && currentPassword.trim().isEmpty()) {
            Toast.makeText(this, "Cần nhập mật khẩu hiện tại để đổi email an toàn.", Toast.LENGTH_LONG).show();
            return;
        }
        final Button saveButton = findViewById(R.id.buttonAdminEditProfile);
        saveButton.setEnabled(false);
        Runnable restoreButton = () -> { if (isSessionReady()) saveButton.setEnabled(true); };
        com.google.android.gms.tasks.Task<Void> authTask;
        if (emailChanged) {
            AuthCredential credential = EmailAuthProvider.getCredential(oldEmail, currentPassword);
            authTask = user.reauthenticate(credential)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) return com.google.android.gms.tasks.Tasks.forException(
                                task.getException() == null ? new IllegalStateException("Re-authentication failed") : task.getException());
                        return user.updateEmail(newEmail);
                    });
        } else {
            authTask = com.google.android.gms.tasks.Tasks.forResult(null);
        }
        authTask.addOnCompleteListener(authResult -> {
            if (!isSessionReady()) return;
            if (!authResult.isSuccessful()) {
                restoreButton.run();
                Toast.makeText(this, emailChanged
                        ? getString(R.string.admin_reauth_failed)
                        : getString(R.string.admin_profile_save_failed), Toast.LENGTH_LONG).show();
                return;
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("phone", phoneValue);
            updates.put("email", newEmail);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            firestore.collection("Admins").document(adminUid).update(updates)
                    .addOnSuccessListener(ignored -> {
                        if (!isSessionReady()) return;
                        adminPhone = phoneValue;
                        ((TextView) findViewById(R.id.textAdminProfilePhone)).setText(
                                phoneValue.isEmpty() ? getString(R.string.admin_profile_unavailable) : phoneValue);
                        ((TextView) findViewById(R.id.textAdminProfileEmail)).setText(newEmail);
                        restoreButton.run();
                        Toast.makeText(this, R.string.admin_profile_saved, Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(error -> {
                        if (!isSessionReady()) return;
                        restoreButton.run();
                        if (emailChanged) {
                            user.updateEmail(oldEmail).addOnCompleteListener(rollback -> {
                                if (!rollback.isSuccessful()) {
                                    Toast.makeText(this,
                                            "Email Auth đã đổi nhưng chưa đồng bộ hồ sơ. Hãy đăng xuất và nhờ quản trị hệ thống sửa hồ sơ Admin.",
                                            Toast.LENGTH_LONG).show();
                                    logoutSession();
                                } else {
                                    Toast.makeText(this, getString(R.string.admin_profile_save_failed),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        } else {
                            Toast.makeText(this, getString(R.string.admin_profile_save_failed),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private void openCounter() {
        if (!isSessionReady()) return;
        homeView.show(AdminHomeView.CIRCULATION);
        findViewById(R.id.inputCirculationReaderBarcode).requestFocus();
    }

    private void openInventory() {
        if (!isSessionReady() || inventoryOpening) return;
        inventoryOpening = true;
        startActivity(new android.content.Intent(this, InventoryActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        inventoryOpening = false;
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        homeView.show(intent.getIntExtra(AdminNavigation.EXTRA_TAB, AdminHomeView.HOME));
        intent.removeExtra(AdminNavigation.EXTRA_TAB);
    }

    @Override
    protected com.example.libraryonline.data.session.SessionProfile.Role expectedRole() {
        return com.example.libraryonline.data.session.SessionProfile.Role.ADMIN;
    }

    @Override
    protected void onSessionAuthorized(
            com.example.libraryonline.data.session.SessionProfile profile) {
        adminUid = profile.uid;
        adminDisplayName = profile.displayName == null ? "Quản trị viên" : profile.displayName;
        ((TextView) findViewById(R.id.textAdminSession)).setText("Admin " + adminDisplayName);
        ((TextView) findViewById(R.id.textAdminProfileName)).setText(adminDisplayName);
        ((TextView) findViewById(R.id.textAdminProfileImmutableName)).setText(adminDisplayName);
        ((TextView) findViewById(R.id.textAdminProfileAvatar)).setText("AD");
        loadAdminProfile(profile);
        if (circulationController != null) circulationController.close();
        circulationController = new CirculationPanelController(this);
        startRequests();
        loadDashboard();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("adminTab", homeView.selectedTab());
        super.onSaveInstanceState(state);
    }

    private void stopRequestListeners() {
        requestsGeneration++;
        if (requestsListener != null) {
            requestsListener.remove();
            requestsListener = null;
        }
        if (resetRequestsListener != null) {
            resetRequestsListener.remove();
            resetRequestsListener = null;
        }
    }

    @Override
    protected void onSessionStopped() {
        dashboardGeneration++;
        stopRequestListeners();
        if (catalogueSyncManager != null) {
            catalogueSyncManager.close();
            catalogueSyncManager = null;
        }
        if (circulationController != null) {
            circulationController.close();
            circulationController = null;
        }
        requestsContainer.removeAllViews();
        resetRequestsContainer.removeAllViews();
        if (syncButton != null) {
            syncButton.setEnabled(true);
            syncButton.setText(R.string.admin_sync);
        }
    }

    private void startRequests() {
        registrationCount = null;
        resetCount = null;
        registrationError = false;
        resetError = false;
        requestsContainer.removeAllViews();
        resetRequestsContainer.removeAllViews();
        progress.setVisibility(View.VISIBLE);
        emptyMessage.setVisibility(View.VISIBLE);
        emptyMessage.setText(R.string.admin_count_loading);
        resetEmptyMessage.setVisibility(View.VISIBLE);
        resetEmptyMessage.setText(R.string.admin_count_loading);
        updateRequestSummary();
        listenForRequests();
        listenForPasswordResetRequests();
    }

    private void updateRequestSummary() {
        ((TextView) findViewById(R.id.textAdminRegistrationCount))
                .setText(registrationCount == null ? "—" : String.valueOf(registrationCount));
        ((TextView) findViewById(R.id.textAdminPendingSummary))
                .setText(
                        registrationError
                                ? getString(R.string.admin_request_error)
                                : registrationCount == null
                                        ? getString(R.string.admin_count_loading)
                                        : registrationCount + " yêu cầu đăng ký chờ duyệt");
        ((TextView) findViewById(R.id.textAdminResetSummary))
                .setText(
                        resetError
                                ? getString(R.string.admin_request_error)
                                : resetCount == null
                                        ? getString(R.string.admin_count_loading)
                                        : resetCount + " yêu cầu khôi phục mật khẩu");
        ((TextView) findViewById(R.id.textAdminRequestsStatus))
                .setText(
                        registrationError || resetError
                                ? R.string.admin_request_error
                                : registrationCount == null || resetCount == null
                                        ? R.string.admin_count_loading
                                        : registrationCached || resetCached
                                                ? R.string.admin_cached_requests
                                                : R.string.admin_live_requests);
    }

    private void loadDashboard() {
        final long request = ++dashboardGeneration;
        loadLocalDashboard(request);
        Button refresh = findViewById(R.id.buttonAdminRefresh);
        refresh.setEnabled(false);
        TextView status = findViewById(R.id.textAdminDataStatus);
        status.setText(R.string.admin_loading);
        ((TextView) findViewById(R.id.textAdminBrand)).setText("Đang cập nhật số liệu...");
        int[] ids = {R.id.textAdminBookCount, R.id.textAdminReaderCount, R.id.textAdminCopyCount};
        for (int id : ids) ((TextView) findViewById(id)).setText(R.string.admin_dash);
        java.util.List<
                        com.google.android.gms.tasks.Task<
                                com.google.firebase.firestore.AggregateQuerySnapshot>>
                tasks =
                        java.util.Arrays.asList(
                                firestore
                                        .collection("BookTitles")
                                        .count()
                                        .get(com.google.firebase.firestore.AggregateSource.SERVER),
                                firestore
                                        .collection("Users")
                                        .whereEqualTo("role", "Reader")
                                        .count()
                                        .get(com.google.firebase.firestore.AggregateSource.SERVER),
                                firestore
                                        .collection("BookCopies")
                                        .count()
                                        .get(com.google.firebase.firestore.AggregateSource.SERVER));
        com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                .addOnCompleteListener(
                        done -> {
                            if (!isSessionReady() || request != dashboardGeneration) return;
                            boolean failed = false;
                            for (int i = 0; i < tasks.size(); i++) {
                                if (tasks.get(i).isSuccessful())
                                    ((TextView) findViewById(ids[i]))
                                            .setText(
                                                    java.text.NumberFormat.getIntegerInstance(
                                                                    new Locale("vi", "VN"))
                                                            .format(
                                                                    tasks.get(i)
                                                                            .getResult()
                                                                            .getCount()));
                                else failed = true;
                            }
                            refresh.setEnabled(true);
                            status.setText(
                                    failed
                                            ? getString(R.string.admin_load_error)
                                            : getString(
                                                    R.string.admin_updated,
                                                    new java.text.SimpleDateFormat(
                                                                    "HH:mm", Locale.getDefault())
                                                            .format(new Date())));
                            ((TextView) findViewById(R.id.textAdminBrand)).setText(
                                    failed ? "Một số số liệu chưa đồng bộ" : "Trực tuyến · Đã cập nhật tổng quan");
                        });
    }

    /** Refresh local stats off the UI thread. Cloud counts remain in loadDashboard(). */
    private void loadLocalDashboard(long request) {
        ((TextView) findViewById(R.id.textAdminLocalStatus))
                .setText(R.string.admin_screen_data_loading);
        int[] ids = { R.id.textAdminTodayBorrow, R.id.textAdminTodayReturn,
                R.id.textAdminTodayWaiting, R.id.textAdminTodayOverdue };
        for (int id : ids)
            ((TextView) findViewById(id)).setText(R.string.admin_dash);
        ((TextView) findViewById(R.id.textAdminRecentActivity))
                .setText(R.string.admin_screen_data_loading);
        dashboardLocalWorker.execute(() -> {
            AdminDashboardStatsDao.Summary summary = null;
            String failure = null;
            DatabaseHelper helper = null;
            try {
                helper = new DatabaseHelper(getApplicationContext());
                summary = new AdminDashboardStatsDao(helper.getReadableDatabase())
                        .load(java.util.Calendar.getInstance());
            } catch (Exception error) {
                failure = error.getClass().getSimpleName();
            } finally {
                if (helper != null) helper.close();
            }
            final AdminDashboardStatsDao.Summary value = summary;
            final String failedReason = failure;
            runOnUiThread(() -> {
                if (!isSessionReady() || request != dashboardGeneration
                        || isFinishing() || isDestroyed()) return;
                if (value == null) {
                    ((TextView) findViewById(R.id.textAdminLocalStatus))
                            .setText(getString(R.string.admin_screen_data_error)
                                    + (failedReason == null ? "" : " (" + failedReason + ")"));
                    ((TextView) findViewById(R.id.textAdminRecentActivity))
                            .setText(R.string.admin_screen_recent_empty);
                    return;
                }
                int[] counts = {value.borrowedToday, value.returnedToday,
                        value.awaitingPickup, value.overdue};
                for (int i = 0; i < ids.length; i++)
                    ((TextView) findViewById(ids[i])).setText(
                            java.text.NumberFormat.getIntegerInstance(new Locale("vi", "VN"))
                                    .format(counts[i]));
                ((AdminWeeklyTrendView) findViewById(R.id.adminWeeklyTrend))
                        .setCounts(value.dailyBorrowed);
                ((TextView) findViewById(R.id.textAdminLocalStatus))
                        .setText(R.string.admin_screen_local_note);
                ((TextView) findViewById(R.id.textAdminRecentActivity))
                        .setText(value.recentActivities.isEmpty()
                                ? getString(R.string.admin_screen_recent_empty)
                                : android.text.TextUtils.join("\n", value.recentActivities));
            });
        });
    }

    private void bindCatalogueSyncButton() {
        syncButton = findViewById(R.id.buttonAdminSync);
        syncButton.setOnClickListener(
                v ->
                        privateDialog()
                                .setTitle(R.string.admin_sync)
                                .setMessage(
                                        "Tải danh mục Firebase về thiết bị. Thêm và thay đổi sách"
                                                + " qua mục Quản lý kho để giữ số lượng nhất quán.")
                                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                                .setPositiveButton(
                                        "Đồng bộ",
                                        (dialog, which) -> {
                                            if (!isSessionReady()) return;
                                            syncButton.setEnabled(false);
                                            syncButton.setText("Đang đồng bộ...");
                                            if (catalogueSyncManager != null)
                                                catalogueSyncManager.close();
                                            catalogueSyncManager =
                                                    new BookCatalogueSyncManager(
                                                            getApplicationContext());
                                            BookCatalogueSyncManager.SyncListener syncListener =
                                                    new BookCatalogueSyncManager.SyncListener() {
                                                        @Override
                                                        public void onSyncCompleted(
                                                                int changedRows) {
                                                            runOnUiThread(
                                                                    () -> {
                                                                        if (!isSessionReady()
                                                                                || isFinishing()
                                                                                || isDestroyed())
                                                                            return;
                                                                        syncButton.setEnabled(true);
                                                                        syncButton.setText(
                                                                                R.string
                                                                                        .admin_sync);
                                                                        Toast.makeText(
                                                                                        AdminMainActivity
                                                                                                .this,
                                                                                        "Đồng bộ"
                                                                                            + " hoàn"
                                                                                            + " tất."
                                                                                            + " Số bản"
                                                                                            + " ghi xử"
                                                                                            + " lý: "
                                                                                                + changedRows,
                                                                                        Toast
                                                                                                .LENGTH_LONG)
                                                                                .show();
                                                                    });
                                                        }

                                                        @Override
                                                        public void onSyncFailed(String message) {
                                                            runOnUiThread(
                                                                    () -> {
                                                                        if (!isSessionReady()
                                                                                || isFinishing()
                                                                                || isDestroyed())
                                                                            return;
                                                                        syncButton.setEnabled(true);
                                                                        syncButton.setText(
                                                                                R.string
                                                                                        .admin_sync);
                                                                        Toast.makeText(
                                                                                        AdminMainActivity
                                                                                                .this,
                                                                                        "Đồng bộ"
                                                                                            + " thất"
                                                                                            + " bại:"
                                                                                            + " "
                                                                                                + message,
                                                                                        Toast
                                                                                                .LENGTH_LONG)
                                                                                .show();
                                                                    });
                                                        }
                                                    };
                                            catalogueSyncManager.start(syncListener);
                                        })
                                .show());
    }

    private void listenForRequests() {
        final long generation = requestsGeneration;
        requestsListener =
                firestore
                        .collection("RegistrationRequests")
                        .addSnapshotListener(
                                com.google.firebase.firestore.MetadataChanges.INCLUDE,
                                (snapshot, error) -> {
                                    if (!isSessionReady() || generation != requestsGeneration)
                                        return;
                                    progress.setVisibility(View.GONE);
                                    if (error != null) {
                                        registrationError = true;
                                        registrationCount = null;
                                        requestsContainer.removeAllViews();
                                        updateRequestSummary();
                                        emptyMessage.setVisibility(View.VISIBLE);
                                        emptyMessage.setText(R.string.admin_request_error);
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
                                    registrationError = false;
                                    registrationCount = count;
                                    registrationCached = snapshot.getMetadata().isFromCache();
                                    updateRequestSummary();
                                    pendingCount.setText("Đăng ký thành viên (" + count + ")");
                                    emptyMessage.setVisibility(
                                            count == 0 ? View.VISIBLE : View.GONE);
                                    if (count == 0)
                                        emptyMessage.setText(
                                                "Hiện chưa có yêu cầu đăng ký đang chờ duyệt.");
                                });
    }

    private void listenForPasswordResetRequests() {
        final long generation = requestsGeneration;
        resetRequestsListener =
                firestore
                        .collection("PasswordResetRequests")
                        .addSnapshotListener(
                                com.google.firebase.firestore.MetadataChanges.INCLUDE,
                                (snapshot, error) -> {
                                    if (!isSessionReady() || generation != requestsGeneration)
                                        return;
                                    if (error != null) {
                                        resetError = true;
                                        resetCount = null;
                                        resetRequestsContainer.removeAllViews();
                                        updateRequestSummary();
                                        resetEmptyMessage.setVisibility(View.VISIBLE);
                                        resetEmptyMessage.setText(R.string.admin_request_error);
                                        return;
                                    }
                                    if (snapshot == null) return;
                                    resetRequestsContainer.removeAllViews();
                                    int count = 0;
                                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                                        if (!"Pending".equalsIgnoreCase(doc.getString("status")))
                                            continue;
                                        count++;
                                        resetRequestsContainer.addView(
                                                createPasswordResetCard(doc));
                                    }
                                    resetError = false;
                                    resetCount = count;
                                    resetCached = snapshot.getMetadata().isFromCache();
                                    updateRequestSummary();
                                    resetPendingCount.setText("Khôi phục mật khẩu (" + count + ")");
                                    resetEmptyMessage.setVisibility(
                                            count == 0 ? View.VISIBLE : View.GONE);
                                    if (count == 0)
                                        resetEmptyMessage.setText(
                                                "Hiện chưa có yêu cầu quên mật khẩu đang chờ"
                                                        + " duyệt.");
                                });
    }

    private View createPasswordResetCard(DocumentSnapshot doc) {
        View card =
                bindRequestCard(
                        doc,
                        resetRequestsContainer,
                        "CHỜ XÁC MINH",
                        value(doc, "birthDate", "Chưa cung cấp"));
        card.findViewById(R.id.requestCreatedAt).setVisibility(View.GONE);
        Button reject = card.findViewById(R.id.requestReject);
        Button approve = card.findViewById(R.id.requestApprove);
        approve.setText("Duyệt & gửi email");
        reject.setOnClickListener(v -> showPasswordResetRejectDialog(doc, reject, approve));
        approve.setOnClickListener(v -> approvePasswordResetRequest(doc, reject, approve));
        return card;
    }

    private void approvePasswordResetRequest(
            DocumentSnapshot request, Button reject, Button approve) {
        String email = value(request, "email", "").trim().toLowerCase(Locale.ROOT);
        String name = value(request, "fullName", "").trim().replaceAll("\\s+", " ");
        String birthDate = value(request, "birthDate", "").trim();
        String phone = value(request, "phone", "").trim();
        if (email.isEmpty() || name.isEmpty() || birthDate.isEmpty() || phone.isEmpty()) {
            failResetAction(reject, approve, "Yêu cầu thiếu thông tin xác minh.");
            return;
        }

        privateDialog()
                .setTitle("Duyệt yêu cầu quên mật khẩu")
                .setMessage(
                        "Chỉ duyệt nếu họ tên, ngày sinh và số điện thoại khớp hoàn toàn với hồ sơ"
                                + " độc giả. Firebase sẽ gửi email đặt lại mật khẩu đến "
                                + email
                                + ".")
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton(
                        "Xác minh và gửi",
                        (dialog, which) -> {
                            if (!isSessionReady()) return;
                            setResetActionsEnabled(reject, approve, false);
                            firestore
                                    .collection("Users")
                                    .whereEqualTo("email", email)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener(
                                            matches -> {
                                                if (matches.isEmpty()) {
                                                    failResetAction(
                                                            reject,
                                                            approve,
                                                            "Không tìm thấy tài khoản độc giả khớp"
                                                                    + " email.");
                                                    return;
                                                }
                                                DocumentSnapshot user =
                                                        matches.getDocuments().get(0);
                                                String storedName =
                                                        value(user, "fullName", "")
                                                                .trim()
                                                                .replaceAll("\\s+", " ");
                                                String storedBirth =
                                                        value(
                                                                        user,
                                                                        "dateOfBirth",
                                                                        value(
                                                                                user,
                                                                                "birthDate",
                                                                                ""))
                                                                .trim();
                                                String storedPhone =
                                                        value(user, "phone", "").trim();
                                                if (!name.equalsIgnoreCase(storedName)
                                                        || !birthDate.equals(storedBirth)
                                                        || !phone.equals(storedPhone)
                                                        || !"Reader"
                                                                .equalsIgnoreCase(
                                                                        value(user, "role", ""))) {
                                                    failResetAction(
                                                            reject,
                                                            approve,
                                                            "Thông tin không khớp hồ sơ độc giả."
                                                                    + " Chưa gửi email.");
                                                    return;
                                                }

                                                secondaryAuth
                                                        .sendPasswordResetEmail(email)
                                                        .addOnSuccessListener(
                                                                unused ->
                                                                        firestore
                                                                                .collection(
                                                                                        "PasswordResetRequests")
                                                                                .document(
                                                                                        request
                                                                                                .getId())
                                                                                .update(
                                                                                        "status",
                                                                                        "Approved",
                                                                                        "rejectionReason",
                                                                                        "",
                                                                                        "reviewedBy",
                                                                                        auth
                                                                                                                .getCurrentUser()
                                                                                                        == null
                                                                                                ? ""
                                                                                                : auth.getCurrentUser()
                                                                                                        .getUid(),
                                                                                        "reviewedAt",
                                                                                        FieldValue
                                                                                                .serverTimestamp())
                                                                                .addOnSuccessListener(
                                                                                        done -> {
                                                                                            setResetActionsEnabled(
                                                                                                    reject,
                                                                                                    approve,
                                                                                                    true);
                                                                                            Toast
                                                                                                    .makeText(
                                                                                                            this,
                                                                                                            "Đã xác"
                                                                                                                + " minh"
                                                                                                                + " và yêu"
                                                                                                                + " cầu Firebase"
                                                                                                                + " gửi email"
                                                                                                                + " đặt lại"
                                                                                                                + " mật khẩu.",
                                                                                                            Toast
                                                                                                                    .LENGTH_LONG)
                                                                                                    .show();
                                                                                        })
                                                                                .addOnFailureListener(
                                                                                        error ->
                                                                                                failResetAction(
                                                                                                        reject,
                                                                                                        approve,
                                                                                                        "Firebase"
                                                                                                            + " đã gửi"
                                                                                                            + " email"
                                                                                                            + " nhưng"
                                                                                                            + " không"
                                                                                                            + " cập nhật"
                                                                                                            + " được"
                                                                                                            + " trạng"
                                                                                                            + " thái"
                                                                                                            + " yêu cầu:"
                                                                                                            + " "
                                                                                                                + error
                                                                                                                        .getLocalizedMessage())))
                                                        .addOnFailureListener(
                                                                error ->
                                                                        failResetAction(
                                                                                reject,
                                                                                approve,
                                                                                "Firebase chưa gửi"
                                                                                    + " được email"
                                                                                    + " đặt lại mật"
                                                                                    + " khẩu: "
                                                                                        + error
                                                                                                .getLocalizedMessage()));
                                            })
                                    .addOnFailureListener(
                                            error ->
                                                    failResetAction(
                                                            reject,
                                                            approve,
                                                            "Không xác minh được hồ sơ: "
                                                                    + error.getLocalizedMessage()));
                        })
                .show();
    }

    private void showPasswordResetRejectDialog(
            DocumentSnapshot doc, Button reject, Button approve) {
        EditText reasonInput =
                (EditText)
                        getLayoutInflater().inflate(R.layout.dialog_admin_rejection_reason, null);
        privateDialog()
                .setTitle("Từ chối yêu cầu quên mật khẩu")
                .setView(reasonInput)
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton(
                        "Xác nhận từ chối",
                        (dialog, which) -> {
                            if (!isSessionReady()) return;
                            String reason = reasonInput.getText().toString().trim();
                            if (reason.length() < 3 || reason.length() > 500) {
                                Toast.makeText(
                                                this,
                                                "Lý do từ chối phải từ 3 đến 500 ký tự.",
                                                Toast.LENGTH_LONG)
                                        .show();
                                return;
                            }
                            setResetActionsEnabled(reject, approve, false);
                            firestore
                                    .collection("PasswordResetRequests")
                                    .document(doc.getId())
                                    .update(
                                            "status",
                                            "Rejected",
                                            "rejectionReason",
                                            reason,
                                            "reviewedBy",
                                            auth.getCurrentUser() == null
                                                    ? ""
                                                    : auth.getCurrentUser().getUid(),
                                            "reviewedAt",
                                            FieldValue.serverTimestamp())
                                    .addOnSuccessListener(
                                            unused -> {
                                                setResetActionsEnabled(reject, approve, true);
                                                Toast.makeText(
                                                                this,
                                                                "Đã từ chối yêu cầu quên mật khẩu.",
                                                                Toast.LENGTH_SHORT)
                                                        .show();
                                            })
                                    .addOnFailureListener(
                                            error ->
                                                    failResetAction(
                                                            reject,
                                                            approve,
                                                            "Không thể từ chối yêu cầu: "
                                                                    + error.getLocalizedMessage()));
                        })
                .show();
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

    private boolean isPending(String status) {
        return "Pending".equalsIgnoreCase(status) || "PendingVerification".equalsIgnoreCase(status);
    }

    private View createRequestCard(DocumentSnapshot doc) {
        View card =
                bindRequestCard(
                        doc,
                        requestsContainer,
                        "CHỜ DUYỆT",
                        value(doc, "birthDate", value(doc, "dateOfBirth", "Chưa cung cấp")));
        Object createdAt = doc.get("createdAt");
        String created = "Thời gian gửi: Chưa rõ";
        if (createdAt instanceof com.google.firebase.Timestamp) {
            Date date = ((com.google.firebase.Timestamp) createdAt).toDate();
            created =
                    "Thời gian gửi: "
                            + DateFormat.getDateTimeInstance(
                                            DateFormat.SHORT,
                                            DateFormat.SHORT,
                                            new Locale("vi", "VN"))
                                    .format(date);
        }
        ((TextView) card.findViewById(R.id.requestCreatedAt)).setText(created);
        Button reject = card.findViewById(R.id.requestReject);
        Button approve = card.findViewById(R.id.requestApprove);
        approve.setText("Duyệt hồ sơ");
        reject.setOnClickListener(v -> showRejectDialog(doc, reject, approve));
        approve.setOnClickListener(v -> approveRequest(doc, reject, approve));
        return card;
    }

    private void showRejectDialog(DocumentSnapshot doc, Button reject, Button approve) {
        EditText reasonInput =
                (EditText)
                        getLayoutInflater().inflate(R.layout.dialog_admin_rejection_reason, null);
        privateDialog()
                .setTitle("Từ chối hồ sơ")
                .setMessage("Lý do sẽ được lưu cùng yêu cầu đăng ký và gửi email thông báo.")
                .setView(reasonInput)
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton(
                        "Xác nhận từ chối",
                        (dialog, which) -> {
                            if (!isSessionReady()) return;
                            String reason = reasonInput.getText().toString().trim();
                            if (reason.length() < 3 || reason.length() > 500) {
                                Toast.makeText(
                                                this,
                                                "Lý do từ chối phải từ 3 đến 500 ký tự.",
                                                Toast.LENGTH_LONG)
                                        .show();
                                return;
                            }
                            setCardActionsEnabled(reject, approve, false);
                            Map<String, Object> changes = new HashMap<>();
                            changes.put("status", "Rejected");
                            changes.put("rejectionReason", reason);
                            changes.put(
                                    "reviewedBy",
                                    auth.getCurrentUser() == null
                                            ? ""
                                            : auth.getCurrentUser().getUid());
                            changes.put("reviewedAt", FieldValue.serverTimestamp());
                            firestore
                                    .collection("RegistrationRequests")
                                    .document(doc.getId())
                                    .update(changes)
                                    .addOnSuccessListener(
                                            unused -> {
                                                String email = value(doc, "email", "").trim();
                                                String fullName = value(doc, "fullName", "bạn");
                                                EmailSender.send(
                                                        email,
                                                        "Library Online - Kết quả đăng ký",
                                                        "Xin chào "
                                                                + fullName
                                                                + ",\n\n"
                                                                + "Rất tiếc, yêu cầu đăng ký thư"
                                                                + " viện của bạn chưa được chấp"
                                                                + " thuận.\n"
                                                                + "Lý do: "
                                                                + reason
                                                                + "\n\n"
                                                                + "Bạn có thể liên hệ thư viện để"
                                                                + " được hỗ trợ.\n"
                                                                + "Đây là email tự động, vui lòng"
                                                                + " không trả lời.",
                                                        (success, message) -> {
                                                            if (!success)
                                                                Toast.makeText(
                                                                                this,
                                                                                "Đã lưu từ chối"
                                                                                    + " nhưng gửi"
                                                                                    + " email thất"
                                                                                    + " bại: "
                                                                                        + message,
                                                                                Toast.LENGTH_LONG)
                                                                        .show();
                                                        });
                                                Toast.makeText(
                                                                this,
                                                                "Đã lưu kết quả từ chối.",
                                                                Toast.LENGTH_LONG)
                                                        .show();
                                            })
                                    .addOnFailureListener(
                                            error -> {
                                                setCardActionsEnabled(reject, approve, true);
                                                Toast.makeText(
                                                                this,
                                                                "Không thể từ chối: "
                                                                        + error
                                                                                .getLocalizedMessage(),
                                                                Toast.LENGTH_LONG)
                                                        .show();
                                            });
                        })
                .show();
    }

    private void approveRequest(DocumentSnapshot doc, Button reject, Button approve) {
        String email = value(doc, "email", "").trim().toLowerCase(Locale.ROOT);
        String phone = value(doc, "phone", "").trim();
        String fullName = value(doc, "fullName", "").trim();
        if (email.isEmpty() || phone.isEmpty() || fullName.isEmpty()) {
            Toast.makeText(this, "Hồ sơ thiếu họ tên, email hoặc số điện thoại.", Toast.LENGTH_LONG)
                    .show();
            return;
        }

        privateDialog()
                .setTitle("Duyệt hồ sơ thành viên")
                .setMessage(
                        "Duyệt hồ sơ cho "
                                + email
                                + ". Tài khoản dùng mật khẩu tạm ban đầu và bắt buộc đổi ở lần đăng"
                                + " nhập đầu tiên. Tiếp tục?")
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton(
                        "Duyệt",
                        (dialog, which) -> {
                            if (!isSessionReady()) return;
                            setCardActionsEnabled(reject, approve, false);
                            checkDuplicatesThenCreate(doc, fullName, email, phone, reject, approve);
                        })
                .show();
    }

    private void checkDuplicatesThenCreate(
            DocumentSnapshot doc,
            String fullName,
            String email,
            String phone,
            Button reject,
            Button approve) {
        firestore
                .collection("Users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(
                        emailMatches -> {
                            if (!emailMatches.isEmpty()) {
                                failAction(
                                        reject, approve, "Email đã được sử dụng bởi một độc giả.");
                                return;
                            }
                            firestore
                                    .collection("Users")
                                    .whereEqualTo("phone", phone)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener(
                                            phoneMatches -> {
                                                if (!phoneMatches.isEmpty()) {
                                                    failAction(
                                                            reject,
                                                            approve,
                                                            "Số điện thoại đã được sử dụng.");
                                                    return;
                                                }
                                                createApprovedAccount(
                                                        doc, fullName, email, phone, reject,
                                                        approve);
                                            })
                                    .addOnFailureListener(
                                            error ->
                                                    failAction(
                                                            reject,
                                                            approve,
                                                            "Không kiểm tra được số điện thoại: "
                                                                    + error.getLocalizedMessage()));
                        })
                .addOnFailureListener(
                        error ->
                                failAction(
                                        reject,
                                        approve,
                                        "Không kiểm tra được email: "
                                                + error.getLocalizedMessage()));
    }

    private void createApprovedAccount(
            DocumentSnapshot request,
            String fullName,
            String email,
            String phone,
            Button reject,
            Button approve) {
        String requestEmail = value(request, "email", "").trim();
        if (!email.equalsIgnoreCase(requestEmail)) {
            failAction(reject, approve, "Email hồ sơ không khớp.");
            return;
        }

        // Chỉ tạo tài khoản Firebase Auth sau khi admin đã bấm duyệt.
        // Mỗi độc giả nhận một mật khẩu tạm ngẫu nhiên, không dùng chung mật khẩu cố định.
        String temporaryPassword = generateTemporaryPassword();
        secondaryAuth
                .createUserWithEmailAndPassword(email, temporaryPassword)
                .addOnSuccessListener(
                        result -> {
                            FirebaseUser createdUser = result.getUser();
                            if (createdUser == null) {
                                secondaryAuth.signOut();
                                failAction(
                                        reject,
                                        approve,
                                        "Firebase không trả về tài khoản vừa tạo.");
                                return;
                            }
                            String uid = createdUser.getUid();
                            String barcode =
                                    "TV-"
                                            + uid.substring(0, Math.min(8, uid.length()))
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
                            requestChanges.put(
                                    "reviewedBy",
                                    auth.getCurrentUser() == null
                                            ? ""
                                            : auth.getCurrentUser().getUid());
                            requestChanges.put("reviewedAt", FieldValue.serverTimestamp());
                            requestChanges.put("userId", uid);
                            requestChanges.put("barcode", barcode);

                            WriteBatch batch = firestore.batch();
                            batch.set(firestore.collection("Users").document(uid), userData);
                            batch.update(
                                    firestore
                                            .collection("RegistrationRequests")
                                            .document(request.getId()),
                                    requestChanges);
                            batch.commit()
                                    .addOnSuccessListener(
                                            unused -> {
                                                secondaryAuth.signOut();
                                                setCardActionsEnabled(reject, approve, true);
                                                EmailSender.send(
                                                        email,
                                                        "Library Online - Hồ sơ đã được duyệt",
                                                        "Xin chào "
                                                                + fullName
                                                                + ",\n\n"
                                                                + "Hồ sơ đăng ký thư viện của bạn"
                                                                + " đã được duyệt.\n"
                                                                + "Thông tin đăng nhập:\n"
                                                                + "Email: "
                                                                + email
                                                                + "\n"
                                                                + "Mật khẩu tạm: "
                                                                + temporaryPassword
                                                                + "\n\n"
                                                                + "Vì lý do bảo mật, vui lòng đổi"
                                                                + " mật khẩu ngay ở lần đăng nhập"
                                                                + " đầu tiên.\n\n"
                                                                + "Đây là email tự động, vui lòng"
                                                                + " không trả lời.",
                                                        (success, message) -> {
                                                            if (!success)
                                                                Toast.makeText(
                                                                                this,
                                                                                "Đã tạo tài khoản"
                                                                                    + " và duyệt hồ"
                                                                                    + " sơ nhưng"
                                                                                    + " gửi email"
                                                                                    + " thất bại: "
                                                                                        + message,
                                                                                Toast.LENGTH_LONG)
                                                                        .show();
                                                        });
                                                Toast.makeText(
                                                                this,
                                                                "Đã duyệt hồ sơ và tạo tài khoản"
                                                                    + " Firebase. Mật khẩu tạm đã"
                                                                    + " được gửi qua email.",
                                                                Toast.LENGTH_LONG)
                                                        .show();
                                            })
                                    .addOnFailureListener(
                                            error -> {
                                                // Nếu không lưu được hồ sơ Firestore, xóa tài khoản
                                                // Auth vừa tạo
                                                // để lần duyệt lại không bị kẹt vì email đã tồn
                                                // tại.
                                                createdUser
                                                        .delete()
                                                        .addOnCompleteListener(
                                                                deleteTask -> {
                                                                    secondaryAuth.signOut();
                                                                    failAction(
                                                                            reject,
                                                                            approve,
                                                                            "Không thể lưu hồ sơ đã"
                                                                                    + " duyệt: "
                                                                                    + error
                                                                                            .getLocalizedMessage()
                                                                                    + (deleteTask
                                                                                                    .isSuccessful()
                                                                                            ? ""
                                                                                            : ". Không"
                                                                                                  + " xóa được"
                                                                                                  + " tài khoản"
                                                                                                  + " Auth"
                                                                                                  + " tạm;"
                                                                                                  + " cần kiểm"
                                                                                                  + " tra Firebase."));
                                                                });
                                            });
                        })
                .addOnFailureListener(
                        error -> {
                            secondaryAuth.signOut();
                            String detail =
                                    error.getLocalizedMessage() == null
                                            ? ""
                                            : error.getLocalizedMessage();
                            failAction(
                                    reject,
                                    approve,
                                    "Không thể tạo tài khoản Firebase sau khi duyệt. Email có thể"
                                            + " đã có tài khoản Auth. "
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

    private View bindRequestCard(
            DocumentSnapshot doc, LinearLayout parent, String state, String birthDate) {
        View card =
                getLayoutInflater().inflate(R.layout.item_admin_approval_request, parent, false);
        ((TextView) card.findViewById(R.id.requestName))
                .setText(value(doc, "fullName", "Chưa có họ tên"));
        ((TextView) card.findViewById(R.id.requestStatus)).setText(state);
        ((TextView) card.findViewById(R.id.requestBirthDate)).setText("Ngày sinh: " + birthDate);
        ((TextView) card.findViewById(R.id.requestEmail))
                .setText("Email: " + value(doc, "email", "Chưa cung cấp"));
        ((TextView) card.findViewById(R.id.requestPhone))
                .setText("Số điện thoại: " + value(doc, "phone", "Chưa cung cấp"));
        return card;
    }

    @Override
    protected void onDestroy() {
        if (requestsListener != null) requestsListener.remove();
        if (resetRequestsListener != null) resetRequestsListener.remove();
        if (catalogueSyncManager != null) catalogueSyncManager.close();
        dashboardLocalWorker.shutdownNow();
        super.onDestroy();
    }
}
