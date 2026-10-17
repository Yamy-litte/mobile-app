package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.AdminDao;
import com.example.libraryapp.dao.BookDao;
import com.example.libraryapp.database.SampleDataSeeder;
import com.example.libraryapp.model.Admin;
import com.example.libraryapp.model.Book;
import com.example.libraryapp.service.CloudLibraryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminBookInventoryActivity extends AppCompatActivity {
    private final List<Book> allBooks = new ArrayList<>();
    private final List<Book> shownBooks = new ArrayList<>();
    private AdminBookInventoryAdapter adapter;
    private EditText search;
    private TextView count, empty;
    private BookDao bookDao;
    private CloudLibraryRepository cloud;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseAuthManager auth = new FirebaseAuthManager();
        if (!auth.isLoggedIn()) { goToLogin(); return; }
        AdminDao adminDao = new AdminDao(this);
        Admin admin = adminDao.getByFirebaseUid(auth.getCurrentUid());
        adminDao.close();
        if (admin == null || !"active".equalsIgnoreCase(admin.getStatus())) { goToLogin(); return; }

        setContentView(R.layout.activity_admin_books);
        search = findViewById(R.id.editAdminBookSearch);
        count = findViewById(R.id.textAdminBookCount);
        empty = findViewById(R.id.textAdminBooksEmpty);
        ListView list = findViewById(R.id.listAdminBooks);
        adapter = new AdminBookInventoryAdapter(this, new AdminBookInventoryAdapter.Actions() {
            @Override public void onEdit(Book book) { showBookDialog(book); }
            @Override public void onToggleActive(Book book) {
                boolean next = !book.isActive();
                book.setActive(next);
                if (bookDao.update(book) > 0) {
                    cloud.setBookActive(String.valueOf(book.getId()), next)
                            .addOnFailureListener(error -> Toast.makeText(AdminBookInventoryActivity.this,
                                    "Đã lưu trên máy nhưng chưa đồng bộ trạng thái.", Toast.LENGTH_LONG).show());
                    syncAndReload();
                }
            }
        });
        list.setAdapter(adapter);
        bookDao = new BookDao(this);
        cloud = new CloudLibraryRepository();
        SampleDataSeeder.seed(this);
        reloadBooks();
        findViewById(R.id.btnAddBook).setOnClickListener(v -> showBookDialog(null));
        AdminTabNavigation.bind(this);
        list.setOnItemClickListener((parent, view, position, id) -> showBookDialog(shownBooks.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Book book = shownBooks.get(position);
            boolean willActivate = !book.isActive();
            new AlertDialog.Builder(this)
                    .setTitle(willActivate ? "Kích hoạt sách" : "Ẩn sách khỏi Reader")
                    .setMessage((willActivate ? "Cho phép độc giả nhìn thấy “" : "Ẩn “") + book.getTitle() + "”?")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Xác nhận", (dialog, which) -> {
                        book.setActive(willActivate);
                        if (bookDao.update(book) > 0) {
                            cloud.setBookActive(String.valueOf(book.getId()), willActivate)
                                    .addOnFailureListener(error -> Toast.makeText(this, "Đã lưu trên máy nhưng chưa đồng bộ trạng thái: " + safe(error.getMessage()), Toast.LENGTH_LONG).show());
                            syncAndReload();
                            Toast.makeText(this, willActivate ? "Đã kích hoạt sách." : "Đã ẩn sách; dữ liệu và lịch sử được giữ lại.", Toast.LENGTH_SHORT).show();
                        }
                    }).show();
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterBooks(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void reloadBooks() {
        allBooks.clear();
        allBooks.addAll(bookDao.getAll());
        filterBooks();
    }

    private void filterBooks() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shownBooks.clear();
        for (Book book : allBooks) {
            if (book.getTitle() == null) continue;
            if (query.isEmpty() || book.getTitle().toLowerCase(Locale.ROOT).contains(query)
                    || (book.getAuthor() != null && book.getAuthor().toLowerCase(Locale.ROOT).contains(query))) {
                shownBooks.add(book);
            }
        }
        adapter.replace(shownBooks);
        count.setText(shownBooks.size() + " sách");
        empty.setVisibility(shownBooks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showBookDialog(Book existing) {
        boolean editing = existing != null;
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad / 2, pad, pad);
        scroll.addView(form);
        EditText title = field(form, "Tên sách *", editing ? existing.getTitle() : "");
        EditText author = field(form, "Tác giả *", editing ? existing.getAuthor() : "");
        EditText publisher = field(form, "Nhà xuất bản", editing ? existing.getPublisher() : "");
        EditText year = field(form, "Năm xuất bản", editing && existing.getPublishYear() > 0 ? String.valueOf(existing.getPublishYear()) : "");
        EditText pages = field(form, "Số trang", editing && existing.getPages() > 0 ? String.valueOf(existing.getPages()) : "");
        EditText category = field(form, "ID danh mục (mặc định 1)", editing ? String.valueOf(existing.getCategoryId()) : "1");
        EditText location = field(form, "ID vị trí kệ (mặc định 1)", editing ? String.valueOf(existing.getLocationId()) : "1");
        EditText total = field(form, "Tổng số bản *", editing ? String.valueOf(existing.getTotalQuantity()) : "1");
        EditText available = field(form, "Số bản còn lại *", editing ? String.valueOf(existing.getAvailableQuantity()) : "1");
        EditText image = field(form, "URL ảnh bìa (http/https)", editing ? existing.getImageUrl() : "");
        EditText summary = field(form, "Giới thiệu sách", editing ? existing.getSummary() : "");
        summary.setMinLines(2);
        summary.setGravity(android.view.Gravity.TOP);
        new AlertDialog.Builder(this)
                .setTitle(editing ? "Sửa thông tin sách" : "Thêm sách mới")
                .setView(scroll)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String bookTitle = title.getText().toString().trim();
                    String bookAuthor = author.getText().toString().trim();
                    String imageUrl = image.getText().toString().trim();
                    int totalValue = number(total, -1);
                    int availableValue = number(available, -1);
                    if (bookTitle.isEmpty() || bookAuthor.isEmpty() || totalValue < 0
                            || availableValue < 0 || availableValue > totalValue
                            || (!imageUrl.isEmpty() && !(imageUrl.startsWith("https://") || imageUrl.startsWith("http://")))) {
                        Toast.makeText(this, "Kiểm tra tên, tác giả, số lượng và URL ảnh (phải bắt đầu bằng http:// hoặc https://).", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Book book = editing ? existing : new Book();
                    book.setTitle(bookTitle);
                    book.setAuthor(bookAuthor);
                    book.setPublisher(publisher.getText().toString().trim());
                    book.setPublishYear(Math.max(0, number(year, 0)));
                    book.setPages(Math.max(0, number(pages, 0)));
                    book.setCategoryId(Math.max(1, number(category, 1)));
                    book.setLocationId(Math.max(1, number(location, 1)));
                    book.setTotalQuantity(totalValue);
                    book.setAvailableQuantity(availableValue);
                    book.setImageUrl(imageUrl);
                    book.setSummary(summary.getText().toString().trim());
                    if (editing) bookDao.update(book); else bookDao.insert(book);
                    syncAndReload();
                }).show();
    }

    private EditText field(LinearLayout parent, String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setSingleLine(false);
        input.setTextSize(15);
        parent.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }

    private int number(EditText input, int fallback) {
        try { return Integer.parseInt(input.getText().toString().trim()); }
        catch (Exception ignored) { return fallback; }
    }

    private void syncAndReload() {
        reloadBooks();
        List<Book> activeBooks = new ArrayList<>();
        for (Book book : allBooks) activeBooks.add(book);
        cloud.syncLocalBooks(activeBooks)
                .addOnSuccessListener(unused -> Toast.makeText(this, "Đã lưu kho sách và đồng bộ thông tin lên Firestore.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(error -> Toast.makeText(this, "Đã lưu trên máy, nhưng đồng bộ Firestore lỗi: " + safe(error.getMessage()), Toast.LENGTH_LONG).show());
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }
    private void goToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    @Override protected void onDestroy() {
        if (bookDao != null) bookDao.close();
        super.onDestroy();
    }
}