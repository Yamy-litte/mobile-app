package com.example.mylibrary.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylibrary.R;
import com.example.mylibrary.model.Book;
import com.example.mylibrary.service.BookService;

import java.util.List;

public class ManageBooksActivity extends AppCompatActivity {

    private TextView txtManageBooksBack;
    private Button btnAddBook;
    private EditText edtSearchManageBooks;
    private LinearLayout layoutManageBooksList;

    private BookService bookService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_books);

        txtManageBooksBack = findViewById(R.id.txtManageBooksBack);
        btnAddBook = findViewById(R.id.btnAddBook);
        edtSearchManageBooks = findViewById(R.id.edtSearchManageBooks);
        layoutManageBooksList = findViewById(R.id.layoutManageBooksList);

        bookService = new BookService();

        txtManageBooksBack.setOnClickListener(v -> finish());
        btnAddBook.setOnClickListener(v -> {
            Toast.makeText(this, "Mở màn hình Thêm sách mới", Toast.LENGTH_SHORT).show();
        });

        edtSearchManageBooks.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchBooks(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadAllBooks();
    }

    private void loadAllBooks() {
        bookService.getAvailableBooks(new BookService.BookServiceCallback() {
            @Override
            public void onSuccess(List<Book> books) {
                renderBookList(books);
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(ManageBooksActivity.this, "Lỗi tải sách: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void searchBooks(String keyword) {
        if (keyword.isEmpty()) {
            loadAllBooks();
            return;
        }
        bookService.searchBooks(keyword, new BookService.BookServiceCallback() {
            @Override
            public void onSuccess(List<Book> books) {
                renderBookList(books);
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(ManageBooksActivity.this, "Lỗi tìm kiếm: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderBookList(List<Book> books) {
        layoutManageBooksList.removeAllViews();
        if (books.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Chưa có sách nào trong kho.");
            empty.setTextColor(Color.rgb(102, 112, 133));
            empty.setPadding(0, dp(12), 0, dp(12));
            layoutManageBooksList.addView(empty);
            return;
        }

        for (Book book : books) {
            layoutManageBooksList.addView(createBookRow(book));
        }
    }

    private View createBookRow(Book book) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), Color.rgb(230, 233, 231));
        row.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(10);
        row.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText(book.getTitle() != null ? book.getTitle() : "Tên sách");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(36, 59, 100));

        TextView author = new TextView(this);
        author.setText("Tác giả: " + (book.getAuthor() != null ? book.getAuthor() : "N/A"));
        author.setTextSize(13);
        author.setTextColor(Color.rgb(102, 112, 133));

        TextView status = new TextView(this);
        status.setText("Trạng thái: " + (book.getStatus() != null ? book.getStatus() : "Available"));
        status.setTextSize(13);
        status.setTypeface(null, Typeface.BOLD);
        status.setTextColor(Color.rgb(46, 125, 50));

        row.addView(title);
        row.addView(author);
        row.addView(status);

        row.setOnClickListener(v -> {
            Intent intent = new Intent(ManageBooksActivity.this, BookDetailActivity.class);
            intent.putExtra("book_id", book.getId());
            startActivity(intent);
        });

        return row;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
