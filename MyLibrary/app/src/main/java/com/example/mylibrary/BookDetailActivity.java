package com.example.mylibrary;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class BookDetailActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_detail);

        TextView tvBookTitle = findViewById(R.id.tvBookTitle);
        TextView tvBookAuthor = findViewById(R.id.tvBookAuthor);
        TextView tvBookPublisher = findViewById(R.id.tvBookPublisher);
        TextView tvBookYear = findViewById(R.id.tvBookYear);
        TextView tvBookSummary = findViewById(R.id.tvBookSummary);
        TextView tvAvailableQuantity = findViewById(R.id.tvAvailableQuantity);
        Button btnReserve = findViewById(R.id.btnReserve);
        Button btnBorrow = findViewById(R.id.btnBorrow);

        tvBookTitle.setText("The Great Gatsby");
        tvBookAuthor.setText("Author: F. Scott Fitzgerald");
        tvBookPublisher.setText("Publisher: Scribner");
        tvBookYear.setText("Year: 1925");
        tvBookSummary.setText("A classic novel about dreams, ambition, and loss in the Jazz Age.");
        tvAvailableQuantity.setText("Available: 3");

        btnReserve.setOnClickListener(v -> Toast.makeText(this, "Book reserved successfully", Toast.LENGTH_SHORT).show());
        btnBorrow.setOnClickListener(v -> Toast.makeText(this, "Book borrowed successfully", Toast.LENGTH_SHORT).show());
    }
}
