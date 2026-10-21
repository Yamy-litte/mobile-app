package com.example.libraryonline.ui.reader;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reader landing page. Book catalogue is observed from Firestore collection BookTitles.
 * Fields follow the BookTitle model: title, author, category, publisher, availableCopies,
 * totalCopies, description, isbn and isHidden.
 */
public class ReaderMainActivity extends AppCompatActivity {
    private static final int NAVY = Color.rgb(23, 37, 84);
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int PALE = Color.rgb(239, 246, 255);
    private static final int INK = Color.rgb(30, 41, 59);
    private static final int MUTED = Color.rgb(100, 116, 139);
    private static final int BORDER = Color.rgb(226, 232, 240);

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private ListenerRegistration booksListener;
    private LinearLayout booksContainer;
    private EditText searchInput;
    private TextView greeting, catalogueStatus;
    private final List<DocumentSnapshot> books = new ArrayList<>();
    private String selectedCategory = "Tất cả";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        buildHome();
        loadReaderProfile();
        listenForBooks();
    }

    private void buildHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView logo = text("MyLibrary", 23, NAVY, true);
        TextView subtitle = text("THƯ VIỆN CỘNG ĐỒNG", 10, MUTED, true);
        brand.addView(logo);
        brand.addView(subtitle);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button logout = new Button(this);
        logout.setText("Đăng xuất");
        logout.setTextSize(12);
        logout.setAllCaps(false);
        logout.setOnClickListener(v -> {
            auth.signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        header.addView(logout);
        content.addView(header);

        View gap = spacer(18);
        content.addView(gap);

        LinearLayout welcome = new LinearLayout(this);
        welcome.setOrientation(LinearLayout.VERTICAL);
        welcome.setPadding(dp(18), dp(20), dp(18), dp(20));
        welcome.setBackground(round(NAVY, 20));
        TextView eyebrow = text("KHÔNG GIAN ĐỌC CỦA BẠN", 11, Color.rgb(191, 219, 254), true);
        greeting = text("Xin chào, độc giả!", 25, Color.WHITE, true);
        TextView intro = text("Khám phá sách hay và kiểm tra tình trạng sách tại thư viện.", 14,
                Color.rgb(226, 232, 240), false);
        intro.setPadding(0, dp(7), 0, 0);
        welcome.addView(eyebrow);
        welcome.addView(greeting);
        welcome.addView(intro);
        content.addView(welcome);

        TextView searchTitle = text("Tìm cuốn sách tiếp theo", 19, INK, true);
        searchTitle.setPadding(0, dp(22), 0, dp(10));
        content.addView(searchTitle);
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(14);
        searchInput.setHint("Tên sách, tác giả hoặc thể loại...");
        searchInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        searchInput.setBackground(round(Color.WHITE, 14, BORDER));
        content.addView(searchInput, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView categoryTitle = text("Danh mục", 18, INK, true);
        categoryTitle.setPadding(0, dp(20), 0, dp(10));
        content.addView(categoryTitle);
        LinearLayout categories = new LinearLayout(this);
        categories.setOrientation(LinearLayout.HORIZONTAL);
        categories.setGravity(Gravity.CENTER_VERTICAL);
        String[] names = {"Tất cả", "Văn học", "Khoa học", "Lịch sử", "Kinh tế", "Công nghệ"};
        for (String name : names) {
            Button chip = new Button(this);
            chip.setText(name);
            chip.setTextSize(11);
            chip.setAllCaps(false);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            chip.setPadding(dp(9), 0, dp(9), 0);
            chip.setOnClickListener(v -> {
                selectedCategory = name;
                for (int i = 0; i < categories.getChildCount(); i++) {
                    Button item = (Button) categories.getChildAt(i);
                    boolean selected = item.getText().toString().equals(selectedCategory);
                    item.setTextColor(selected ? Color.WHITE : NAVY);
                    item.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected ? BLUE : PALE));
                }
                renderBooks();
            });
            boolean selected = name.equals(selectedCategory);
            chip.setTextColor(selected ? Color.WHITE : NAVY);
            chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected ? BLUE : PALE));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, dp(42));
            chipParams.rightMargin = dp(6);
            categories.addView(chip, chipParams);
        }
        ScrollView categoryScroll = new ScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        categoryScroll.setFillViewport(false);
        categoryScroll.addView(categories);
        // Horizontal category row uses a horizontal scroll container.
        android.widget.HorizontalScrollView horizontal = new android.widget.HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.addView(categories);
        content.addView(horizontal, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView popular = text("Sách trong thư viện", 19, INK, true);
        titleRow.addView(popular, new LinearLayout.LayoutParams(0, -2, 1));
        catalogueStatus = text("Đang tải...", 12, MUTED, false);
        titleRow.addView(catalogueStatus);
        titleRow.setPadding(0, dp(22), 0, dp(8));
        content.addView(titleRow);

        booksContainer = new LinearLayout(this);
        booksContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(booksContainer);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderBooks(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        TextView tip = text("Mẹo nhỏ: chọn một danh mục hoặc tìm theo tên tác giả để thu hẹp kết quả.", 12, MUTED, false);
        tip.setPadding(0, dp(14), 0, dp(6));
        content.addView(tip);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(Color.WHITE);
        nav.setGravity(Gravity.CENTER);
        addNavItem(nav, "⌂", "Trang chủ", true, () -> scroll.smoothScrollTo(0, 0));
        addNavItem(nav, "⌕", "Tìm sách", false, () -> {
            searchInput.requestFocus();
            searchInput.performClick();
        });
        addNavItem(nav, "▤", "Sách của tôi", false, () -> showNotReady("Danh sách mượn sẽ được nối với dữ liệu phiếu mượn ở bước tiếp theo."));
        addNavItem(nav, "♙", "Tài khoản", false, this::showAccount);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(64)));
        setContentView(root);
    }

    private void loadReaderProfile() {
        FirebaseUser current = auth.getCurrentUser();
        if (current == null) {
            goToLogin();
            return;
        }
        firestore.collection("Users").document(current.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("fullName");
                        if (name != null && !name.trim().isEmpty()) {
                            String firstName = name.trim().split("\\s+")[name.trim().split("\\s+").length - 1];
                            greeting.setText("Xin chào, " + firstName + "!");
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Không tải được hồ sơ độc giả. Một số thông tin có thể chưa hiển thị.", Toast.LENGTH_SHORT).show());
    }

    private void listenForBooks() {
        booksListener = firestore.collection("BookTitles").addSnapshotListener((snapshot, error) -> {
            if (isFinishing()) return;
            if (error != null) {
                catalogueStatus.setText("Chưa tải được");
                booksContainer.removeAllViews();
                booksContainer.addView(text("Không thể tải danh mục sách. Hãy kiểm tra kết nối và quyền Firestore.", 14, MUTED, false));
                return;
            }
            books.clear();
            if (snapshot != null) {
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    Boolean hidden = doc.getBoolean("isHidden");
                    if (!Boolean.TRUE.equals(hidden)) books.add(doc);
                }
            }
            renderBooks();
        });
    }

    private void renderBooks() {
        if (booksContainer == null) return;
        booksContainer.removeAllViews();
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (DocumentSnapshot doc : books) {
            String title = field(doc, "title", "Chưa có tên sách");
            String author = field(doc, "author", "Chưa rõ tác giả");
            String category = field(doc, "category", "Khác");
            if (!"Tất cả".equals(selectedCategory) && !category.toLowerCase(Locale.ROOT).contains(selectedCategory.toLowerCase(Locale.ROOT))) continue;
            String searchable = (title + " " + author + " " + category + " " + field(doc, "isbn", "")).toLowerCase(Locale.ROOT);
            if (!searchable.contains(query)) continue;
            booksContainer.addView(createBookCard(doc, title, author, category));
            shown++;
        }
        catalogueStatus.setText(shown + " đầu sách");
        if (shown == 0) {
            TextView empty = text(books.isEmpty()
                    ? "Chưa có sách trong danh mục. Khi Admin thêm sách, danh sách sẽ xuất hiện tại đây."
                    : "Không tìm thấy sách phù hợp. Thử từ khóa hoặc danh mục khác.", 14, MUTED, false);
            empty.setPadding(dp(14), dp(18), dp(14), dp(18));
            empty.setBackground(round(Color.WHITE, 14, BORDER));
            booksContainer.addView(empty);
        }
    }

    private View createBookCard(DocumentSnapshot doc, String title, String author, String category) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(round(Color.WHITE, 16, BORDER));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);

        TextView cover = text("BOOK", 13, BLUE, true);
        cover.setGravity(Gravity.CENTER);
        cover.setBackground(round(PALE, 12));
        card.addView(cover, new LinearLayout.LayoutParams(dp(66), dp(88)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(12), 0, 0, 0);
        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        details.addView(text(title, 16, INK, true));
        TextView authorText = text(author, 13, MUTED, false);
        authorText.setPadding(0, dp(4), 0, dp(5));
        details.addView(authorText);
        details.addView(text(category, 11, BLUE, true));
        int available = number(doc, "availableCopies");
        int total = number(doc, "totalCopies");
        TextView stock = text(available > 0 ? "Còn " + available + "/" + total + " cuốn" : "Hiện đã hết sách",
                12, available > 0 ? Color.rgb(21, 128, 61) : Color.rgb(185, 28, 28), true);
        stock.setPadding(0, dp(6), 0, 0);
        details.addView(stock);
        card.setOnClickListener(v -> showBookDetails(doc, title, author, category, available, total));
        return card;
    }

    private void showBookDetails(DocumentSnapshot doc, String title, String author, String category, int available, int total) {
        String message = "Tác giả: " + author
                + "\nThể loại: " + category
                + "\nNhà xuất bản: " + field(doc, "publisher", "Chưa cập nhật")
                + "\nISBN: " + field(doc, "isbn", "Chưa cập nhật")
                + "\nTình trạng: " + available + "/" + total + " cuốn sẵn sàng"
                + "\n\n" + field(doc, "description", "Chưa có mô tả cho tác phẩm này.");
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(available > 0 ? "Đã hiểu" : "Đóng", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showAccount() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { goToLogin(); return; }
        firestore.collection("Users").document(user.getUid()).get().addOnSuccessListener(doc -> {
            String name = field(doc, "fullName", "Độc giả");
            String email = field(doc, "email", user.getEmail() == null ? "" : user.getEmail());
            String barcode = field(doc, "barcode", "Chưa cấp");
            String status = field(doc, "cardStatus", "Chưa rõ");
            new AlertDialog.Builder(this).setTitle("Tài khoản độc giả")
                    .setMessage("Họ tên: " + name + "\nEmail: " + email + "\nMã thẻ: " + barcode + "\nTrạng thái thẻ: " + status)
                    .setPositiveButton("Đóng", (dialog, which) -> dialog.dismiss()).show();
        }).addOnFailureListener(e -> showNotReady("Không tải được thông tin tài khoản."));
    }

    private void addNavItem(LinearLayout nav, String icon, String label, boolean active, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        TextView iconText = text(icon, 22, active ? BLUE : MUTED, true);
        TextView labelText = text(label, 10, active ? BLUE : MUTED, active);
        item.addView(iconText);
        item.addView(labelText);
        item.setOnClickListener(v -> action.run());
        nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void goToLogin() {
        auth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void showNotReady(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String field(DocumentSnapshot doc, String key, String fallback) {
        if (doc == null) return fallback;
        String value = doc.getString(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private int number(DocumentSnapshot doc, String key) {
        Long value = doc.getLong(key);
        return value == null ? 0 : value.intValue();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private View spacer(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radiusDp));
        return bg;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
        GradientDrawable bg = round(color, radiusDp);
        bg.setStroke(dp(1), strokeColor);
        return bg;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        if (booksListener != null) booksListener.remove();
        super.onDestroy();
    }
}
