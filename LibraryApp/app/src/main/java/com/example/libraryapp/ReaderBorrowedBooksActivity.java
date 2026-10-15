package com.example.libraryapp;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.CustomerDao;
import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Customer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReaderBorrowedBooksActivity extends AppCompatActivity {
    private LibraryDbHelper dbHelper;
    private TextView emptyText;
    private ItemAdapter adapter;
    private int customerId = -1;
    private final List<BorrowedItem> items = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseAuthManager auth = new FirebaseAuthManager();
        if (!auth.isLoggedIn()) { goToLogin(); return; }

        setContentView(R.layout.activity_reader_borrowed_books);
        ReaderTabNavigation.bind(this);
        emptyText = findViewById(R.id.textBorrowedEmpty);
        ListView list = findViewById(R.id.listBorrowedBooks);
        adapter = new ItemAdapter();
        list.setAdapter(adapter);

        CustomerDao customerDao = new CustomerDao(this);
        Customer customer = customerDao.getByFirebaseUid(auth.getCurrentUid());
        customerDao.close();
        if (customer == null) { goToLogin(); return; }

        customerId = customer.getId();
        dbHelper = new LibraryDbHelper(this);
        loadRecords();
    }

    @Override protected void onResume() {
        super.onResume();
        if (dbHelper != null && customerId > 0 && adapter != null) loadRecords();
    }

    private void loadRecords() {
        items.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // Fetch borrow records and requests separately so a missing optional book-copy
        // reference cannot hide a reservation. Each request is represented by its own row.
        String borrowSql = "SELECT b.title, b.author, br.borrow_date, br.due_date, br.return_date, br.status, bc.barcode " +
                "FROM borrow_records br JOIN book_copies bc ON bc.id=br.book_copy_id " +
                "JOIN books b ON b.id=bc.book_id WHERE br.customer_id=? ORDER BY br.borrow_date DESC";
        try (Cursor c = db.rawQuery(borrowSql, new String[]{String.valueOf(customerId)})) {
            while (c.moveToNext()) {
                String status = c.getString(5);
                String statusText = "returned".equalsIgnoreCase(status) ? "Đã trả" :
                        ("overdue".equalsIgnoreCase(status) ? "Quá hạn" : "Đang mượn");
                String details = "Tác giả: " + safe(c.getString(1)) +
                        "\nMã bản sách: " + safe(c.getString(6)) +
                        "\nNgày mượn: " + safe(c.getString(2)) +
                        "  •  Hạn trả: " + safe(c.getString(3)) +
                        "\nTrạng thái: " + statusText;
                if (!isBlank(c.getString(4))) details += "\nNgày trả: " + c.getString(4);
                items.add(new BorrowedItem(c.getString(0), details, 0, false));
            }
        }

        String requestSql = "SELECT r.id, b.title, r.reservation_date, r.status " +
                "FROM reservations r LEFT JOIN books b ON b.id=r.book_id " +
                "WHERE r.customer_id=? ORDER BY r.id DESC";
        try (Cursor c = db.rawQuery(requestSql, new String[]{String.valueOf(customerId)})) {
            while (c.moveToNext()) {
                int reservationId = c.getInt(0);
                String status = c.getString(3);
                String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
                String label;
                if ("pending".equals(normalized)) label = "Chờ Admin xác nhận";
                else if ("approved".equals(normalized) || "confirmed".equals(normalized)) label = "Đã duyệt";
                else if ("rejected".equals(normalized)) label = "Bị từ chối";
                else if ("cancelled".equals(normalized) || "canceled".equals(normalized)) label = "Đã hủy";
                else label = isBlank(status) ? "Chưa cập nhật" : status;
                String details = "Yêu cầu mượn: " + safe(c.getString(2)) +
                        "\nTrạng thái yêu cầu: " + label;
                items.add(new BorrowedItem(safe(c.getString(1)), details, reservationId,
                        "pending".equals(normalized)));
            }
        }

        adapter.notifyDataSetChanged();
        emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        if (items.isEmpty()) emptyText.setText("Bạn chưa có yêu cầu mượn hoặc lịch sử mượn sách.");
    }

    private void cancelRequest(BorrowedItem item) {
        if (item.reservationId <= 0 || !item.canCancel) return;
        new AlertDialog.Builder(this)
                .setTitle("Hủy yêu cầu mượn")
                .setMessage("Bạn muốn hủy yêu cầu mượn cuốn “" + item.title + "” chứ?")
                .setNegativeButton("Không", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Hủy yêu cầu", (dialog, which) -> {
                    ContentValues values = new ContentValues();
                    values.put("status", "cancelled");
                    int updated = dbHelper.getWritableDatabase().update(
                            LibraryDbHelper.TABLE_RESERVATIONS,
                            values,
                            "id=? AND customer_id=? AND LOWER(status)='pending'",
                            new String[]{String.valueOf(item.reservationId), String.valueOf(customerId)});
                    if (updated > 0) {
                        Toast.makeText(this, "Đã hủy yêu cầu mượn sách.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Yêu cầu không còn ở trạng thái chờ xác nhận nên không thể hủy.", Toast.LENGTH_LONG).show();
                    }
                    loadRecords();
                }).show();
    }

    private String safe(String value) { return isBlank(value) ? "Chưa cập nhật" : value.trim(); }
    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }

    private void goToLogin() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override protected void onDestroy() {
        if (dbHelper != null) dbHelper.close();
        super.onDestroy();
    }

    private static class BorrowedItem {
        final String title;
        final String details;
        final int reservationId;
        final boolean canCancel;
        BorrowedItem(String title, String details, int reservationId, boolean canCancel) {
            this.title = title;
            this.details = details;
            this.reservationId = reservationId;
            this.canCancel = canCancel;
        }
    }

    private class ItemAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) {
            BorrowedItem item = items.get(position);
            return item.reservationId > 0 ? item.reservationId : position;
        }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(ReaderBorrowedBooksActivity.this)
                        .inflate(R.layout.row_reader_borrowed_item, parent, false);
            }
            BorrowedItem item = items.get(position);
            ((TextView) row.findViewById(R.id.textBorrowedItemTitle)).setText(item.title);
            ((TextView) row.findViewById(R.id.textBorrowedItemDetails)).setText(item.details);
            Button cancelButton = row.findViewById(R.id.btnCancelBorrowRequest);
            cancelButton.setVisibility(item.canCancel ? View.VISIBLE : View.GONE);
            cancelButton.setOnClickListener(v -> cancelRequest(item));
            return row;
        }
    }
}