package com.example.libraryonline.ui.reader;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.libraryonline.R;
import com.example.libraryonline.data.local.dao.BookTitleDao;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.model.BookTitle;
import com.example.libraryonline.data.model.LoanDetail;
import com.example.libraryonline.data.model.LibraryNotification;
import com.example.libraryonline.data.repository.LoanDetailRepository;
import com.example.libraryonline.data.repository.LibraryNotificationRepository;
import com.example.libraryonline.data.remote.firebase.BookCatalogueSyncManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reader home screen. Layout is defined in activity_reader_main.xml and item_reader_book.xml. This
 * Activity handles profile/catalogue data and user interactions only.
 */
public class ReaderMainActivity extends com.example.libraryonline.ui.auth.SessionActivity {
    private ReaderHomeView homeView;
    private ReaderAccountController accountController;
    private ReaderOnlineLoanController onlineLoanController;
    private ReaderNotificationSettingsController notificationSettings;

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private DatabaseHelper databaseHelper;
    private BookCatalogueSyncManager catalogueSyncManager;
    private LinearLayout categoriesContainer;
    private LinearLayout popularBooksContainer;
    private LinearLayout latestBooksContainer;
    private EditText searchInput;
    private TextView greeting, avatar, popularCount, catalogueStatus;
    private final List<BookTitle> books = new ArrayList<>();
    private final ExecutorService catalogueExecutor = Executors.newSingleThreadExecutor();
    private final CatalogueLoadStatus catalogueLoadStatus = new CatalogueLoadStatus();
    private String selectedCategory = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        setContentView(R.layout.activity_reader_main);

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.readerRoot),
                (view, insets) -> {
                    androidx.core.graphics.Insets bars =
                            insets.getInsets(
                                    androidx.core.view.WindowInsetsCompat.Type.systemBars());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return androidx.core.view.WindowInsetsCompat.CONSUMED;
                });
        homeView =
                new ReaderHomeView(
                        this,
                        savedInstanceState == null
                                ? getIntent()
                                        .getIntExtra(
                                                ReaderNavigation.EXTRA_TAB, ReaderHomeView.HOME)
                                : savedInstanceState.getInt("readerTab", ReaderHomeView.HOME));
        getIntent().removeExtra(ReaderNavigation.EXTRA_TAB);
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new androidx.activity.OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                if (accountController != null && accountController.closeAccountSubscreen()) return;
                                if (homeView.onBack()) return;
                                setEnabled(false);
                                getOnBackPressedDispatcher().onBackPressed();
                                setEnabled(true);
                            }
                        });
        findViewById(R.id.readerAccountLogout)
                .setOnClickListener(
                        v -> {
                            if (isSessionReady()) logoutSession();
                        });
        bindViews();
        bindActions();
        notificationSettings = new ReaderNotificationSettingsController(this);
        accountController = new ReaderAccountController(this);
        onlineLoanController = new ReaderOnlineLoanController(this);
        homeView.setOnTabChangedListener(tab -> {
            if (tab != ReaderHomeView.ACCOUNT) accountController.onLeaveAccount();
            if (isSessionReady()) {
                FirebaseUser user = auth.getCurrentUser();
                if (user != null && (tab == ReaderHomeView.LOANS
                        || tab == ReaderHomeView.NOTIFICATIONS)) {
                    loadReaderLoans(user.getUid());
                    if (tab == ReaderHomeView.NOTIFICATIONS)
                        loadReaderNotifications(user.getUid());
                }
            }
        });
        buildCategories();
    }

    @Override
    protected com.example.libraryonline.data.session.SessionProfile.Role expectedRole() {
        return com.example.libraryonline.data.session.SessionProfile.Role.READER;
    }

    @Override
    protected void onSessionAuthorized(
            com.example.libraryonline.data.session.SessionProfile profile) {
        homeView.bindProfile(profile);
        if (accountController != null) {
            accountController.load();
            accountController.applyCardBrightnessIfVisible();
        }
        loadReaderProfile();
        if (notificationSettings != null) notificationSettings.start(profile.uid);
        if (databaseHelper == null) databaseHelper = new DatabaseHelper(getApplicationContext());
        loadBooksFromSqlite();
        loadReaderLoans(profile.user == null ? null : profile.user.userId);
        loadReaderNotifications(profile.user == null ? null : profile.user.userId);
        if (onlineLoanController != null) onlineLoanController.start(profile.uid);
        catalogueSyncManager = new BookCatalogueSyncManager(getApplicationContext());
        catalogueSyncManager.start(
                new BookCatalogueSyncManager.SyncListener() {
                    @Override
                    public void onSyncCompleted(int changedRows) {
                        runOnUiThread(
                                () -> {
                                    if (isSessionReady() && !isFinishing() && !isDestroyed()) {
                                        loadBooksFromSqlite();
                                    }
                                });
                    }

                    @Override
                    public void onCatalogueSnapshot(boolean fromCache, int documentCount) {
                        runOnUiThread(
                                () -> {
                                    if (isSessionReady() && !isFinishing() && !isDestroyed()) {
                                        catalogueLoadStatus.remoteSnapshot(
                                                fromCache, documentCount);
                                        refreshCatalogueStatus();
                                    }
                                });
                    }

                    @Override
                    public void onSyncFailed(String message) {
                        runOnUiThread(
                                () -> {
                                    if (isSessionReady() && !isFinishing() && !isDestroyed()) {
                                        catalogueLoadStatus.remoteFailed(
                                                safe(message, "Không rõ nguyên nhân"));
                                        refreshCatalogueStatus();
                                    }
                                });
                    }
                });
    }

    private void bindViews() {
        greeting = findViewById(R.id.textReaderGreeting);
        avatar = findViewById(R.id.buttonReaderAccount);
        searchInput = findViewById(R.id.inputBookSearch);
        categoriesContainer = findViewById(R.id.containerReaderCategories);
        popularBooksContainer = findViewById(R.id.containerPopularBooks);
        latestBooksContainer = findViewById(R.id.containerLatestBooks);
        popularCount = findViewById(R.id.textPopularCount);
        catalogueStatus = findViewById(R.id.textReaderCatalogueStatus);
    }

    private void bindActions() {
        searchInput.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        renderBooks();
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
    }

    private void buildCategories() {
        java.util.Map<String, String> names = new java.util.LinkedHashMap<>();
        names.put("", "Tất cả");
        for (BookTitle book : books)
            if (book.categoryId != null && !book.categoryId.isEmpty())
                names.put(book.categoryId, safe(book.category, "Chưa cập nhật"));
        if (!names.containsKey(selectedCategory)) selectedCategory = "";
        categoriesContainer.removeAllViews();
        for (java.util.Map.Entry<String, String> categoryEntry : names.entrySet()) {
            String name = categoryEntry.getValue(), categoryId = categoryEntry.getKey();
            TextView chip =
                    (TextView)
                            getLayoutInflater()
                                    .inflate(
                                            R.layout.item_reader_category,
                                            categoriesContainer,
                                            false);
            chip.setText(name);
            chip.setSelected(categoryId.equals(selectedCategory));
            categoriesContainer.addView(chip);
            chip.setOnClickListener(
                    v -> {
                        selectedCategory = categoryId;
                        buildCategories();
                        renderBooks();
                    });
        }
    }

    private void loadReaderProfile() {
        FirebaseUser current = auth.getCurrentUser();
        if (current == null) {
            goToLogin();
            return;
        }
        firestore
                .collection("Users")
                .document(current.getUid())
                .get()
                .addOnSuccessListener(
                        doc -> {
                            if (!isSessionReady() || !doc.exists()) return;
                            String name = value(doc, "fullName", "Độc giả");
                            String firstName =
                                    name.trim().isEmpty()
                                            ? "Độc giả"
                                            : name.trim()
                                                    .split("\\s+")[
                                                    name.trim().split("\\s+").length - 1];
                            greeting.setText(firstName);
                            String[] parts = name.trim().split("\\s+");
                            String initials =
                                    parts.length > 1
                                            ? (parts[0].substring(0, 1)
                                                    + parts[parts.length - 1].substring(0, 1))
                                            : (parts.length == 1 && !parts[0].isEmpty()
                                                    ? parts[0].substring(0, 1)
                                                    : "TV");
                            avatar.setText(initials.toUpperCase(Locale.ROOT));
                        })
                .addOnFailureListener(e -> showMessage("Không tải được thông tin độc giả."));
    }

    /** Read catalogue rows from SQLite; the UI never renders Firestore snapshots directly. */
    private void loadBooksFromSqlite() {
        if (databaseHelper == null) return;
        catalogueExecutor.execute(
                () -> {
                    final List<BookTitle> local;
                    try {
                        local = new BookTitleDao(databaseHelper.getReadableDatabase()).getAll();
                    } catch (Exception failure) {
                        runOnUiThread(
                                () -> {
                                    if (isSessionReady() && !isFinishing() && !isDestroyed()) {
                                        catalogueLoadStatus.localFailed(
                                                safe(
                                                        failure.getLocalizedMessage(),
                                                        failure.getClass().getSimpleName()));
                                        refreshCatalogueStatus();
                                    }
                                });
                        return;
                    }
                    runOnUiThread(
                            () -> {
                                if (!isSessionReady() || isFinishing() || isDestroyed()) return;
                                books.clear();
                                for (BookTitle book : local) {
                                    if (book != null && !book.isHidden) books.add(book);
                                }
                                catalogueLoadStatus.localLoaded(local.size(), books.size());
                                buildCategories();
                                renderBooks();
                            });
                });
    }

    private void renderBooks() {
        if (popularBooksContainer == null || latestBooksContainer == null) return;
        popularBooksContainer.removeAllViews();
        latestBooksContainer.removeAllViews();

        String query =
                searchInput == null
                        ? ""
                        : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<BookTitle> filtered = new ArrayList<>();
        for (BookTitle doc : books) {
            String title = safe(doc.title, "Chưa có tên sách");
            String author = safe(doc.author, "Chưa rõ tác giả");
            String category = safe(doc.category, "Khác");
            if (!selectedCategory.isEmpty() && !selectedCategory.equals(doc.categoryId)) continue;
            String searchable =
                    (title + " " + author + " " + category + " " + safe(doc.isbn, ""))
                            .toLowerCase(Locale.ROOT);
            if (searchable.contains(query)) filtered.add(doc);
        }

        lastDisplayedCount = filtered.size();
        popularCount.setText(filtered.size() + " đầu sách");
        catalogueStatus.setText(catalogueLoadStatus.describe(books.size(), filtered.size()));
        if (filtered.isEmpty()) {
            TextView empty =
                    (TextView)
                            getLayoutInflater()
                                    .inflate(
                                            R.layout.item_reader_empty,
                                            latestBooksContainer,
                                            false);
            empty.setText(
                    books.isEmpty()
                            ? "Thư viện chưa có sách được công khai."
                            : "Không tìm thấy sách phù hợp với từ khóa hoặc danh mục này.");
            latestBooksContainer.addView(empty);
            return;
        }

        int popularLimit = Math.min(6, filtered.size());
        for (int i = 0; i < popularLimit; i++) {
            View card =
                    createBookCard(
                            filtered.get(i),
                            popularBooksContainer,
                            R.layout.item_reader_popular_book);
            popularBooksContainer.addView(card);
        }
        List<BookTitle> latest = new ArrayList<>(filtered);
        Collections.sort(
                latest,
                (left, right) -> {
                    String leftDate = safe(left.updatedAt, safe(left.createdAt, ""));
                    String rightDate = safe(right.updatedAt, safe(right.createdAt, ""));
                    return rightDate.compareTo(leftDate);
                });
        for (BookTitle doc : latest)
            latestBooksContainer.addView(
                    createBookCard(doc, latestBooksContainer, R.layout.item_reader_book));
    }

    private void loadReaderLoans(String userId) {
        LinearLayout container = findViewById(R.id.containerReaderLoans);
        TextView summary = findViewById(R.id.textReaderLoansSummary);
        if (container == null || summary == null) return;
        if (userId == null || userId.trim().isEmpty()) {
            summary.setText("Không xác định được tài khoản độc giả.");
            return;
        }
        catalogueExecutor.execute(() -> {
            List<LoanDetail> active;
            try (LoanDetailRepository repository = new LoanDetailRepository(getApplicationContext())) {
                active = repository.getActiveByUser(userId);
            } catch (Exception failure) {
                runOnUiThread(() -> summary.setText("Chưa tải được danh sách mượn. Vui lòng thử lại."));
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                container.removeAllViews();
                summary.setText(active.isEmpty()
                        ? "Bạn hiện không có sách đang mượn hoặc quá hạn."
                        : "Bạn đang mượn " + active.size() + " cuốn sách.");
                if (notificationSettings != null) notificationSettings.updateLoans(active);
                for (LoanDetail loan : active) {
                    View card = getLayoutInflater().inflate(
                            R.layout.item_reader_loan_record, container, false);
                    container.addView(card);
                    ((TextView) card.findViewById(R.id.readerLoanReceipt)).setText(
                            "Mã phiếu: " + safe(loan.loanReceiptId, "Chưa cập nhật"));
                    ((TextView) card.findViewById(R.id.readerLoanCopy)).setText(
                            "Mã bản sách: " + safe(loan.copyId, "Chưa cập nhật"));
                    ((TextView) card.findViewById(R.id.readerLoanBorrow)).setText(
                            "Ngày mượn: " + safe(loan.borrowDate, "Chưa cập nhật"));
                    ((TextView) card.findViewById(R.id.readerLoanDue)).setText(
                            "Hạn trả: " + safe(loan.dueDate, "Chưa cập nhật"));
                    ((TextView) card.findViewById(R.id.readerLoanStatus)).setText(
                            "Trạng thái: " + ("QuaHan".equals(loan.detailStatus) ? "Quá hạn" : "Đang mượn"));
                }
            });
        });
    }

    private void loadReaderNotifications(String userId) {
        LinearLayout container = findViewById(R.id.containerReaderNotifications);
        TextView summary = findViewById(R.id.textReaderNotificationsSummary);
        if (container == null || summary == null) return;
        if (userId == null || userId.trim().isEmpty()) {
            summary.setText("Không xác định được tài khoản độc giả.");
            return;
        }
        catalogueExecutor.execute(() -> {
            List<LibraryNotification> notifications;
            try (LibraryNotificationRepository repository = new LibraryNotificationRepository(getApplicationContext())) {
                notifications = repository.getByUser(userId);
            } catch (Exception failure) {
                runOnUiThread(() -> summary.setText("Chưa tải được thông báo. Vui lòng thử lại."));
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                container.removeAllViews();
                int unread = 0;
                for (LibraryNotification notification : notifications)
                    if (notification.readAt == null || notification.readAt.trim().isEmpty()) unread++;
                summary.setText(notifications.isEmpty()
                        ? "Bạn chưa có thông báo nào."
                        : notifications.size() + " thông báo · " + unread + " chưa đọc");
                if (notificationSettings != null) notificationSettings.setLocalUnread(unread);
                for (LibraryNotification notification : notifications) {
                    View card = getLayoutInflater().inflate(
                            R.layout.item_reader_local_notification, container, false);
                    container.addView(card);
                    ((TextView) card.findViewById(R.id.readerLocalNotifTitle)).setText(
                            safe(notification.title, "Thông báo thư viện"));
                    ((TextView) card.findViewById(R.id.readerLocalNotifMessage)).setText(
                            safe(notification.message, "Không có nội dung"));
                    TextView date = card.findViewById(R.id.readerLocalNotifDate);
                    if (notification.scheduledAt == null || notification.scheduledAt.trim().isEmpty()) {
                        date.setVisibility(View.GONE);
                    } else {
                        date.setText(notification.scheduledAt);
                    }
                    TextView markRead = card.findViewById(R.id.readerLocalNotifRead);
                    boolean isUnread = notification.readAt == null || notification.readAt.trim().isEmpty();
                    if (isUnread) {
                        markRead.setOnClickListener(v -> {
                            markRead.setEnabled(false);
                            catalogueExecutor.execute(() -> {
                                boolean updated = false;
                                try (LibraryNotificationRepository repository = new LibraryNotificationRepository(getApplicationContext())) {
                                    updated = repository.markRead(notification.notificationId,
                                            new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
                                                    .format(new java.util.Date())) > 0;
                                } catch (Exception ignored) { }
                                boolean success = updated;
                                runOnUiThread(() -> {
                                    if (success) loadReaderNotifications(userId);
                                    else {
                                        markRead.setEnabled(true);
                                        showMessage("Không thể cập nhật trạng thái thông báo.");
                                    }
                                });
                            });
                        });
                    } else {
                        markRead.setVisibility(View.GONE);
                        card.findViewById(R.id.readerLocalNotifDone).setVisibility(View.VISIBLE);
                    }
                }
            });
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void refreshCatalogueStatus() {
        if (catalogueStatus != null)
            catalogueStatus.setText(catalogueLoadStatus.describe(books.size(), lastDisplayedCount));
    }

    private int lastDisplayedCount;

    private View createBookCard(BookTitle doc, android.view.ViewGroup parent, int layout) {
        View card = LayoutInflater.from(this).inflate(layout, parent, false);
        TextView title = card.findViewById(R.id.textReaderBookTitle);
        TextView author = card.findViewById(R.id.textReaderBookAuthor);
        TextView category = card.findViewById(R.id.textReaderBookCategory);
        TextView availability = card.findViewById(R.id.textReaderBookAvailability);
        ImageView cover = card.findViewById(R.id.imageReaderBookCover);
        TextView fallback = card.findViewById(R.id.textReaderCoverFallback);

        title.setText(safe(doc.title, "Chưa có tên sách"));
        author.setText(safe(doc.author, "Chưa rõ tác giả"));
        category.setText(safe(doc.category, "Khác").toUpperCase(Locale.ROOT));
        long available = doc.availableCopies;
        long total = doc.totalCopies;
        availability.setText(
                available > 0 ? "Còn " + available + "/" + total + " cuốn" : "Tạm hết sách");
        availability.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                        this, available > 0 ? R.color.library_success : R.color.library_muted));

        BookCoverLoader.load(doc.coverUrl, cover, fallback);

        card.setOnClickListener(v -> showBookDetails(doc));
        return card;
    }

    private void showBookDetails(BookTitle doc) {
        if (isSessionReady())
            startActivity(
                    new Intent(this, BookDetailActivity.class)
                            .putExtra(BookDetailActivity.EXTRA_BOOK_ID, doc.bookTitleId));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        homeView.show(intent.getIntExtra(ReaderNavigation.EXTRA_TAB, ReaderHomeView.HOME));
        intent.removeExtra(ReaderNavigation.EXTRA_TAB);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("readerTab", homeView.selectedTab());
        super.onSaveInstanceState(state);
    }

    private void goToLogin() {
        logoutSession();
    }

    @Override
    protected void onSessionStopped() {
        if (onlineLoanController != null) onlineLoanController.stop();
        if (notificationSettings != null) notificationSettings.stop();
        if (accountController != null) accountController.onLeaveAccount();
        if (homeView != null) homeView.clearProfile();
        if (catalogueSyncManager != null) {
            catalogueSyncManager.close();
            catalogueSyncManager = null;
        }
        books.clear();
    }

    private String value(DocumentSnapshot doc, String key, String fallback) {
        if (doc == null || !doc.contains(key)) return fallback;
        Object raw = doc.get(key);
        if (raw == null) return fallback;
        String result = String.valueOf(raw).trim();
        return result.isEmpty() ? fallback : result;
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    ReaderNotificationSettingsController notificationSettings() { return notificationSettings; }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accountController != null) accountController.applyCardBrightnessIfVisible();
        if (notificationSettings != null) notificationSettings.refresh();
    }

    @Override
    protected void onPause() {
        if (accountController != null) accountController.restoreCardBrightness();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (notificationSettings != null) notificationSettings.refresh();
    }

    @Override
    protected void onDestroy() {
        if (catalogueSyncManager != null) catalogueSyncManager.close();
        if (databaseHelper != null) databaseHelper.close();
        catalogueExecutor.shutdownNow();
        super.onDestroy();
    }
}
