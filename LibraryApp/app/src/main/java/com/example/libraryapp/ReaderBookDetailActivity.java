package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.BookDao;
import com.example.libraryapp.dao.CategoryDao;
import com.example.libraryapp.dao.LocationDao;
import com.example.libraryapp.dao.CustomerDao;
import com.example.libraryapp.dao.ReservationDao;
import com.example.libraryapp.model.Book;
import com.example.libraryapp.model.Category;
import com.example.libraryapp.model.Location;
import com.example.libraryapp.model.Customer;
import com.example.libraryapp.model.Reservation;
import com.example.libraryapp.service.CloudLibraryRepository;
import com.google.firebase.firestore.ListenerRegistration;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReaderBookDetailActivity extends AppCompatActivity {
    private BookDao bookDao;
    private CategoryDao categoryDao;
    private LocationDao locationDao;
    private CloudLibraryRepository cloudRepository;
    private ListenerRegistration bookListener;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseAuthManager auth = new FirebaseAuthManager();
        if (!auth.isLoggedIn()) { goToLogin(); return; }
        setContentView(R.layout.activity_reader_book_detail);
        ReaderTabNavigation.bind(this);
        cloudRepository = new CloudLibraryRepository();

        int bookId = getIntent().getIntExtra("book_id", -1);
        bookDao = new BookDao(this);
        categoryDao = new CategoryDao(this);
        locationDao = new LocationDao(this);
        Book book = bookDao.getById(bookId);
        if (book == null) {
            Toast.makeText(this, "Không tìm thấy thông tin sách.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Category category = categoryDao.getById(book.getCategoryId());
        Location location = locationDao.getById(book.getLocationId());
        BookCoverLoader.load(book.getImageUrl(), findViewById(R.id.imageDetailBookCover));
        setText(R.id.textDetailCategory, category == null ? "Chưa phân loại" : category.getCategoryName());
        setText(R.id.textDetailTitle, safe(book.getTitle(), "Chưa có tên sách"));
        setText(R.id.textDetailAuthor, "Tác giả: " + safe(book.getAuthor(), "Chưa cập nhật"));
        setText(R.id.textDetailYear, book.getPublishYear() > 0 ? String.valueOf(book.getPublishYear()) : "Chưa cập nhật");
        setText(R.id.textDetailPages, book.getPages() > 0 ? book.getPages() + " trang" : "Chưa cập nhật");
        setText(R.id.textDetailSummary, safe(book.getSummary(), "Thư viện chưa cập nhật phần giới thiệu cho cuốn sách này."));
        setText(R.id.textDetailPublisher, "Nhà xuất bản: " + safe(book.getPublisher(), "Chưa cập nhật"));
        String locationText = location == null ? "Chưa cập nhật vị trí kệ" :
                "Vị trí: Tòa " + safe(location.getBuilding(), "?") + ", tầng " +
                        safe(location.getFloor(), "?") + ", kệ " + safe(location.getShelfNumber(), "?");
        setText(R.id.textDetailLocation, locationText);
        setText(R.id.textDetailQuantity, "Còn " + book.getAvailableQuantity() + "/" + book.getTotalQuantity() + " cuốn");
        View requestButton = findViewById(R.id.btnRequestBorrow);
        updateRequestButtonText(requestButton, book.getAvailableQuantity());
        requestButton.setOnClickListener(v -> {
            if (!v.isEnabled()) return;
            v.setEnabled(false);
            submitBorrowRequest(auth.getCurrentUid(), book, requestButton);
        });
        bookListener = cloudRepository.listenBookByTitleAndAuthor(book.getTitle(), book.getAuthor(), (snapshot, error) -> {
            if (isFinishing() || isDestroyed() || error != null || snapshot == null || snapshot.isEmpty()) return;
            com.google.firebase.firestore.DocumentSnapshot cloudBook = findMatchingCloudBook(snapshot, book.getAuthor());
            if (cloudBook == null) return;
            String imageUrl = cloudBook.getString("imageUrl");
            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                book.setImageUrl(imageUrl);
                bookDao.update(book);
                BookCoverLoader.load(imageUrl, findViewById(R.id.imageDetailBookCover));
            }
            Long available = cloudBook.getLong("availableQuantity");
            Long total = cloudBook.getLong("totalQuantity");
            if (available != null && total != null) {
                setText(R.id.textDetailQuantity, "Còn " + available + "/" + total + " cuốn");
                updateRequestButtonText(requestButton, available.intValue());
            }
        });
        findViewById(R.id.btnDetailBack).setOnClickListener(v -> finish());
    }


    private void submitBorrowRequest(String firebaseUid, Book book, View requestButton) {
        CustomerDao customerDao = new CustomerDao(this);
        Customer customer = customerDao.getByFirebaseUid(firebaseUid);
        customerDao.close();
        if (customer == null) {
            requestButton.setEnabled(true);
            Toast.makeText(this, "Không tìm thấy hồ sơ độc giả. Vui lòng đăng nhập lại.", Toast.LENGTH_LONG).show();
            return;
        }

        CloudLibraryRepository cloud = cloudRepository;
        cloud.findBookByTitleAndAuthor(book.getTitle(), book.getAuthor()).addOnSuccessListener(snapshot -> {
            com.google.firebase.firestore.DocumentSnapshot document =
                    snapshot == null ? null : findMatchingCloudBook(snapshot, book.getAuthor());
            if (document == null) {
                requestButton.setEnabled(true);
                Toast.makeText(this, "Không tìm thấy sách khớp tiêu đề và tác giả trên Firestore. Hãy mở màn hình Admin để đồng bộ danh mục, rồi thử lại.", Toast.LENGTH_LONG).show();
                return;
            }
            String cloudBookId = document.getId();
            Long availableQuantity = document.getLong("availableQuantity");
            boolean preorder = availableQuantity == null || availableQuantity <= 0;
            cloud.submitReservation(cloudBookId, book.getTitle(), preorder)
                    .addOnSuccessListener(reference -> {
                        requestButton.setEnabled(true);
                        Toast.makeText(this,
                                preorder
                                        ? "Đã đặt trước sách. Yêu cầu đang chờ Admin xác nhận khi có bản sách."
                                        : "Đã gửi yêu cầu mượn lên hệ thống chung. Vui lòng chờ Admin xác nhận.",
                                Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(error -> {
                        requestButton.setEnabled(true);
                        Toast.makeText(this,
                                "Không gửi được yêu cầu lên Firestore: " + safe(error.getMessage(), "Lỗi kết nối"),
                                Toast.LENGTH_LONG).show();
                    });
        }).addOnFailureListener(error -> {
            requestButton.setEnabled(true);
            Toast.makeText(this,
                    "Không thể kiểm tra dữ liệu chung: " + safe(error.getMessage(), "Lỗi kết nối"),
                    Toast.LENGTH_LONG).show();
        });
    }

    private void updateRequestButtonText(View button, int available) {
        if (button instanceof android.widget.Button) {
            ((android.widget.Button) button).setText(available <= 0
                    ? "Đặt trước khi có sách" : "Gửi yêu cầu mượn sách");
        }
    }

    private com.google.firebase.firestore.DocumentSnapshot findMatchingCloudBook(
            com.google.firebase.firestore.QuerySnapshot snapshot, String expectedAuthor) {
        if (snapshot == null) return null;
        for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
            String actualAuthor = document.getString("author");
            if (actualAuthor != null && expectedAuthor != null
                    && actualAuthor.trim().replaceAll("\\s+", " ")
                    .equalsIgnoreCase(expectedAuthor.trim().replaceAll("\\s+", " "))) {
                return document;
            }
        }
        return null;
    }

    private void setText(int id, String value) { ((TextView)findViewById(id)).setText(value); }
    private String safe(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }
    private void goToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent); finish();
    }
    @Override protected void onDestroy() {
        if (bookDao != null) bookDao.close();
        if (categoryDao != null) categoryDao.close();
        if (locationDao != null) locationDao.close();
        if (bookListener != null) {
            bookListener.remove();
            bookListener = null;
        }
        super.onDestroy();
    }
}