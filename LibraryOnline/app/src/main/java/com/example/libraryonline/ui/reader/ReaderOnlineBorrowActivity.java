package com.example.libraryonline.ui.reader;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.libraryonline.R;
import com.example.libraryonline.data.remote.firebase.OnlineLoanRequestRepository;
import com.example.libraryonline.data.session.SessionProfile;
import com.example.libraryonline.ui.auth.SessionActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Source;

import java.util.Locale;

/** Figma-inspired Reader Online loan request screen. This creates Pending requests,
 * never a reservation queue or a loan receipt. The counter checkout remains separate. */
public final class ReaderOnlineBorrowActivity extends SessionActivity {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final OnlineLoanRequestRepository requests = new OnlineLoanRequestRepository();
    private ListenerRegistration bookListener, requestListener;
    private String bookId, readerId, requestStatus = "";
    private int maxDays = 14, maxBooks = 5, selectedDays = 7, stock;
    private boolean bookVerified, requestVerified, configVerified, quotaVerified, submitting;
    private int activeOnlineCount;
    private boolean cardActive;
    private Button day3, day7, day14, confirm;
    private EditText customDays;
    private TextView availability, status, selection, limit, reader, holdInfo;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader_online_borrow);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.onlineBorrowRoot), (view, insets) -> {
                    androidx.core.graphics.Insets bars = insets.getInsets(
                            androidx.core.view.WindowInsetsCompat.Type.systemBars());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return androidx.core.view.WindowInsetsCompat.CONSUMED;
                });
        com.google.android.material.bottomnavigation.BottomNavigationView navigation =
                findViewById(R.id.readerBottomNavigation);
        navigation.getMenu().findItem(R.id.navReaderLoans).setChecked(true);
        navigation.setOnItemSelectedListener(item -> navigate(item.getItemId()));
        navigation.setOnItemReselectedListener(item -> navigate(item.getItemId()));
        bookId = getIntent().getStringExtra(BookDetailActivity.EXTRA_BOOK_ID);
        if (savedInstanceState != null)
            selectedDays = savedInstanceState.getInt("selectedDays", 7);
        availability = findViewById(R.id.onlineBorrowAvailability);
        status = findViewById(R.id.onlineBorrowStatus);
        selection = findViewById(R.id.onlineBorrowSelection);
        limit = findViewById(R.id.onlineBorrowLimit);
        reader = findViewById(R.id.onlineBorrowReader);
        holdInfo = findViewById(R.id.onlineBorrowHoldInfo);
        day3 = findViewById(R.id.onlineBorrowDay3);
        day7 = findViewById(R.id.onlineBorrowDay7);
        day14 = findViewById(R.id.onlineBorrowDay14);
        confirm = findViewById(R.id.onlineBorrowConfirm);
        customDays = findViewById(R.id.onlineBorrowCustomDays);
        findViewById(R.id.onlineBorrowBack).setOnClickListener(v -> finish());
        findViewById(R.id.onlineBorrowMyLoans).setOnClickListener(
                v -> ReaderNavigation.returnToMain(this, ReaderHomeView.LOANS));
        day3.setOnClickListener(v -> selectDays(3));
        day7.setOnClickListener(v -> selectDays(7));
        day14.setOnClickListener(v -> selectDays(14));
        customDays.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    selectedDays = -1;
                    refresh();
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        confirm.setOnClickListener(v -> submit());
        refresh();
    }

    private boolean navigate(int itemId) {
        if (!isSessionReady()) return false;
        int tab = ReaderHomeView.LOANS;
        if (itemId == R.id.navReaderHome) tab = ReaderHomeView.HOME;
        else if (itemId == R.id.navReaderNotifications) tab = ReaderHomeView.NOTIFICATIONS;
        else if (itemId == R.id.navReaderAccount) tab = ReaderHomeView.ACCOUNT;
        ReaderNavigation.returnToMain(this, tab);
        return false;
    }

    @Override protected SessionProfile.Role expectedRole() {
        return SessionProfile.Role.READER;
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("selectedDays", selectedDays);
        super.onSaveInstanceState(out);
    }

    @Override protected void onSessionAuthorized(SessionProfile profile) {
        stopListeners();
        readerId = profile.uid;
        quotaVerified = false;
        activeOnlineCount = 0;
        cardActive = profile.user != null
                && "HoatDong".equals(profile.user.cardStatus);
        String displayName = profile.displayName == null ? "Độc giả" : profile.displayName;
        String barcode = profile.user == null || profile.user.barcode == null
                ? "Chưa cấp" : profile.user.barcode;
        reader.setText(displayName + " · Thẻ " + barcode
                + (cardActive ? " · Đang hoạt động" : " · Chưa đủ điều kiện"));
        if (bookId == null || bookId.trim().isEmpty() || bookId.contains("/")) {
            status.setText("Không xác định được đầu sách. Hãy quay lại và chọn sách.");
            refresh();
            return;
        }
        status.setText("Đang xác minh sách và hạn mức với thư viện…");
        String id = OnlineLoanRequestRepository.requestId(profile.uid, bookId);
        requestListener = db.collection(OnlineLoanRequestRepository.REQUESTS)
                .document(id).addSnapshotListener(MetadataChanges.INCLUDE, (doc, error) -> {
                    if (!validSession(profile.uid)) return;
                    if (error != null) {
                        requestVerified = false;
                        status.setText("Chưa kiểm tra được yêu cầu trước đó. Kiểm tra kết nối.");
                    } else if (doc != null) {
                        requestVerified = !doc.getMetadata().isFromCache()
                                && !doc.getMetadata().hasPendingWrites();
                        requestStatus = doc.exists() ? doc.getString("status") : "";
                        if (doc.exists())
                            status.setText(OnlineLoanRequestRepository.label(requestStatus));
                        else if (requestVerified)
                            status.setText("Sẵn sàng gửi yêu cầu. Admin sẽ kiểm tra trước khi giữ sách.");
                    }
                    refresh();
                });
        bookListener = db.collection("BookTitles").document(bookId)
                .addSnapshotListener(MetadataChanges.INCLUDE, (doc, error) -> {
                    if (!validSession(profile.uid)) return;
                    bookVerified = error == null && doc != null && doc.exists()
                            && !doc.getMetadata().isFromCache()
                            && !doc.getMetadata().hasPendingWrites();
                    if (doc != null && doc.exists()) {
                        stock = Long.valueOf(doc.getLong("availableCopies") == null
                                ? 0L : doc.getLong("availableCopies")).intValue();
                        if (Boolean.TRUE.equals(doc.getBoolean("isHidden"))) {
                            stock = 0;
                            bookVerified = false;
                        }
                        bindBook(doc);
                    } else {
                        stock = 0;
                        if (error != null)
                            availability.setText("Không xác minh được tồn kho; cần kết nối mạng.");
                    }
                    refresh();
                });
        db.collection("SystemConfigs").document("default").get(Source.SERVER)
                .addOnCompleteListener(this, task -> {
                    if (!validSession(profile.uid)) return;
                    configVerified = task.isSuccessful();
                    if (task.isSuccessful() && task.getResult().exists()) {
                        Long days = task.getResult().getLong("maxLoanDays");
                        Long books = task.getResult().getLong("maxBooksPerUser");
                        if (days == null || books == null) configVerified = false;
                        else {
                            maxDays = days.intValue();
                            maxBooks = books.intValue();
                        }
                    }
                    if (!configVerified)
                        status.setText("Không đọc được cấu hình thư viện. Hãy kiểm tra kết nối.");
                    refresh();
                });
        db.collection(OnlineLoanRequestRepository.REQUESTS)
                .whereEqualTo("userId", profile.uid).get(Source.SERVER)
                .addOnCompleteListener(this, task -> {
                    if (!validSession(profile.uid)) return;
                    quotaVerified = task.isSuccessful();
                    if (!task.isSuccessful()) {
                        holdInfo.setText("Chưa đối soát được các yêu cầu Online khác.");
                        refresh();
                        return;
                    }
                    int pending = 0, approved = 0, collected = 0;
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        if (OnlineLoanRequestRepository.PENDING.equals(doc.getString("status")))
                            pending++;
                        if (OnlineLoanRequestRepository.APPROVED.equals(doc.getString("status")))
                            approved++;
                        if (OnlineLoanRequestRepository.COLLECTED.equals(doc.getString("status")))
                            collected++;
                    }
                    activeOnlineCount = pending + approved + collected;
                    holdInfo.setText("Đang chờ duyệt: " + pending
                            + " · Chờ lấy: " + approved + " · Đang mượn Online: " + collected
                            + "\nNgày đến hạn được tính khi thủ thư xác nhận giao sách.");
                    refresh();
                });
    }

    private void bindBook(DocumentSnapshot doc) {
        ((TextView) findViewById(R.id.onlineBorrowTitle)).setText(
                value(doc.getString("title"), "Chưa cập nhật tên sách"));
        ((TextView) findViewById(R.id.onlineBorrowAuthor)).setText(
                value(doc.getString("author"), "Chưa cập nhật tác giả"));
        ((TextView) findViewById(R.id.onlineBorrowIsbn)).setText(
                "ISBN " + value(doc.getString("isbn"), "Chưa cập nhật"));
        Long total = doc.getLong("totalCopies");
        availability.setText(String.format(Locale.getDefault(),
                "Sẵn sàng %d / Tổng %s", stock, total == null ? "—" : total.toString()));
        ImageView cover = findViewById(R.id.onlineBorrowCover);
        TextView fallback = findViewById(R.id.onlineBorrowCoverFallback);
        BookCoverLoader.load(doc.getString("coverUrl"), cover, fallback);
    }

    private static String value(String raw, String fallback) {
        return raw == null || raw.trim().isEmpty() ? fallback : raw;
    }

    private boolean validSession(String uid) {
        return uid != null && uid.equals(readerId) && isSessionReady()
                && !isFinishing() && !isDestroyed();
    }

    private void selectDays(int days) {
        if (days > maxDays) return;
        selectedDays = days;
        customDays.setText("");
        refresh();
    }

    private int days() {
        String typed = customDays.getText().toString().trim();
        if (!typed.isEmpty()) {
            try { return Integer.parseInt(typed); }
            catch (NumberFormatException ignored) { return -1; }
        }
        return selectedDays;
    }

    private void refresh() {
        if (day3 == null) return;
        day3.setEnabled(configVerified && maxDays >= 3);
        day7.setEnabled(configVerified && maxDays >= 7);
        day14.setEnabled(configVerified && maxDays >= 14);
        int choice = days();
        for (Button button : new Button[]{day3, day7, day14}) {
            int days = button == day3 ? 3 : button == day7 ? 7 : 14;
            boolean chosen = choice == days && customDays.getText().length() == 0;
            button.setSelected(chosen);
        }
        selection.setText(configVerified
                ? "Đang chọn " + (choice > 0 ? choice : "—") + " ngày · Tối đa " + maxDays + " ngày"
                : "Đang xác minh thời hạn mượn tối đa…");
        limit.setText(configVerified
                ? "Hạn mức thư viện: tối đa " + maxBooks + " cuốn, " + maxDays
                    + " ngày/lượt. Đơn Online chưa kết thúc: " + activeOnlineCount
                    + ". Đối soát thêm mượn tại quầy khi giao."
                : "Đang tải cấu hình thư viện…");
        boolean active = OnlineLoanRequestRepository.PENDING.equals(requestStatus)
                || OnlineLoanRequestRepository.APPROVED.equals(requestStatus)
                || OnlineLoanRequestRepository.COLLECTED.equals(requestStatus);
        confirm.setEnabled(isSessionReady() && !submitting && cardActive
                && bookVerified && requestVerified && configVerified && quotaVerified
                && activeOnlineCount < maxBooks && stock > 0
                && !active && choice >= 1 && choice <= maxDays);
        confirm.setText(submitting ? "Đang gửi yêu cầu…"
                : active ? "Yêu cầu đã được ghi nhận" : "Xác nhận mượn Online");
    }

    private void submit() {
        if (!confirm.isEnabled() || !isSessionReady()) return;
        final int count = days();
        if (count < 1 || count > maxDays) {
            status.setText("Số ngày mượn phải từ 1 đến " + maxDays + ".");
            return;
        }
        final String uid = readerId;
        submitting = true;
        refresh();
        requests.submit(uid, bookId, count).addOnCompleteListener(this, task -> {
            if (!validSession(uid)) return;
            submitting = false;
            if (task.isSuccessful()) {
                requestStatus = OnlineLoanRequestRepository.PENDING;
                activeOnlineCount++;
                status.setText("Đã gửi thành công · Chờ Admin duyệt. Chưa giữ cuốn sách.");
                Toast.makeText(this, "Yêu cầu mượn Online đã được gửi.", Toast.LENGTH_LONG).show();
            } else {
                status.setText("Gửi thất bại: " + (task.getException() == null
                        ? "Vui lòng thử lại." : task.getException().getMessage()));
            }
            refresh();
        });
    }

    @Override protected void onSessionStopped() {
        stopListeners();
        readerId = null;
        bookVerified = false;
        requestVerified = false;
        configVerified = false;
        quotaVerified = false;
        activeOnlineCount = 0;
        cardActive = false;
        submitting = false;
        refresh();
    }

    private void stopListeners() {
        if (bookListener != null) { bookListener.remove(); bookListener = null; }
        if (requestListener != null) { requestListener.remove(); requestListener = null; }
    }
}
