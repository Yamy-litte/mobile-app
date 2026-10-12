
package com.example.mylibrary.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import com.example.mylibrary.R;
import com.example.mylibrary.models.Book;
import com.example.mylibrary.models.Category;
import com.example.mylibrary.service.BookService;
import com.example.mylibrary.service.CategoryService;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private TextView txtGreeting;
    private LinearLayout layoutCategories;
    private LinearLayout layoutPopularBooks;
    private LinearLayout layoutNewBooks;
    private EditText edtSearchBook;
    private Button btnNavHome;
    private Button btnNavBorrow;
    private Button btnNavCard;
    private Button btnNavProfile;
    private String searchKeyword = "";
    private boolean isCategoryFilterActive = false;
    private CategoryService categoryService;
    private BookService bookService;

    private final List<Category> categories = new ArrayList<>();
    private String selectedCategoryId = null;

    private final int greenColor = Color.rgb(49, 92, 69);
    private final int textColor = Color.rgb(45, 50, 47);
    private final int secondaryTextColor = Color.rgb(117, 117, 117);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        edtSearchBook = findViewById(R.id.edtSearchBook);
        btnNavHome = findViewById(R.id.btnNavHome);
        btnNavBorrow = findViewById(R.id.btnNavBorrow);
        btnNavCard = findViewById(R.id.btnNavCard);
        btnNavProfile = findViewById(R.id.btnNavProfile);
        txtGreeting = findViewById(R.id.txtGreeting);
        layoutCategories = findViewById(R.id.layoutCategories);
        layoutPopularBooks = findViewById(R.id.layoutPopularBooks);
        layoutNewBooks = findViewById(R.id.layoutNewBooks);

        categoryService = new CategoryService();
        bookService = new BookService();

        String customerName = getIntent().getStringExtra("customer_name");

        if (customerName != null && !customerName.trim().isEmpty()) {
            txtGreeting.setText("Xin chào, " + customerName + "!");
        } else {
            txtGreeting.setText("Xin chào, độc giả!");
        }

        loadCategories();
        loadPopularBooks();
        loadNewestBooks();
        setupSearch();
        setupBottomNavigation();
    }
    // ====================== THANH ĐIỀU HƯỚNG ======================

    private void setupBottomNavigation() {
        // Trang chủ hiện tại, tránh mở thêm nhiều HomeActivity.
        btnNavHome.setOnClickListener(v -> {
            // Đã ở trang chủ nên không cần chuyển Activity.
        });

        btnNavBorrow.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, BorrowHistoryActivity.class))
        );

        btnNavCard.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, LibraryCardActivity.class))
        );

        btnNavProfile.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, ProfileActivity.class))
        );
    }

    //===================== DETAIL BOOK ====================

    private void openBookDetail(Book book) {
        Intent intent = new Intent(
                HomeActivity.this,
                BookDetailActivity.class
        );

        intent.putExtra("book_id", book.getId());
        startActivity(intent);
    }

    // ==================== SEARCH BOOK ====================

    private void setupSearch() {
        edtSearchBook.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count
            ) {
                searchKeyword = s.toString().trim();

                if (searchKeyword.isEmpty()) {
                    if (selectedCategoryId == null) {
                        loadPopularBooks();
                        loadNewestBooks();
                    } else {
                        loadBooksByCategory(selectedCategoryId);
                    }
                } else {
                    searchBooksOnHome(searchKeyword);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }


    private void searchBooksOnHome(String keyword) {
        bookService.searchBooks(
                keyword,
                new BookService.BookServiceCallback() {
                    @Override
                    public void onSuccess(List<Book> books) {
                        // Bỏ qua kết quả cũ nếu người dùng đã nhập tiếp
                        if (!keyword.equalsIgnoreCase(
                                edtSearchBook.getText().toString().trim())) {
                            return;
                        }

                        // Kết hợp tìm kiếm với danh mục đang chọn
                        List<Book> results = new ArrayList<>();

                        for (Book book : books) {
                            if (selectedCategoryId == null
                                    || selectedCategoryId.equals(
                                    book.getCategoryId())) {
                                results.add(book);
                            }
                        }

                        results.sort((a, b) ->
                                Integer.compare(
                                        b.getPopularity(),
                                        a.getPopularity()
                                )
                        );

                        layoutPopularBooks.removeAllViews();
                        layoutNewBooks.removeAllViews();

                        if (results.isEmpty()) {
                            addMessage(
                                    layoutPopularBooks,
                                    "Không tìm thấy sách phù hợp."
                            );
                            return;
                        }

                        for (int i = 0;
                             i < Math.min(5, results.size());
                             i++) {
                            layoutPopularBooks.addView(
                                    createPopularBookCard(results.get(i))
                            );
                        }

                        for (int i = 0;
                             i < Math.min(5, results.size());
                             i++) {
                            layoutNewBooks.addView(
                                    createNewestBookRow(results.get(i))
                            );
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        showError("Tìm kiếm thất bại: " + errorMessage);
                    }
                }
        );
    }

    // ==================== DANH MỤC ====================

    private void loadCategories() {
        layoutCategories.removeAllViews();
        categories.clear();

        addCategoryChip("Tất cả", null);

        categoryService.getActiveCategories(
                new CategoryService.CategoryServiceCallback() {
                    @Override
                    public void onSuccess(List<Category> result) {
                        categories.clear();
                        categories.addAll(result);

                        for (Category category : categories) {
                            addCategoryChip(
                                    category.getName(),
                                    category.getId()
                            );
                        }

                        updateCategoryStyles();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        showError("Không tải được danh mục: "
                                + errorMessage);
                    }
                }
        );
    }
    private void loadBooksByCategory(String categoryId) {
        bookService.getBooksByCategory(
                categoryId,
                new BookService.BookServiceCallback() {
                    @Override
                    public void onSuccess(List<Book> books) {
                        if (!categoryId.equals(selectedCategoryId)) {
                            return;
                        }

                        List<Book> popularBooks = new ArrayList<>(books);

                        popularBooks.sort((book1, book2) ->
                                Integer.compare(
                                        book2.getPopularity(),
                                        book1.getPopularity()
                                )
                        );

                        layoutPopularBooks.removeAllViews();
                        layoutNewBooks.removeAllViews();

                        if (books.isEmpty()) {
                            addMessage(
                                    layoutPopularBooks,
                                    "Danh mục này chưa có sách."
                            );
                            addMessage(
                                    layoutNewBooks,
                                    "Danh mục này chưa có sách."
                            );
                            return;
                        }

                        for (int i = 0;
                             i < Math.min(5, popularBooks.size());
                             i++) {
                            layoutPopularBooks.addView(
                                    createPopularBookCard(popularBooks.get(i))
                            );
                        }

                        List<Book> newestBooks = new ArrayList<>(books);

                        newestBooks.sort((book1, book2) -> {
                            if (book1.getCreatedAt() == null) {
                                return book2.getCreatedAt() == null ? 0 : 1;
                            }

                            if (book2.getCreatedAt() == null) {
                                return -1;
                            }

                            return book2.getCreatedAt().compareTo(
                                    book1.getCreatedAt()
                            );
                        });

                        for (int i = 0;
                             i < Math.min(5, newestBooks.size());
                             i++) {
                            layoutNewBooks.addView(
                                    createNewestBookRow(newestBooks.get(i))
                            );
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (categoryId.equals(selectedCategoryId)) {
                            showError(
                                    "Không lọc được sách: " + errorMessage
                            );
                        }
                    }
                }
        );
    }
    private void addCategoryChip(String name, String categoryId) {
        TextView chip = new TextView(this);

        chip.setText(name == null ? "Chưa đặt tên" : name);
        chip.setTextSize(14);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(18), dp(10), dp(18), dp(10));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(40)
                );

        params.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(params);

        chip.setOnClickListener(view -> {
            selectedCategoryId = categoryId;
            isCategoryFilterActive = categoryId != null;

            updateCategoryStyles();

            if (categoryId == null) {
                loadPopularBooks();
                loadNewestBooks();
            } else {
                loadBooksByCategory(categoryId);
            }
        });

        layoutCategories.addView(chip);
        updateCategoryStyles();
    }

    private void updateCategoryStyles() {
        for (int i = 0; i < layoutCategories.getChildCount(); i++) {
            View view = layoutCategories.getChildAt(i);

            if (!(view instanceof TextView)) {
                continue;
            }

            TextView chip = (TextView) view;
            String categoryId = null;

            if (i > 0 && i - 1 < categories.size()) {
                categoryId = categories.get(i - 1).getId();
            }

            boolean selected;

            if (i == 0) {
                selected = selectedCategoryId == null;
            } else {
                selected = categoryId != null
                        && categoryId.equals(selectedCategoryId);
            }

            chip.setTextColor(selected ? Color.WHITE : textColor);
            chip.setTypeface(
                    null,
                    selected ? Typeface.BOLD : Typeface.NORMAL
            );

            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(
                    selected ? greenColor : Color.WHITE
            );
            drawable.setCornerRadius(dp(24));
            drawable.setStroke(
                    dp(1),
                    selected ? greenColor : Color.rgb(225, 230, 226)
            );

            chip.setBackground(drawable);
        }
    }

    // ==================== SÁCH PHỔ BIẾN ====================

    private void loadPopularBooks() {
        bookService.getPopularBooks(
                5,
                new BookService.BookServiceCallback() {
                    @Override
                    public void onSuccess(List<Book> books) {
                        layoutPopularBooks.removeAllViews();

                        if (books.isEmpty()) {
                            addMessage(
                                    layoutPopularBooks,
                                    "Chưa có sách phổ biến."
                            );
                            return;
                        }

                        for (Book book : books) {
                            layoutPopularBooks.addView(
                                    createPopularBookCard(book)
                            );
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        showError("Không tải được sách phổ biến: "
                                + errorMessage);
                    }
                }
        );
    }

    private View createPopularBookCard(Book book) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(230, 233, 231));
        card.setBackground(background);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(dp(290), dp(150));
        cardParams.setMargins(0, 0, dp(12), 0);
        card.setLayoutParams(cardParams);

        ImageView cover = createBookCover(book.getCoverUrl(), dp(88), dp(126));
        card.addView(cover);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(10), 0, 0, 0);
        info.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(info, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView title = createTextView(
                safeText(book.getTitle(), "Chưa có tên sách"),
                15, textColor, true);
        TextView author = createTextView(
                "Tác giả: " + safeText(book.getAuthor(), "Chưa cập nhật"),
                12, secondaryTextColor, false);
        TextView popularity = createTextView(
                "Độ phổ biến: " + book.getPopularity(),
                12, greenColor, true);

        info.addView(title);
        addTopMargin(info, author, 7);
        addTopMargin(info, popularity, 7);

        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> openBookDetail(book));
        return card;
    }

    // ==================== SÁCH MỚI ====================

    private void loadNewestBooks() {
        bookService.getNewestBooks(
                5,
                new BookService.BookServiceCallback() {
                    @Override
                    public void onSuccess(List<Book> books) {
                        layoutNewBooks.removeAllViews();

                        if (books.isEmpty()) {
                            addMessage(
                                    layoutNewBooks,
                                    "Chưa có sách mới."
                            );
                            return;
                        }

                        for (Book book : books) {
                            layoutNewBooks.addView(
                                    createNewestBookRow(book)
                            );
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        showError("Không tải được sách mới: "
                                + errorMessage);
                    }
                }
        );
    }

    private View createNewestBookRow(Book book) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(230, 233, 231));
        row.setBackground(background);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);

        ImageView cover = createBookCover(book.getCoverUrl(), dp(76), dp(104));
        row.addView(cover);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(12), 0, 0, 0);
        row.addView(info, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = createTextView(
                safeText(book.getTitle(), "Chưa có tên sách"),
                16, textColor, true);
        TextView author = createTextView(
                "Tác giả: " + safeText(book.getAuthor(), "Chưa cập nhật"),
                13, secondaryTextColor, false);
        TextView availability = createTextView(
                "Còn " + book.getAvailableQuantity() + " cuốn",
                12, greenColor, true);

        info.addView(title);
        addTopMargin(info, author, 6);
        addTopMargin(info, availability, 6);

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> openBookDetail(book));
        return row;
    }

    private ImageView createBookCover(String coverUrl, int widthDp, int heightDp) {
        ImageView imageView = new ImageView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(widthDp, heightDp);
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundColor(Color.rgb(242, 244, 247));
        imageView.setContentDescription("Ảnh bìa sách");

        // Giữ placeholder nếu Firestore chưa có coverUrl hoặc URL không tải được.
        imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        if (coverUrl != null && !coverUrl.trim().isEmpty()) {
            Glide.with(this)
                    .load(coverUrl.trim())
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .centerCrop()
                    .into(imageView);
        }
        return imageView;
    }

    // ==================== HÀM DÙNG CHUNG ====================

    private TextView createTextView(
            String text,
            int textSize,
            int color,
            boolean bold
    ) {
        TextView textView = new TextView(this);

        textView.setText(text);
        textView.setTextSize(textSize);
        textView.setTextColor(color);

        if (bold) {
            textView.setTypeface(null, Typeface.BOLD);
        }

        return textView;
    }

    private void addTopMargin(
            LinearLayout parent,
            View child,
            int marginDp
    ) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.topMargin = dp(marginDp);
        parent.addView(child, params);
    }

    private void addMessage(LinearLayout parent, String message) {
        TextView textView = createTextView(
                message,
                14,
                secondaryTextColor,
                false
        );

        textView.setPadding(dp(12), dp(18), dp(12), dp(18));
        parent.addView(textView);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty()
                ? fallback
                : value;
    }

    private void showError(String message) {
        if (!isFinishing()) {
            Toast.makeText(
                    HomeActivity.this,
                    message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density
                        + 0.5f
        );
    }
}
