package com.example.libraryonline.ui.reader;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.core.view.*;

import com.example.libraryonline.R;
import com.example.libraryonline.data.local.dao.BookTitleDao;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.model.BookTitle;
import com.example.libraryonline.data.remote.firebase.BookCatalogueSyncManager;
import com.example.libraryonline.data.session.*;
import com.example.libraryonline.ui.auth.SessionActivity;
import com.google.firebase.firestore.*;

import java.util.concurrent.*;

public class BookDetailActivity extends SessionActivity {
    public static final String EXTRA_BOOK_ID = "bookTitleId";
    private String bookId;
    private BookDetailView detail;
    private DatabaseHelper helper;
    private BookCatalogueSyncManager sync;
    private ListenerRegistration documentListener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private long generation;
    private Boolean remoteVisible;
    private boolean leaving;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_reader_book_detail);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot), (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        com.google.android.material.bottomnavigation.BottomNavigationView navigation =
                findViewById(R.id.readerBottomNavigation);
        navigation.getMenu().findItem(R.id.navReaderHome).setChecked(true);
        navigation.setOnItemSelectedListener(item -> {
            navigateToTab(item.getItemId());
            return false;
        });
        navigation.setOnItemReselectedListener(item -> navigateToTab(item.getItemId()));
        detail = new BookDetailView(this);
        bookId = getIntent().getStringExtra(EXTRA_BOOK_ID);
        helper = new DatabaseHelper(getApplicationContext());
        findViewById(R.id.detailBack).setOnClickListener(v -> finish());
        findViewById(R.id.detailBorrowOnline).setOnClickListener(v -> {
            if (detail != null) {
                Toast.makeText(this,
                        "Chức năng mượn online chưa được kết nối với giao dịch Firestore.",
                        Toast.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.detailRetry).setOnClickListener(v -> {
            if (isSessionReady()) startCatalogue();
        });
    }

    private void navigateToTab(int id) {
        if (!isSessionReady() || leaving) return;
        leaving = true;
        int tab = ReaderHomeView.HOME;
        if (id == R.id.navReaderLoans) tab = ReaderHomeView.LOANS;
        else if (id == R.id.navReaderNotifications) tab = ReaderHomeView.NOTIFICATIONS;
        else if (id == R.id.navReaderAccount) tab = ReaderHomeView.ACCOUNT;
        ReaderNavigation.returnToMain(this, tab);
    }

    @Override
    protected SessionProfile.Role expectedRole() {
        return SessionProfile.Role.READER;
    }

    @Override
    protected void onSessionAuthorized(SessionProfile profile) {
        startCatalogue();
    }

    private void startCatalogue() {
        stopCatalogue();
        final long token = generation;
        if (bookId == null || bookId.trim().isEmpty() || bookId.contains("/")) {
            detail.bind(null);
            return;
        }
        remoteVisible = null;
        detail.status("Đang kiểm tra dữ liệu…");
        loadLocal(token);
        documentListener = FirebaseFirestore.getInstance().collection("BookTitles").document(bookId)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (!current(token)) return;
                    if (error != null) {
                        detail.error("Không thể tải sách. Đang giữ dữ liệu đã lưu; hãy thử lại.");
                        return;
                    }
                    if (snapshot == null) return;
                    if (snapshot.exists() || !snapshot.getMetadata().isFromCache())
                        remoteVisible = snapshot.exists()
                                && !Boolean.TRUE.equals(snapshot.getBoolean("isHidden"));
                    if (Boolean.FALSE.equals(remoteVisible)) {
                        detail.bind(null);
                        return;
                    }
                    detail.status(snapshot.getMetadata().isFromCache()
                            ? "Dữ liệu đã lưu — đang kiểm tra kết nối"
                            : "Đã nhận cập nhật từ thư viện");
                    loadLocal(token);
                });
        sync = new BookCatalogueSyncManager(getApplicationContext());
        sync.start(new BookCatalogueSyncManager.SyncListener() {
            @Override
            public void onSyncCompleted(int count) {
                runOnUiThread(() -> {
                    if (current(token)) loadLocal(token);
                });
            }

            @Override
            public void onSyncFailed(String message) {
                runOnUiThread(() -> {
                    if (current(token)) detail.error("Lỗi đồng bộ: " + message);
                });
            }
        });
    }

    private boolean current(long token) {
        return token == generation && isSessionReady() && !isDestroyed();
    }

    private void loadLocal(long token) {
        worker.execute(() -> {
            try {
                BookTitle book = new BookTitleDao(helper.getReadableDatabase()).findById(bookId);
                runOnUiThread(() -> {
                    if (current(token))
                        detail.bind(Boolean.FALSE.equals(remoteVisible) ? null : book);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (current(token)) detail.error("Không đọc được sách đã lưu. Hãy thử lại.");
                });
            }
        });
    }

    private void stopCatalogue() {
        generation++;
        if (documentListener != null) {
            documentListener.remove();
            documentListener = null;
        }
        if (sync != null) {
            sync.close();
            sync = null;
        }
    }

    @Override
    protected void onSessionStopped() {
        stopCatalogue();
    }

    @Override
    protected void onDestroy() {
        stopCatalogue();
        worker.execute(helper::close);
        worker.shutdown();
        super.onDestroy();
    }
}
