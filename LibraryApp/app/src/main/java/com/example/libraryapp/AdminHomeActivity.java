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
                    db.beginTransaction();
                    try {
                        ContentValues update = new ContentValues();
                        update.put("status", approve ? "approved" : "rejected");
                        int changed = db.update("reservations", update,
                                "id=? AND LOWER(status)='pending'",
                                new String[]{String.valueOf(item.id)});
                        if (changed == 1) {
                            ContentValues notification = new ContentValues();
                            notification.put("customer_id", item.customerId);
                            notification.putNull("admin_id");
                            notification.put("title", approve ? "Yêu cầu mượn đã được duyệt" : "Yêu cầu mượn bị từ chối");
                            notification.put("content", "Yêu cầu mượn sách “" + item.bookTitle + "” " +
                                    (approve ? "đã được Admin duyệt." : "đã bị Admin từ chối."));
                            notification.put("is_read", 0);
                            notification.put("created_at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                            db.insert("notifications", null, notification);
                            db.setTransactionSuccessful();
                            Toast.makeText(this, approve ? "Đã duyệt yêu cầu." : "Đã từ chối yêu cầu.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Yêu cầu đã được xử lý trước đó.", Toast.LENGTH_LONG).show();
                        }
                    } finally {
                        db.endTransaction();
                    }
                    loadRequests();
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