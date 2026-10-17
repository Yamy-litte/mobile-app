package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.BookDao;
import com.example.libraryapp.dao.CategoryDao;
import com.example.libraryapp.database.SampleDataSeeder;
import com.example.libraryapp.model.Book;
import com.example.libraryapp.model.Category;
import com.example.libraryapp.service.CloudLibraryRepository;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReaderHomeActivity extends AppCompatActivity {
    private final List<Book> allBooks = new ArrayList<>();
    private final List<Book> visibleBooks = new ArrayList<>();
    private ReaderBookAdapter bookAdapter;
    private EditText searchInput;
    private Spinner categorySpinner;
    private TextView countText;
    private TextView emptyText;
    private BookDao bookDao;
    private CategoryDao categoryDao;
    private final List<Category> availableCategories = new ArrayList<>();
    private final List<Integer> categoryIds = new ArrayList<>();
    private CloudLibraryRepository cloud;
    private ListenerRegistration booksListener;
    private ListenerRegistration notificationsListener;
    private final Set<String> knownNotificationIds = new HashSet<>();
    private boolean notificationSnapshotInitialized = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseAuthManager auth = new FirebaseAuthManager();
        if (!auth.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_reader_home);
        searchInput = findViewById(R.id.editBookSearch);
        categorySpinner = findViewById(R.id.spinnerBookCategory);
        ListView bookList = findViewById(R.id.listReaderBooks);
        countText = findViewById(R.id.textBookCount);
        emptyText = findViewById(R.id.textEmptyBooks);

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            auth.logout();
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        ReaderTabNavigation.bind(this);
        findViewById(R.id.btnBorrowMultipleBooks).setOnClickListener(v ->
                startActivity(new Intent(this, ReaderMultiBookRequestActivity.class)));
        findViewById(R.id.tabHome).setOnClickListener(v -> {
            searchInput.setText("");
            categorySpinner.setSelection(0);
        });

        loadCategoryOptions();
        bookAdapter = new ReaderBookAdapter(this, visibleBooks);
        bookList.setAdapter(bookAdapter);
        SampleDataSeeder.seed(this);
        bookDao = new BookDao(this);
        allBooks.addAll(bookDao.getAll());
        cloud = new CloudLibraryRepository();
        filterBooks();

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterBooks(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { filterBooks(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        bookList.setOnItemClickListener((parent, view, position, id) -> {
            Book book = visibleBooks.get(position);
            Intent detailIntent = new Intent(this, ReaderBookDetailActivity.class);
            detailIntent.putExtra("book_id", book.getId());
            startActivity(detailIntent);
        });
    }

    @Override protected void onStart() {
        super.onStart();
        if (cloud == null) return;
        notificationSnapshotInitialized = false;
        knownNotificationIds.clear();
        notificationsListener = cloud.listenMyNotifications((snapshot, error) -> {
            if (isFinishing() || isDestroyed() || error != null || snapshot == null) return;
            if (!notificationSnapshotInitialized) {
                for (QueryDocumentSnapshot doc : snapshot) knownNotificationIds.add(doc.getId());
                notificationSnapshotInitialized = true;
                return;
            }
            for (com.google.firebase.firestore.DocumentChange change : snapshot.getDocumentChanges()) {
                QueryDocumentSnapshot doc = (QueryDocumentSnapshot) change.getDocument();
                if (change.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED
                        && knownNotificationIds.add(doc.getId())) {
                    com.example.libraryapp.service.LocalLibraryNotification.show(
                            this, doc.getString("title"), doc.getString("content"), doc.getId());
                }
            }
        });
        booksListener = cloud.listenAvailableBooks((snapshot, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                Toast.makeText(this, "Không đồng bộ được tồn kho sách: "
                        + (error.getMessage() == null ? "Lỗi kết nối" : error.getMessage()),
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (snapshot == null) return;
            for (QueryDocumentSnapshot doc : snapshot) {
                if (Boolean.FALSE.equals(doc.getBoolean("active"))) continue;
                String title = doc.getString("title");
                String author = doc.getString("author");
                Long total = doc.getLong("totalQuantity");
                Long available = doc.getLong("availableQuantity");
                if (title == null || author == null || total == null || available == null) continue;
                for (Book localBook : allBooks) {
                    if (title.equals(localBook.getTitle()) && author.equals(localBook.getAuthor())) {
                        localBook.setTotalQuantity(total.intValue());
                        localBook.setAvailableQuantity(available.intValue());
                        String imageUrl = doc.getString("imageUrl");
                        if (imageUrl != null) localBook.setImageUrl(imageUrl);
                        bookDao.update(localBook);
                        break;
                    }
                }
            }
            filterBooks();
        });
    }

    @Override protected void onStop() {
        if (notificationsListener != null) {
            notificationsListener.remove();
            notificationsListener = null;
        }
        if (booksListener != null) {
            booksListener.remove();
            booksListener = null;
        }
        super.onStop();
    }

    private void loadCategoryOptions() {
        List<String> labels = new ArrayList<>();
        categoryIds.clear();
        availableCategories.clear();
        labels.add("Tất cả danh mục");
        categoryIds.add(-1);
        categoryDao = new CategoryDao(this);
        availableCategories.addAll(categoryDao.getAll());
        categoryDao.close();
        for (Category category : availableCategories) {
            labels.add(category.getCategoryName());
            categoryIds.add(category.getId());
        }
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);
    }

    private void filterBooks() {
        if (bookAdapter == null) return;
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        int selectedCategory = categorySpinner == null ? 0 : categorySpinner.getSelectedItemPosition();
        visibleBooks.clear();
        for (Book book : allBooks) {
            if (!book.isActive()) continue;
            boolean matchesQuery = query.isEmpty() || contains(book.getTitle(), query)
                    || contains(book.getAuthor(), query) || contains(book.getPublisher(), query);
            int selectedCategoryId = selectedCategory >= 0 && selectedCategory < categoryIds.size()
                    ? categoryIds.get(selectedCategory) : -1;
            boolean matchesCategory = selectedCategoryId < 0 || book.getCategoryId() == selectedCategoryId;
            if (matchesQuery && matchesCategory) visibleBooks.add(book);
        }
        bookAdapter.notifyDataSetChanged();
        countText.setText(visibleBooks.size() + " cuốn");
        emptyText.setVisibility(visibleBooks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    @Override protected void onDestroy() {
        if (booksListener != null) {
            booksListener.remove();
            booksListener = null;
        }
        if (bookDao != null) bookDao.close();
        if (categoryDao != null) categoryDao.close();
        super.onDestroy();
    }
}