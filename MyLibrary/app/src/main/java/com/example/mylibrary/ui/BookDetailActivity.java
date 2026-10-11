
package com.example.mylibrary.ui;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.mylibrary.R;
import com.example.mylibrary.model.Book;
import com.example.mylibrary.model.Category;
import com.example.mylibrary.service.BookService;
import com.example.mylibrary.service.CategoryService;

import java.util.List;

public class BookDetailActivity extends AppCompatActivity {

    private ImageView imgBookCover;

    private TextView txtDetailTitle;
    private TextView txtDetailAuthor;
    private TextView txtDetailCategory;
    private TextView txtDetailDescription;
    private TextView txtDetailStatus;
    private TextView txtDetailBack;
    private TextView txtDetailFavorite;

    private BookService bookService;
    private CategoryService categoryService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_detail);

        // Ánh xạ giao diện
        imgBookCover = findViewById(R.id.imgBookCover);

        txtDetailTitle = findViewById(R.id.txtDetailTitle);
        txtDetailAuthor = findViewById(R.id.txtDetailAuthor);
        txtDetailCategory = findViewById(R.id.txtDetailCategory);
        txtDetailDescription = findViewById(R.id.txtDetailDescription);
        txtDetailStatus = findViewById(R.id.txtDetailStatus);
        txtDetailBack = findViewById(R.id.txtDetailBack);
        txtDetailFavorite = findViewById(R.id.txtDetailFavorite);

        // Khởi tạo service
        bookService = new BookService();
        categoryService = new CategoryService();

        // Quay lại màn hình trước
        txtDetailBack.setOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed()
        );

        // Trái tim hiện chỉ là giao diện theo Figma.
        // Chưa triển khai chức năng yêu thích.

        // Nhận mã sách từ màn hình trước
        String bookId = getIntent().getStringExtra("book_id");

        if (bookId == null || bookId.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Không tìm thấy mã sách.",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
            return;
        }

        loadBookDetail(bookId);
    }

    /**
     * Tải thông tin sách từ Firestore.
     */
    private void loadBookDetail(String bookId) {
        bookService.getBookById(
                bookId,
                new BookService.BookServiceCallback() {
                    @Override
                    public void onSuccess(List<Book> books) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        if (books == null || books.isEmpty()) {
                            showLoadError("Không tìm thấy sách.");
                            return;
                        }

                        displayBook(books.get(0));
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (!isFinishing() && !isDestroyed()) {
                            showLoadError(errorMessage);
                        }
                    }
                }
        );
    }

    /**
     * Hiển thị thông tin sách lên giao diện.
     */
    private void displayBook(Book book) {
        if (book == null) {
            showLoadError("Không thể đọc thông tin sách.");
            return;
        }

        txtDetailTitle.setText(
                safeText(book.getTitle(), "Chưa có tên sách")
        );

        txtDetailAuthor.setText(
                "Tác giả: " +
                        safeText(book.getAuthor(), "Chưa cập nhật")
        );

        txtDetailDescription.setText(
                safeText(book.getDescription(), "Chưa có mô tả.")
        );

        txtDetailStatus.setText(
                "Trạng thái: " +
                        safeText(book.getStatus(), "Chưa cập nhật")
        );

        // Tải ảnh bìa bằng Glide
        String coverUrl = book.getCoverUrl();

        if (coverUrl != null && !coverUrl.trim().isEmpty()) {
            Glide.with(this)
                    .load(coverUrl.trim())
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(imgBookCover);
        } else {
            imgBookCover.setImageResource(
                    android.R.drawable.ic_menu_gallery
            );
        }

        // Hiển thị tên danh mục thay cho mã danh mục
        loadCategoryName(book.getCategoryId());
    }

    /**
     * Tải tên danh mục từ CategoryService.
     */
    private void loadCategoryName(String categoryId) {
        if (categoryId == null || categoryId.trim().isEmpty()) {
            txtDetailCategory.setText("Danh mục: Chưa phân loại");
            return;
        }

        categoryService.getCategoryById(
                categoryId,
                new CategoryService.CategoryServiceCallback() {
                    @Override
                    public void onSuccess(List<Category> categories) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        if (categories == null || categories.isEmpty()) {
                            txtDetailCategory.setText(
                                    "Danh mục: Chưa xác định"
                            );
                            return;
                        }

                        Category category = categories.get(0);

                        txtDetailCategory.setText(
                                "Danh mục: " +
                                        safeText(
                                                category.getName(),
                                                "Chưa xác định"
                                        )
                        );
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (!isFinishing() && !isDestroyed()) {
                            txtDetailCategory.setText(
                                    "Danh mục: Chưa xác định"
                            );
                        }
                    }
                }
        );
    }

    /**
     * Tránh hiển thị null hoặc chuỗi rỗng.
     */
    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty()
                ? fallback
                : value;
    }

    /**
     * Thông báo khi không thể tải thông tin sách.
     */
    private void showLoadError(String message) {
        Toast.makeText(
                BookDetailActivity.this,
                message == null || message.trim().isEmpty()
                        ? "Đã xảy ra lỗi khi tải thông tin sách."
                        : message,
                Toast.LENGTH_LONG
        ).show();

        finish();
    }
}
