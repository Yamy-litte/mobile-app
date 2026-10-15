package com.example.libraryapp;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.dao.CustomerDao;
import com.example.libraryapp.dao.NotificationDao;
import com.example.libraryapp.database.LibraryDbHelper;
import com.example.libraryapp.model.Customer;
import com.example.libraryapp.model.Notification;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReaderNotificationsActivity extends AppCompatActivity {
    private NotificationDao notificationDao;
    private final List<Notification> notifications = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView emptyText;
    private int customerId;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseAuthManager auth = new FirebaseAuthManager();
        if (!auth.isLoggedIn()) { goToLogin(); return; }
        setContentView(R.layout.activity_reader_notifications);
        ReaderTabNavigation.bind(this);
        emptyText = findViewById(R.id.textNotificationEmpty);
        ListView list = findViewById(R.id.listNotifications);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        list.setAdapter(adapter);
        CustomerDao customerDao = new CustomerDao(this);
        Customer customer = customerDao.getByFirebaseUid(auth.getCurrentUid());
        customerDao.close();
        if (customer == null) { goToLogin(); return; }
        customerId = customer.getId();
        notificationDao = new NotificationDao(this);
        seedDemoNotificationsIfNeeded();
        loadNotifications();
        list.setOnItemClickListener((parent, view, position, id) -> {
            Notification item = notifications.get(position);
            if (!item.getIsRead()) {
                notificationDao.markRead(item.getId());
                item.setIsRead(true);
                loadNotifications();
                Toast.makeText(this, "Đã đánh dấu thông báo là đã đọc.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void seedDemoNotificationsIfNeeded() {
        if (!notificationDao.getByCustomerId(customerId).isEmpty()) return;
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        notificationDao.insert(new Notification(0, customerId, 0, "Chào mừng đến MyLibrary", "Bạn có thể tìm kiếm sách và theo dõi hoạt động thư viện tại đây.", false, now));
        notificationDao.insert(new Notification(0, customerId, 0, "Mẹo sử dụng", "Chọn mục Mượn sách để xem các lượt mượn và hạn trả khi thư viện cập nhật dữ liệu.", false, now));
    }

    private void loadNotifications() {
        notifications.clear(); rows.clear();
        notifications.addAll(notificationDao.getByCustomerId(customerId));
        for (Notification n : notifications) {
            rows.add((n.getIsRead() ? "✓ " : "● ") + n.getTitle() + "\n" + n.getContent() + "\n" + (n.getCreatedAt() == null ? "" : n.getCreatedAt()));
        }
        adapter.notifyDataSetChanged();
        emptyText.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }
    private void goToLogin() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i); finish();
    }
    @Override protected void onDestroy() { if (notificationDao != null) notificationDao.close(); super.onDestroy(); }
}