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
import com.google.firebase.firestore.FirebaseFirestoreException;
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
    private SessionProfile activeProfile;
    private String bookId, readerId, requestStatus = "";
    private int maxDays = 14, maxBooks = 5, selectedDays = 7, stock;
    private boolean bookVerified, requestVerified, configVerified, quotaVerified, submitting;
    private int activeOnlineCount;
    private boolean cardActive;
    private Button day3, day7, day14, confirm;
    private EditText customDays;
    private TextView availability, status, selection, limit, reader, holdInfo, validation;

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
        validation = findViewById(R.id.onlineBorrowValidation);
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
        findViewById(R.id.onlineBorrowRetry).setOnClickListener(v -> {
            if (activeProfile != null && validSession(activeProfile.uid)) {
                status.setText("Đang kiểm tra lại thông tin với Firestore…");
                onSessionAuthorized(activeProfile);
            } else {
                status.setText("Phiên đăng nhập chưa sẵn sàng. Hãy đăng nhập lại.");
            }
        });
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
        activeProfile = profile;
        readerId = profile.uid;
        requestStatus = "";
        stock = 0;
        bookVerified = false;
        requestVerified = false;
        configVerified = false;
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
                        status.setText(remoteError(error,
                                "Chưa kiểm tra được yêu cầu cũ. Hãy thử lại."));
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
                            availability.setText(remoteError(error,
                                    "Không thể xác minh tồn kho; cần kết nối mạng."));
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
                        status.setText(remoteError(task.getException(),
                                "Không đọc được cấu hình thư viện. Hãy thử lại."));
                    refresh();
                });
        db.collection(OnlineLoanRequestRepository.REQUESTS)
                .whereEqualTo("userId", profile.uid).get(Source.SERVER)
                .addOnCompleteListener(this, task -> {
                    if (!validSession(profile.uid)) return;
                    quotaVerified = task.isSuccessful();
                    if (!task.isSuccessful()) {
                        holdInfo.setText(remoteError(task.getException(),
                                "Chưa đối soát được các yêu cầu Online khác."));
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

    /** The request button remains tappable so a failed prerequisite never looks inert. */
    private String blockingReason() {
        if (!isSessionReady() || readerId == null)
            return "Phiên đăng nhập chưa sẵn sàng. Hãy đăng nhập lại.";
        if (bookId == null || bookId.trim().isEmpty() || bookId.contains("/"))
            return "Không xác định được đầu sách. Hãy quay lại chọn sách.";
        if (!cardActive)
            return "Thẻ độc giả chưa hoạt động hoặc đang bị khóa.";
        if (!bookVerified)
            return "Chưa xác minh được đầu sách trực tuyến. Kiểm tra kết nối rồi bấm Kiểm tra lại.";
        if (!requestVerified)
            return "Chưa đọc được trạng thái yêu cầu trên Firestore. Kiểm tra kết nối hoặc quyền truy cập Firestore rồi thử lại.";
        if (!configVerified)
            return "Chưa tải được cấu hình thư viện. Bấm Kiểm tra lại.";
        if (!quotaVerified)
            return "Chưa đối soát được số yêu cầu Online đang có. Bấm Kiểm tra lại.";
        if (OnlineLoanRequestRepository.PENDING.equals(requestStatus))
            return "Bạn đã gửi yêu cầu đầu sách này. Hãy xem mục Mượn sách để theo dõi.";
        if (OnlineLoanRequestRepository.APPROVED.equals(requestStatus))
            return "Đơn này đã được duyệt. Hãy đến quầy nhận sách đúng hạn.";
        if (OnlineLoanRequestRepository.COLLECTED.equals(requestStatus))
            return "Bạn đang mượn đầu sách này. Trả sách trước khi gửi lượt mượn mới.";
        if (activeOnlineCount >= maxBooks)
            return "Bạn đã đạt hạn mức " + maxBooks + " đơn Online đang hoạt động.";
        if (stock <= 0)
            return "Đầu sách hiện không còn cuốn sẵn sàng để gửi yêu cầu.";
        int count = days();
        if (count < 1 || count > maxDays)
            return "Nhập số ngày mượn từ 1 đến " + maxDays + ".";
        return null;
    }

    private static String remoteError(Exception error, String fallback) {
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code =
                    ((FirebaseFirestoreException) error).getCode();
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                return "Firestore từ chối quyền truy cập (PERMISSION_DENIED). "
                        + "Kiểm tra Rules đã triển khai và tài khoản đăng nhập.";
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE)
                return "Không kết nối được Firestore. Kiểm tra Internet và thử lại.";
            if (code == FirebaseFirestoreException.Code.FAILED_PRECONDITION)
                return "Firestore chưa đáp ứng điều kiện truy vấn (FAILED_PRECONDITION). "
                        + "Kiểm tra cấu hình và chỉ mục.";
        }
        return error != null && error.getMessage() != null
                ? fallback + " " + error.getMessage() : fallback;
    }

    private void refresh() {
        if (day3 == null) return;
        day3.setEnabled(configVerified && maxDays >= 3);
        day7.setEnabled(configVerified && maxDays >= 7);
        day14.setEnabled(configVerified && maxDays >= 14);
        int choice = days();
        for (Button button : new Button[]{day3, day7, day14}) {
            int preset = button == day3 ? 3 : button == day7 ? 7 : 14;
            button.setSelected(choice == preset && customDays.getText().length() == 0);
        }
        selection.setText(configVerified
                ? "Đang chọn " + (choice > 0 ? choice : "—") + " ngày · Tối đa " + maxDays + " ngày"
                : "Đang xác minh thời hạn mượn tối đa…");
        limit.setText(configVerified
                ? "Hạn mức thư viện: tối đa " + maxBooks + " cuốn, " + maxDays
                    + " ngày/lượt. Đơn Online chưa kết thúc: " + activeOnlineCount
                    + ". Đối soát thêm mượn tại quầy khi giao."
                : "Đang tải cấu hình thư viện…");
        String reason = blockingReason();
        if (validation != null) {
            validation.setText(submitting
                    ? "Đang gửi lên Firestore. Yêu cầu chưa được xác nhận…"
                    : reason == null
                    ? "Đủ điều kiện sơ bộ. Nhấn Xác nhận để gửi lên Firestore."
                    : reason);
            validation.setTextColor(androidx.core.content.ContextCompat.getColor(this,
                    reason == null ? R.color.library_success : R.color.text_secondary));
        }
        // Never silently disable the button for a missing network or rule permission.
        confirm.setEnabled(!submitting);
        boolean existing = OnlineLoanRequestRepository.PENDING.equals(requestStatus)
                || OnlineLoanRequestRepository.APPROVED.equals(requestStatus)
                || OnlineLoanRequestRepository.COLLECTED.equals(requestStatus);
        confirm.setText(submitting ? "Đang gửi lên Firestore…"
                : existing ? "Yêu cầu đã được ghi nhận" : "Xác nhận mượn Online");
    }

    private void submit() {
        if (submitting) return;
        String blocking = blockingReason();
        if (blocking != null) {
            status.setText(blocking);
            Toast.makeText(this, blocking, Toast.LENGTH_LONG).show();
            return;
        }
        final int count = days();
        final String uid = readerId;
        submitting = true;
        status.setText("Đang gửi yêu cầu và xác nhận với Firestore…");
        refresh();
        requests.submit(uid, bookId, count).addOnCompleteListener(this, task -> {
            if (!validSession(uid)) return;
            submitting = false;
            if (task.isSuccessful()) {
                requestStatus = OnlineLoanRequestRepository.PENDING;
                activeOnlineCount++;
                status.setText("Đã gửi thành công · Chờ Admin duyệt. Chưa giữ cuốn sách.");
                Toast.makeText(this, "Đã ghi yêu cầu lên Firestore. Xem trong mục Mượn sách.",
                        Toast.LENGTH_LONG).show();
                ReaderNavigation.returnToMain(this, ReaderHomeView.LOANS);
            } else {
                String failure = remoteError(task.getException(),
                        "Gửi yêu cầu thất bại.");
                status.setText(failure);
                Toast.makeText(this, failure, Toast.LENGTH_LONG).show();
                refresh();
            }
        });
    }

    @Override protected void onSessionStopped() {
        stopListeners();
        activeProfile = null;
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
