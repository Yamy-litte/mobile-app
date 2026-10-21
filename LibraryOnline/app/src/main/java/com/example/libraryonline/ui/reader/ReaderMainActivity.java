package com.example.libraryonline.ui.reader;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;
import com.example.libraryonline.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reader home screen. Layout is defined in home_reader_activity.xml and item_reader_book.xml.
 * This Activity handles profile/catalogue data and user interactions only.
 */
public class ReaderMainActivity extends AppCompatActivity {
    private static final int TEAL = Color.rgb(23, 102, 91);
    private static final int MUTED = Color.rgb(124, 141, 134);

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private ListenerRegistration booksListener;
    private LinearLayout categoriesContainer;
    private LinearLayout popularBooksContainer;
    private LinearLayout latestBooksContainer;
    private EditText searchInput;
    private TextView greeting, avatar, popularCount, catalogueStatus;
    private final List<DocumentSnapshot> books = new ArrayList<>();
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(2);
    private String selectedCategory = "Tất cả";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        setContentView(R.layout.home_reader_activity);

        bindViews();
        bindActions();
        buildCategories();
        loadReaderProfile();
        listenForBooks();
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
        findViewById(R.id.buttonReaderAccount).setOnClickListener(v -> showAccount());
        findViewById(R.id.navReaderHome).setOnClickListener(v ->
                ((android.widget.ScrollView) findViewById(R.id.readerScroll)).smoothScrollTo(0, 0));
        findViewById(R.id.navReaderLoans).setOnClickListener(v ->
                showMessage("Màn hình mượn sách sẽ được nối với phiếu mượn trong bước nghiệp vụ mượn/trả."));
        findViewById(R.id.navReaderNotifications).setOnClickListener(v ->
                showMessage("Màn hình thông báo sẽ hiển thị thông báo mượn, trả và hạn trả khi được nối dữ liệu."));
        findViewById(R.id.navReaderAccount).setOnClickListener(v -> showAccount());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderBooks(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void buildCategories() {
        String[] names = {"Tất cả", "Văn học", "Khoa học", "Lịch sử", "Tâm lý", "Kinh tế", "Công nghệ"};
        categoriesContainer.removeAllViews();
        for (String name : names) {
            TextView chip = new TextView(this);
            chip.setText(name);
            chip.setTextSize(12);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding(dp(15), 0, dp(15), 0);
            chip.setTextColor(name.equals(selectedCategory) ? Color.WHITE : TEAL);
            chip.setBackgroundResource(name.equals(selectedCategory)
                    ? R.drawable.bg_reader_chip_selected : R.drawable.bg_reader_chip);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(36));
            params.rightMargin = dp(8);
            categoriesContainer.addView(chip, params);
            chip.setOnClickListener(v -> {
                selectedCategory = name;
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
        firestore.collection("Users").document(current.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String name = value(doc, "fullName", "Độc giả");
                    String firstName = name.trim().isEmpty() ? "Độc giả" :
                            name.trim().split("\\s+")[name.trim().split("\\s+").length - 1];
                    greeting.setText(firstName);
                    String[] parts = name.trim().split("\\s+");
                    String initials = parts.length > 1
                            ? (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                            : (parts.length == 1 && !parts[0].isEmpty() ? parts[0].substring(0, 1) : "TV");
                    avatar.setText(initials.toUpperCase(Locale.ROOT));
                })
                .addOnFailureListener(e -> showMessage("Không tải được thông tin độc giả."));
    }

    private void listenForBooks() {
        booksListener = firestore.collection("BookTitles").addSnapshotListener((snapshot, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                popularCount.setText("Chưa tải được");
                catalogueStatus.setText("Không thể tải danh mục. Kiểm tra kết nối và quyền Firestore.");
                popularBooksContainer.removeAllViews();
                latestBooksContainer.removeAllViews();
                return;
            }
            books.clear();
            if (snapshot != null) {
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    if (!Boolean.TRUE.equals(doc.getBoolean("isHidden"))) books.add(doc);
                }
            }
            renderBooks();
        });
    }

    private void renderBooks() {
        if (popularBooksContainer == null || latestBooksContainer == null) return;
        popularBooksContainer.removeAllViews();
        latestBooksContainer.removeAllViews();

        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<DocumentSnapshot> filtered = new ArrayList<>();
        for (DocumentSnapshot doc : books) {
            String title = value(doc, "title", "Chưa có tên sách");
            String author = value(doc, "author", "Chưa rõ tác giả");
            String category = value(doc, "category", "Khác");
            if (!"Tất cả".equals(selectedCategory)
                    && !category.toLowerCase(Locale.ROOT).contains(selectedCategory.toLowerCase(Locale.ROOT))) continue;
            String searchable = (title + " " + author + " " + category + " " + value(doc, "isbn", ""))
                    .toLowerCase(Locale.ROOT);
            if (searchable.contains(query)) filtered.add(doc);
        }

        popularCount.setText(filtered.size() + " đầu sách");
        catalogueStatus.setText(filtered.size() + " cuốn sách phù hợp");
        if (filtered.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(books.isEmpty()
                    ? "Thư viện chưa có sách được công khai."
                    : "Không tìm thấy sách phù hợp với từ khóa hoặc danh mục này.");
            empty.setTextColor(MUTED);
            empty.setTextSize(13);
            empty.setPadding(dp(14), dp(16), dp(14), dp(16));
            latestBooksContainer.addView(empty);
            return;
        }

        int popularLimit = Math.min(6, filtered.size());
        for (int i = 0; i < popularLimit; i++) {
            View card = createBookCard(filtered.get(i));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(292), -2);
            params.rightMargin = dp(10);
            popularBooksContainer.addView(card, params);
        }
        for (DocumentSnapshot doc : filtered) latestBooksContainer.addView(createBookCard(doc));
    }

    private View createBookCard(DocumentSnapshot doc) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_reader_book, latestBooksContainer, false);
        TextView title = card.findViewById(R.id.textReaderBookTitle);
        TextView author = card.findViewById(R.id.textReaderBookAuthor);
        TextView category = card.findViewById(R.id.textReaderBookCategory);
        TextView availability = card.findViewById(R.id.textReaderBookAvailability);
        ImageView cover = card.findViewById(R.id.imageReaderBookCover);
        TextView fallback = card.findViewById(R.id.textReaderCoverFallback);

        title.setText(value(doc, "title", "Chưa có tên sách"));
        author.setText(value(doc, "author", "Chưa rõ tác giả"));
        category.setText(value(doc, "category", "Khác").toUpperCase(Locale.ROOT));
        long available = number(doc, "availableCopies");
        long total = number(doc, "totalCopies");
        availability.setText(available > 0 ? "Còn " + available + "/" + total + " cuốn" : "Tạm hết sách");
        availability.setTextColor(available > 0 ? Color.rgb(25, 115, 77) : Color.rgb(179, 69, 57));

        String coverUrl = value(doc, "coverUrl", "");
        if (!coverUrl.isEmpty()) {
            fallback.setVisibility(View.GONE);
            cover.setTag(coverUrl);
            imageExecutor.execute(() -> {
                Bitmap bitmap = downloadBitmap(coverUrl);
                if (bitmap != null && !isFinishing() && !isDestroyed()) {
                    runOnUiThread(() -> {
                        if (coverUrl.equals(cover.getTag())) cover.setImageBitmap(bitmap);
                    });
                } else if (!isFinishing() && !isDestroyed()) {
                    runOnUiThread(() -> {
                        if (coverUrl.equals(cover.getTag())) fallback.setVisibility(View.VISIBLE);
                    });
                }
            });
        } else {
            cover.setImageDrawable(null);
            fallback.setVisibility(View.VISIBLE);
        }

        card.setOnClickListener(v -> showBookDetails(doc));
        return card;
    }

    private Bitmap downloadBitmap(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);
            connection.connect();
            try (InputStream stream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(stream);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void showBookDetails(DocumentSnapshot doc) {
        String title = value(doc, "title", "Chi tiết sách");
        String author = value(doc, "author", "Chưa rõ tác giả");
        long available = number(doc, "availableCopies");
        long total = number(doc, "totalCopies");
        String message = "Tác giả: " + author
                + "\nThể loại: " + value(doc, "category", "Chưa cập nhật")
                + "\nNhà xuất bản: " + value(doc, "publisher", "Chưa cập nhật")
                + "\nNăm xuất bản: " + value(doc, "publishYear", "Chưa cập nhật")
                + "\nISBN: " + value(doc, "isbn", "Chưa cập nhật")
                + "\nTình trạng: " + available + "/" + total + " cuốn sẵn sàng"
                + "\n\n" + value(doc, "description", "Chưa có mô tả cho tác phẩm này.");
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Đóng", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showAccount() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }
        firestore.collection("Users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String message = "Họ tên: " + value(doc, "fullName", "Độc giả")
                            + "\nEmail: " + value(doc, "email", user.getEmail() == null ? "" : user.getEmail())
                            + "\nMã thẻ: " + value(doc, "barcode", "Chưa cấp")
                            + "\nTrạng thái thẻ: " + value(doc, "cardStatus", "Chưa rõ");
                    new AlertDialog.Builder(this)
                            .setTitle("Tài khoản độc giả")
                            .setMessage(message)
                            .setNeutralButton("Đăng xuất", (dialog, which) -> logout())
                            .setPositiveButton("Đóng", (dialog, which) -> dialog.dismiss())
                            .show();
                })
                .addOnFailureListener(e -> showMessage("Không tải được thông tin tài khoản."));
    }

    private void logout() {
        auth.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goToLogin() {
        auth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private String value(DocumentSnapshot doc, String key, String fallback) {
        if (doc == null) return fallback;
        Object raw = doc.get(key);
        if (raw == null) return fallback;
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? fallback : value;
    }

    private long number(DocumentSnapshot doc, String key) {
        Long value = doc.getLong(key);
        return value == null ? 0 : value;
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        if (booksListener != null) booksListener.remove();
        imageExecutor.shutdownNow();
        super.onDestroy();
    }
}