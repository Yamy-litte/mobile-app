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
import com.example.libraryapp.database.SampleDataSeeder;
import com.example.libraryapp.model.Book;
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
    private final String[] categories = {"Tất cả danh mục", "Văn học", "Khoa học", "Công nghệ", "Kỹ năng sống", "Lịch sử", "Thiếu nhi"};

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
        findViewById(R.id.tabHome).setOnClickListener(v -> {
            searchInput.setText("");
            categorySpinner.setSelection(0);
        });

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);
        bookAdapter = new ReaderBookAdapter(this, visibleBooks);
        bookList.setAdapter(bookAdapter);
        SampleDataSeeder.seed(this);
        bookDao = new BookDao(this);
        allBooks.addAll(bookDao.getAll());
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
            Toast.makeText(this, book.getTitle() + "\nTác giả: " + book.getAuthor()
                    + "\nCòn " + book.getAvailableQuantity() + "/" + book.getTotalQuantity() + " cuốn",
                    Toast.LENGTH_LONG).show();
        });
    }

    private void filterBooks() {
        if (bookAdapter == null) return;
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        int selectedCategory = categorySpinner == null ? 0 : categorySpinner.getSelectedItemPosition();
        visibleBooks.clear();
        for (Book book : allBooks) {
            boolean matchesQuery = query.isEmpty() || contains(book.getTitle(), query)
                    || contains(book.getAuthor(), query) || contains(book.getPublisher(), query);
            boolean matchesCategory = selectedCategory == 0 || book.getCategoryId() == selectedCategory;
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
        if (bookDao != null) bookDao.close();
        super.onDestroy();
    }
}