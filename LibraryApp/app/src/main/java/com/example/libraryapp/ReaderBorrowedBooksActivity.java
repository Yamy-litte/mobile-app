package com.example.libraryapp;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.CustomerDao;
import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Customer;
import java.util.ArrayList;
import java.util.List;

public class ReaderBorrowedBooksActivity extends AppCompatActivity {
    private LibraryDbHelper dbHelper;
    private TextView emptyText;
    private final List<String> rows = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseAuthManager auth = new FirebaseAuthManager();
        if (!auth.isLoggedIn()) { goToLogin(); return; }
        setContentView(R.layout.activity_reader_borrowed_books);
        ReaderTabNavigation.bind(this);
        emptyText = findViewById(R.id.textBorrowedEmpty);
        ListView list = findViewById(R.id.listBorrowedBooks);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
        CustomerDao customerDao = new CustomerDao(this);
        Customer customer = customerDao.getByFirebaseUid(auth.getCurrentUid());
        customerDao.close();
        if (customer == null) { goToLogin(); return; }
        dbHelper = new LibraryDbHelper(this);
        loadRecords(customer.getId());
        ((ArrayAdapter<?>) list.getAdapter()).notifyDataSetChanged();
        emptyText.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void loadRecords(int customerId) {
        rows.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT b.title, b.author, br.borrow_date, br.due_date, br.return_date, br.status, bc.barcode " +
                "FROM borrow_records br JOIN book_copies bc ON bc.id=br.book_copy_id " +
                "JOIN books b ON b.id=bc.book_id WHERE br.customer_id=? ORDER BY br.borrow_date DESC";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(customerId)})) {
            while (c.moveToNext()) {
                String title = c.getString(0);
                String author = c.getString(1);
                String borrowDate = c.getString(2);
                String dueDate = c.getString(3);
                String returnDate = c.getString(4);
                String status = c.getString(5);
                String barcode = c.getString(6);
                String statusText = "returned".equalsIgnoreCase(status) ? "Đã trả" :
                        ("overdue".equalsIgnoreCase(status) ? "Quá hạn" : "Đang mượn");
                rows.add(title + "\nTác giả: " + author + "\nMã bản sách: " + barcode +
                        "\nNgày mượn: " + safe(borrowDate) + "  •  Hạn trả: " + safe(dueDate) +
                        "\nTrạng thái: " + statusText + (returnDate == null || returnDate.trim().isEmpty() ? "" : "\nNgày trả: " + returnDate));
            }
        }

        String requestSql = "SELECT b.title, r.reservation_date, r.status FROM reservations r " +
                "JOIN books b ON b.id=r.book_id WHERE r.customer_id=? ORDER BY r.reservation_date DESC";
        try (Cursor requestCursor = db.rawQuery(requestSql, new String[]{String.valueOf(customerId)})) {
            while (requestCursor.moveToNext()) {
                String title = requestCursor.getString(0);
                String date = requestCursor.getString(1);
                String status = requestCursor.getString(2);
                String label = "pending".equalsIgnoreCase(status) ? "Chờ Admin xác nhận" : status;
                if ("approved".equalsIgnoreCase(status) || "confirmed".equalsIgnoreCase(status)) label = "Đã duyệt";
                else if ("rejected".equalsIgnoreCase(status)) label = "Bị từ chối";
                else if ("cancelled".equalsIgnoreCase(status) || "canceled".equalsIgnoreCase(status)) label = "Đã hủy";
                rows.add(title + "\nYêu cầu mượn: " + safe(date) + "\nTrạng thái yêu cầu: " + label);
            }
        }
    }
    private String safe(String s) { return s == null || s.trim().isEmpty() ? "Chưa cập nhật" : s; }
    private void goToLogin() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i); finish();
    }
    @Override protected void onDestroy() { if (dbHelper != null) dbHelper.close(); super.onDestroy(); }
}