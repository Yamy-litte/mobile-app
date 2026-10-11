package com.example.mylibrary.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.mylibrary.R;
import com.example.mylibrary.models.Book;
import com.example.mylibrary.models.Category;
import com.example.mylibrary.models.Reservation;
import com.example.mylibrary.repository.ReservationRepository;
import com.example.mylibrary.service.BookService;
import com.example.mylibrary.service.CategoryService;
import com.example.mylibrary.dao.DataCallback;
import com.google.firebase.auth.FirebaseAuth;

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

    private Button btnReserveBook;
    private Button btnBorrowBook;

    private BookService bookService;
    private CategoryService categoryService;
    private ReservationRepository reservationRepository;
    private Book currentBook;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_detail);

        imgBookCover = findViewById(R.id.imgBookCover);
        txtDetailTitle = findViewById(R.id.txtDetailTitle);
        txtDetailAuthor = findViewById(R.id.txtDetailAuthor);
        txtDetailCategory = findViewById(R.id.txtDetailCategory);
        txtDetailDescription = findViewById(R.id.txtDetailDescription);
        txtDetailStatus = findViewById(R.id.txtDetailStatus);
        txtDetailBack = findViewById(R.id.txtDetailBack);
        txtDetailFavorite = findViewById(R.id.txtDetailFavorite);

        btnReserveBook = findViewById(R.id.btnReserveBook);
        btnBorrowBook = findViewById(R.id.btnBorrowBook);

        bookService = new BookService();
        categoryService = new CategoryService();
        reservationRepository = new ReservationRepository();

        txtDetailBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        btnReserveBook.setOnClickListener(v -> handleReservation());
        btnBorrowBook.setOnClickListener(v -> handleBorrowRequest());

        String bookId = getIntent().getStringExtra("book_id");
        if (bookId == null || bookId.trim().isEmpty()) {
            Toast.makeText(this, "Không tìm thấy mã sách.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadBookDetail(bookId);
    }

    private void handleReservation() {
        if (currentBook == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                FirebaseAuth.getInstance().getCurrentUser().getUid() : "GUEST";
        
        Reservation reservation = new Reservation();
        reservation.setCustomerId(uid);
        reservation.setBookId(currentBook.getId());
        reservation.setBookTitle(currentBook.getTitle());
        reservation.setReservationDate(System.currentTimeMillis());
        reservation.setExpectedPickupDate(System.currentTimeMillis() + (3 * 24 * 60 * 60 * 1000L));
        reservation.setExpirationDate(System.currentTimeMillis() + (3 * 24 * 60 * 60 * 1000L));
        reservation.setPickupLocation("Quầy dịch vụ Tầng 2");
        reservation.setReservationCode("RES-" + (int)(Math.random() * 90000 + 10000));
        reservation.setStatus("PENDING");

        reservationRepository.addReservation(reservation, new DataCallback<Reservation>() {
            @Override
            public void onSuccess(Reservation result) {
                Toast.makeText(BookDetailActivity.this, "Đặt trước sách thành công! Mã: " + result.getReservationCode(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(BookDetailActivity.this, "Đặt trước thất bại: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleBorrowRequest() {
        Toast.makeText(this, "Yêu cầu mượn đã gửi! Vui lòng đến quầy quét mã.", Toast.LENGTH_LONG).show();
    }

    private void loadBookDetail(String bookId) {
        bookService.getBookById(bookId, new BookService.BookServiceCallback() {
            @Override
            public void onSuccess(List<Book> books) {
                if (isFinishing() || isDestroyed()) return;
                if (books == null || books.isEmpty()) {
                    showLoadError("Không tìm thấy sách.");
                    return;
                }
                currentBook = books.get(0);
                displayBook(currentBook);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isFinishing() && !isDestroyed()) showLoadError(errorMessage);
            }
        });
    }

    private void displayBook(Book book) {
        if (book == null) {
            showLoadError("Không thể đọc thông tin sách.");
            return;
        }

        txtDetailTitle.setText(safeText(book.getTitle(), "Chưa có tên sách"));
        txtDetailAuthor.setText("Tác giả: " + safeText(book.getAuthor(), "Chưa cập nhật"));
        txtDetailDescription.setText(safeText(book.getDescription(), "Chưa có mô tả."));
        txtDetailStatus.setText("Trạng thái: " + safeText(book.getStatus(), "Chưa cập nhật"));

        String coverUrl = book.getCoverUrl();
        if (coverUrl != null && !coverUrl.trim().isEmpty()) {
            Glide.with(this)
                    .load(coverUrl.trim())
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(imgBookCover);
        } else {
            imgBookCover.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        loadCategoryName(book.getCategoryId());
    }

    private void loadCategoryName(String categoryId) {
        if (categoryId == null || categoryId.trim().isEmpty()) {
            txtDetailCategory.setText("Danh mục: Chưa phân loại");
            return;
        }

        categoryService.getCategoryById(categoryId, new CategoryService.CategoryServiceCallback() {
            @Override
            public void onSuccess(List<Category> categories) {
                if (isFinishing() || isDestroyed()) return;
                if (categories == null || categories.isEmpty()) {
                    txtDetailCategory.setText("Danh mục: Chưa xác định");
                    return;
                }
                Category category = categories.get(0);
                txtDetailCategory.setText("Danh mục: " + safeText(category.getName(), "Chưa xác định"));
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isFinishing() && !isDestroyed()) {
                    txtDetailCategory.setText("Danh mục: Chưa xác định");
                }
            }
        });
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private void showLoadError(String message) {
        Toast.makeText(BookDetailActivity.this,
                message == null || message.trim().isEmpty() ? "Đã xảy ra lỗi khi tải thông tin sách." : message,
                Toast.LENGTH_LONG).show();
        finish();
    }
}
