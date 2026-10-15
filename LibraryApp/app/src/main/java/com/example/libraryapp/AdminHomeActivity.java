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
import com.example.libraryapp.dao.AdminDao;
import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Admin;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminHomeActivity extends AppCompatActivity {
    private LibraryDbHelper dbHelper;
    private int adminId = -1;
    private TextView emptyText, countText;
    private final List<RequestItem> requests = new ArrayList<>();
    private RequestAdapter adapter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseAuthManager auth = new FirebaseAuthManager();
        if (!auth.isLoggedIn()) { goToLogin(); return; }

        AdminDao adminDao = new AdminDao(this);
        Admin admin = adminDao.getByFirebaseUid(auth.getCurrentUid());
        adminDao.close();
        if (admin == null || !"active".equalsIgnoreCase(admin.getStatus())) {
            auth.logout();
            goToLogin();
            return;
        }
        adminId = admin.getId();
        setContentView(R.layout.activity_admin_home);
        AdminTabNavigation.bind(this);
        emptyText = findViewById(R.id.textAdminRequestsEmpty);
        countText = findViewById(R.id.textAdminRequestCount);
        ListView list = findViewById(R.id.listAdminRequests);
        adapter = new RequestAdapter();
        list.setAdapter(adapter);
        findViewById(R.id.btnAdminLogout).setOnClickListener(v -> {
            auth.logout();
            goToLogin();
        });
        dbHelper = new LibraryDbHelper(this);
        loadRequests();
    }

    @Override protected void onResume() {
        super.onResume();
        if (dbHelper != null && adapter != null) loadRequests();
    }

    private void loadRequests() {
        requests.clear();
        String sql = "SELECT r.id, r.customer_id, b.title, c.full_name, c.email, r.reservation_date, r.status " +
                "FROM reservations r JOIN books b ON b.id=r.book_id " +
                "JOIN customers c ON c.id=r.customer_id " +
                "WHERE LOWER(r.status)='pending' ORDER BY r.id DESC";
        try (Cursor c = dbHelper.getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                requests.add(new RequestItem(c.getInt(0), c.getInt(1), c.getString(2), c.getString(3),
                        c.getString(4), c.getString(5), c.getString(6)));
            }
        }
        adapter.notifyDataSetChanged();
        countText.setText("Yêu cầu đang chờ: " + requests.size());
        emptyText.setVisibility(requests.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void processRequest(RequestItem item, boolean approve) {
        String action = approve ? "duyệt" : "từ chối";
        new AlertDialog.Builder(this)
                .setTitle(approve ? "Duyệt yêu cầu" : "Từ chối yêu cầu")
                .setMessage("Bạn muốn " + action + " yêu cầu mượn “" + item.bookTitle + "” của " + item.customerName + "?")
                .setNegativeButton("Quay lại", (d, w) -> d.dismiss())
                .setPositiveButton(approve ? "Duyệt" : "Từ chối", (d, w) -> {
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    boolean success = false;
                    String emailSubject = approve ? "Yêu cầu mượn sách đã được duyệt - MyLibrary" : "Cập nhật yêu cầu mượn sách - MyLibrary";
                    String emailBody = "Xin chào " + safe(item.customerName) + ",\n\nYêu cầu mượn sách “" + item.bookTitle + "” " +
                            (approve ? "đã được duyệt." : "đã bị từ chối.") +
                            (approve ? "\nVui lòng liên hệ thư viện để nhận sách." : "") +
                            "\n\nTrân trọng,\nMyLibrary";
                    db.beginTransaction();
                    try {
                        Cursor pending = db.rawQuery(
                                "SELECT id FROM reservations WHERE id=? AND LOWER(status)='pending'",
                                new String[]{String.valueOf(item.id)});
                        boolean stillPending;
                        try { stillPending = pending.moveToFirst(); } finally { pending.close(); }
                        if (!stillPending) {
                            Toast.makeText(this, "Yêu cầu đã được xử lý trước đó.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        int copyId = -1;
                        if (approve) {
                            try (Cursor copy = db.rawQuery(
                                    "SELECT id FROM book_copies WHERE book_id=? AND LOWER(status)='available' ORDER BY id LIMIT 1",
                                    new String[]{String.valueOf(item.bookId)})) {
                                if (copy.moveToFirst()) copyId = copy.getInt(0);
                            }
                            if (copyId < 0) {
                                Toast.makeText(this, "Không còn bản sao khả dụng để cấp mượn. Chưa duyệt yêu cầu.", Toast.LENGTH_LONG).show();
                                return;
                            }
                            ContentValues loan = new ContentValues();
                            loan.put("customer_id", item.customerId);
                            loan.put("book_copy_id", copyId);
                            loan.put("admin_borrow_id", adminId);
                            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                            java.util.Calendar calendar = java.util.Calendar.getInstance();
                            calendar.add(java.util.Calendar.DAY_OF_YEAR, 14);
                            String due = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(calendar.getTime());
                            loan.put("borrow_date", now);
                            loan.put("due_date", due);
                            loan.put("status", "borrowed");
                            long loanId = db.insert("borrow_records", null, loan);
                            if (loanId < 0) throw new IllegalStateException("Không tạo được phiếu mượn.");
                            ContentValues copyUpdate = new ContentValues();
                            copyUpdate.put("status", "borrowed");
                            if (db.update("book_copies", copyUpdate, "id=? AND LOWER(status)='available'",
                                    new String[]{String.valueOf(copyId)}) != 1) {
                                throw new IllegalStateException("Bản sao sách vừa được cấp cho yêu cầu khác.");
                            }
                            db.execSQL("UPDATE books SET available_quantity=CASE WHEN available_quantity>0 THEN available_quantity-1 ELSE 0 END WHERE id=?",
                                    new Object[]{item.bookId});
                        }

                        ContentValues update = new ContentValues();
                        update.put("status", approve ? "approved" : "rejected");
                        int changed = db.update("reservations", update,
                                "id=? AND LOWER(status)='pending'",
                                new String[]{String.valueOf(item.id)});
                        if (changed != 1) throw new IllegalStateException("Không cập nhật được trạng thái yêu cầu.");

                        ContentValues notification = new ContentValues();
                        notification.put("customer_id", item.customerId);
                        notification.putNull("admin_id");
                        notification.put("title", approve ? "Yêu cầu mượn đã được duyệt" : "Yêu cầu mượn bị từ chối");
                        notification.put("content", "Yêu cầu mượn sách “" + item.bookTitle + "” " +
                                (approve ? "đã được duyệt. Hạn trả dự kiến sau 14 ngày." : "đã bị Admin từ chối."));
                        notification.put("is_read", 0);
                        notification.put("created_at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                        if (db.insert("notifications", null, notification) < 0) throw new IllegalStateException("Không tạo được thông báo trong ứng dụng.");
                        db.setTransactionSuccessful();
                        success = true;
                    } catch (Exception ex) {
                        Toast.makeText(this, "Không xử lý được yêu cầu: " + safe(ex.getMessage()), Toast.LENGTH_LONG).show();
                    } finally {
                        db.endTransaction();
                    }
                    if (success) {
                        Toast.makeText(this, approve ? "Đã duyệt, tạo phiếu mượn và gửi thông báo trong ứng dụng." : "Đã từ chối và tạo thông báo trong ứng dụng.", Toast.LENGTH_LONG).show();
                        loadRequests();
                        // Opens an email composer addressed to the customer's Gmail.
                        // Android apps must not silently send email using embedded SMTP credentials.
                        if (item.email != null && item.email.contains("@")) {
                            Intent email = new Intent(Intent.ACTION_SENDTO);
                            email.setData(android.net.Uri.parse("mailto:" + android.net.Uri.encode(item.email)));
                            email.putExtra(Intent.EXTRA_SUBJECT, emailSubject);
                            email.putExtra(Intent.EXTRA_TEXT, emailBody);
                            try {
                                startActivity(Intent.createChooser(email, "Gửi email thông báo cho độc giả"));
                            } catch (android.content.ActivityNotFoundException ignored) {
                                Toast.makeText(this, "Thông báo đã lưu trong ứng dụng; thiết bị chưa có ứng dụng email để gửi Gmail.", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                }).show();
    }

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

    private static class RequestItem {
        final int id, customerId;
        final String bookTitle, customerName, email, date, status;
        RequestItem(int id, int customerId, String bookTitle, String customerName, String email, String date, String status) {
            this.id = id; this.customerId = customerId; this.bookTitle = bookTitle; this.customerName = customerName;
            this.email = email; this.date = date; this.status = status;
        }
    }

    private class RequestAdapter extends BaseAdapter {
        @Override public int getCount() { return requests.size(); }
        @Override public Object getItem(int position) { return requests.get(position); }
        @Override public long getItemId(int position) { return requests.get(position).id; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) row = LayoutInflater.from(AdminHomeActivity.this).inflate(R.layout.row_admin_borrow_request, parent, false);
            RequestItem item = requests.get(position);
            ((TextView) row.findViewById(R.id.textAdminRequestTitle)).setText(item.bookTitle);
            ((TextView) row.findViewById(R.id.textAdminRequestDetails)).setText(
                    "Độc giả: " + safe(item.customerName) + "\nEmail: " + safe(item.email) +
                    "\nNgày gửi: " + safe(item.date));
            Button approve = row.findViewById(R.id.btnApproveRequest);
            Button reject = row.findViewById(R.id.btnRejectRequest);
            approve.setOnClickListener(v -> processRequest(item, true));
            reject.setOnClickListener(v -> processRequest(item, false));
            return row;
        }
    }
    private String safe(String value) { return value == null || value.trim().isEmpty() ? "Chưa cập nhật" : value; }
}