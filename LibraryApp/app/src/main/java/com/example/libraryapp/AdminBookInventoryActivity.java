package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.AdminDao;
import com.example.libraryapp.dao.BookDao;
import com.example.libraryapp.dao.CategoryDao;
import com.example.libraryapp.dao.LocationDao;
import com.example.libraryapp.database.SampleDataSeeder;
import com.example.libraryapp.model.Admin;
import com.example.libraryapp.model.Book;
import com.example.libraryapp.model.Category;
import com.example.libraryapp.model.Location;
import com.example.libraryapp.service.CloudLibraryRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
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
    private final List<Category> categories = new ArrayList<>();
    private final List<Location> locations = new ArrayList<>();
    private int selectedCategoryId = -1;
    private LinearLayout categoryFilterContainer;
    private ListenerRegistration booksListener;

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
        categoryFilterContainer = findViewById(R.id.categoryFilterContainer);
        ListView list = findViewById(R.id.listAdminBooks);
        adapter = new AdminBookInventoryAdapter(this, new AdminBookInventoryAdapter.Actions() {
            @Override public void onEdit(Book book) { showBookDialog(book); }
            @Override public void onToggleActive(Book book) {
                boolean next = !book.isActive();
                book.setActive(next);
                if (bookDao.update(book) > 0) {
                    cloud.syncAdminBook(book)
                            .addOnSuccessListener(unused -> Toast.makeText(this, "Đã đồng bộ trạng thái sách.", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(error -> Toast.makeText(AdminBookInventoryActivity.this,
                                    "Đã lưu trên máy nhưng chưa đồng bộ trạng thái: " + safe(error.getMessage()), Toast.LENGTH_LONG).show());
                    reloadBooks();
                }
            }
        });
        list.setAdapter(adapter);
        bookDao = new BookDao(this);
        cloud = new CloudLibraryRepository();
        SampleDataSeeder.seed(this);
        loadCategories();
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
                            cloud.syncAdminBook(book)
                                    .addOnSuccessListener(unused -> Toast.makeText(this, "Đã đồng bộ trạng thái sách.", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(error -> Toast.makeText(this, "Đã lưu trên máy nhưng chưa đồng bộ trạng thái: " + safe(error.getMessage()), Toast.LENGTH_LONG).show());
                            reloadBooks();
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

    private void loadCategories() {
        CategoryDao categoryDao = new CategoryDao(this);
        categories.clear();
        categories.addAll(categoryDao.getAll());
        categoryDao.close();
        LocationDao locationDao = new LocationDao(this);
        locations.clear();
        locations.addAll(locationDao.getAll());
        locationDao.close();
        categoryFilterContainer.removeAllViews();
        addCategoryChip("Tất cả", -1, true);
        for (Category category : categories) {
            addCategoryChip(category.getCategoryName(), category.getId(), false);
        }
    }

    private void addCategoryChip(String label, int categoryId, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(13);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(dp(18), 0, dp(18), 0);
        chip.setMinHeight(dp(38));
        chip.setBackgroundResource(selected ? R.drawable.bg_filter_selected : R.drawable.bg_filter_chip);
        chip.setTextColor(selected ? android.graphics.Color.WHITE : getColor(R.color.mylibrary_ink));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(38));
        if (categoryFilterContainer.getChildCount() > 0) params.leftMargin = dp(8);
        categoryFilterContainer.addView(chip, params);
        chip.setTag(categoryId);
        chip.setOnClickListener(v -> {
            selectedCategoryId = categoryId;
            for (int i = 0; i < categoryFilterContainer.getChildCount(); i++) {
                View item = categoryFilterContainer.getChildAt(i);
                boolean isSelected = ((Integer) item.getTag()) == selectedCategoryId;
                item.setBackgroundResource(isSelected ? R.drawable.bg_filter_selected : R.drawable.bg_filter_chip);
                ((TextView) item).setTextColor(isSelected ? android.graphics.Color.WHITE : getColor(R.color.mylibrary_ink));
            }
            filterBooks();
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void filterBooks() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shownBooks.clear();
        for (Book book : allBooks) {
            if (book.getTitle() == null) continue;
            boolean matchesText = query.isEmpty()
                    || book.getTitle().toLowerCase(Locale.ROOT).contains(query)
                    || (book.getAuthor() != null && book.getAuthor().toLowerCase(Locale.ROOT).contains(query));
            boolean matchesCategory = selectedCategoryId < 0 || book.getCategoryId() == selectedCategoryId;
            if (matchesText && matchesCategory) shownBooks.add(book);
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
        TextView requiredNote = new TextView(this);
        requiredNote.setText("Các mục có dấu * là bắt buộc. Điền thông tin sách và số lượng trong kho.");
        requiredNote.setTextSize(13);
        requiredNote.setTextColor(getColor(R.color.mylibrary_muted));
        form.addView(requiredNote);
        EditText title = field(form, "Tên sách *", "Ví dụ: Đắc nhân tâm", editing ? existing.getTitle() : "");
        EditText author = field(form, "Tác giả *", "Nhập họ tên tác giả", editing ? existing.getAuthor() : "");
        EditText publisher = field(form, "Nhà xuất bản", "Ví dụ: NXB Trẻ", editing ? existing.getPublisher() : "");
        EditText year = field(form, "Năm xuất bản", "Ví dụ: 2024", editing && existing.getPublishYear() > 0 ? String.valueOf(existing.getPublishYear()) : "");
        year.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText pages = field(form, "Số trang", "Ví dụ: 320", editing && existing.getPages() > 0 ? String.valueOf(existing.getPages()) : "");
        pages.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        Spinner category = selectionField(form, "Danh mục sách *", categoryOptions(),
                findCategoryPosition(editing ? existing.getCategoryId() : (categories.isEmpty() ? -1 : categories.get(0).getId())));
        Spinner location = selectionField(form, "Vị trí kệ *", locationOptions(),
                findLocationPosition(editing ? existing.getLocationId() : (locations.isEmpty() ? -1 : locations.get(0).getId())));
        EditText total = field(form, "Tổng số bản *", "Ví dụ: 5", editing ? String.valueOf(existing.getTotalQuantity()) : "1");
        total.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText available = field(form, "Số bản còn lại *", "Không lớn hơn tổng số bản", editing ? String.valueOf(existing.getAvailableQuantity()) : "1");
        available.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText image = field(form, "URL ảnh bìa", "Dán link ảnh công khai bắt đầu bằng https://", editing ? existing.getImageUrl() : "");
        image.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        EditText summary = field(form, "Giới thiệu sách", "Tóm tắt nội dung cuốn sách", editing ? existing.getSummary() : "");
        summary.setSingleLine(false);
        summary.setMinLines(3);
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
                    if (bookTitle.isEmpty() || bookAuthor.isEmpty() || categories.isEmpty() || locations.isEmpty() || totalValue < 0
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
                    book.setCategoryId(selectedCategoryId(category));
                    book.setLocationId(selectedLocationId(location));
                    book.setTotalQuantity(totalValue);
                    book.setAvailableQuantity(availableValue);
                    book.setImageUrl(imageUrl);
                    book.setSummary(summary.getText().toString().trim());
                    long localResult = editing ? bookDao.update(book) : bookDao.insert(book);
                    if (localResult > 0) {
                        reloadBooks();
                        cloud.syncAdminBook(book)
                                .addOnSuccessListener(unused -> Toast.makeText(this, "Đã lưu và đồng bộ sách lên Firestore.", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(error -> Toast.makeText(this, "Đã lưu trên máy, nhưng đồng bộ Firestore lỗi: " + safe(error.getMessage()), Toast.LENGTH_LONG).show());
                    } else {
                        Toast.makeText(this, "Không lưu được sách vào SQLite.", Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private List<String> categoryOptions() {
        List<String> options = new ArrayList<>();
        for (Category item : categories) options.add(item.getCategoryName());
        return options;
    }
    private List<String> locationOptions() {
        List<String> options = new ArrayList<>();
        for (Location item : locations) options.add("Tòa nhà " + safe(item.getBuilding()) + " · Tầng " + safe(item.getFloor()) + " · Kệ " + safe(item.getShelfNumber()));
        return options;
    }
    private Spinner selectionField(LinearLayout parent, String label, List<String> options, int selectedPosition) {
        TextView labelView = new TextView(this);
        labelView.setText(label); labelView.setTextSize(13);
        labelView.setTextColor(getColor(R.color.mylibrary_ink));
        labelView.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(12);
        parent.addView(labelView, lp);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(a);
        if (!options.isEmpty()) spinner.setSelection(Math.max(0, Math.min(selectedPosition, options.size()-1)));
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));
        return spinner;
    }
    private int findCategoryPosition(int id) {
        for (int i=0;i<categories.size();i++) if(categories.get(i).getId()==id) return i;
        return 0;
    }
    private int findLocationPosition(int id) {
        for (int i=0;i<locations.size();i++) if(locations.get(i).getId()==id) return i;
        return 0;
    }
    private int selectedCategoryId(Spinner spinner) {
        int p=spinner.getSelectedItemPosition(); return p>=0 && p<categories.size()?categories.get(p).getId():-1;
    }
    private int selectedLocationId(Spinner spinner) {
        int p=spinner.getSelectedItemPosition(); return p>=0 && p<locations.size()?locations.get(p).getId():-1;
    }

    private EditText field(LinearLayout parent, String label, String hint, String value) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTextColor(getColor(R.color.mylibrary_ink));
        labelView.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(10);
        parent.addView(labelView, labelParams);
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setSingleLine(label.startsWith("Giới thiệu"));
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
    }

    @Override protected void onStart() {
        super.onStart();
        if (cloud == null || bookDao == null) return;
        booksListener = cloud.listenAllBooks((snapshot, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                Toast.makeText(this, "Không tải được kho sách từ Firestore: " + safe(error.getMessage()), Toast.LENGTH_LONG).show();
                return;
            }
            if (snapshot == null) return;
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                int cloudId = parseBookId(doc.getId(), doc.getString("localBookId"));
                Book local = cloudId > 0 ? bookDao.getById(cloudId) : null;
                String titleValue = doc.getString("title");
                String authorValue = doc.getString("author");
                if (local == null && titleValue != null && authorValue != null) {
                    for (Book candidate : bookDao.getAll()) {
                        if (titleValue.equals(candidate.getTitle()) && authorValue.equals(candidate.getAuthor())) {
                            local = candidate;
                            break;
                        }
                    }
                }
                if (local == null) {
                    Long categoryValue = doc.getLong("categoryId");
                    Long locationValue = doc.getLong("locationId");
                    if (cloudId <= 0 || categoryValue == null || locationValue == null
                            || categoryValue <= 0 || locationValue <= 0) continue;
                    local = new Book();
                    local.setId(cloudId);
                    local.setCategoryId(categoryValue.intValue());
                    local.setLocationId(locationValue.intValue());
                }
                if (titleValue != null) local.setTitle(titleValue);
                if (authorValue != null) local.setAuthor(authorValue);
                String publisherValue = doc.getString("publisher");
                if (publisherValue != null) local.setPublisher(publisherValue);
                Long yearValue = doc.getLong("publishYear");
                if (yearValue != null) local.setPublishYear(yearValue.intValue());
                Long pagesValue = doc.getLong("pages");
                if (pagesValue != null) local.setPages(pagesValue.intValue());
                String summaryValue = doc.getString("summary");
                if (summaryValue != null) local.setSummary(summaryValue);
                String imageValue = doc.getString("imageUrl");
                if (imageValue != null) local.setImageUrl(imageValue);
                Long categoryValue = doc.getLong("categoryId");
                if (categoryValue != null && categoryValue > 0) local.setCategoryId(categoryValue.intValue());
                Long locationValue = doc.getLong("locationId");
                if (locationValue != null && locationValue > 0) local.setLocationId(locationValue.intValue());
                Long totalValue = doc.getLong("totalQuantity");
                if (totalValue != null && totalValue >= 0) local.setTotalQuantity(totalValue.intValue());
                Long availableValue = doc.getLong("availableQuantity");
                if (availableValue != null && availableValue >= 0)
                    local.setAvailableQuantity(Math.min(availableValue.intValue(), local.getTotalQuantity()));
                else
                    local.setAvailableQuantity(Math.min(local.getAvailableQuantity(), local.getTotalQuantity()));
                Boolean activeValue = doc.getBoolean("active");
                if (activeValue != null) local.setActive(activeValue);
                if (local.getId() > 0 && local.getCategoryId() > 0 && local.getLocationId() > 0
                        && local.getTitle() != null && local.getAuthor() != null) {
                    bookDao.upsertCloudBook(local);
                }
            }
            reloadBooks();
        });
    }

    private int parseBookId(String documentId, String localBookId) {
        try {
            if (localBookId != null) return Integer.parseInt(localBookId);
            return Integer.parseInt(documentId);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @Override protected void onStop() {
        if (booksListener != null) {
            booksListener.remove();
            booksListener = null;
        }
        super.onStop();
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }
    private void goToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    @Override protected void onDestroy() {
        if (booksListener != null) {
            booksListener.remove();
            booksListener = null;
        }
        if (bookDao != null) bookDao.close();
        super.onDestroy();
    }
}