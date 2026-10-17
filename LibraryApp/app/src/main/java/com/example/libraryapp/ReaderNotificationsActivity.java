package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.BaseAdapter;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;
import com.example.libraryapp.service.CloudLibraryRepository;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class ReaderNotificationsActivity extends AppCompatActivity {
    private final List<String> rows = new ArrayList<>();
    private final List<String> documentIds = new ArrayList<>();
    private final List<Boolean> readStates = new ArrayList<>();
    private final List<String> titles = new ArrayList<>();
    private final List<String> contents = new ArrayList<>();
    private final List<String> dates = new ArrayList<>();
    private NotificationAdapter adapter;
    private TextView emptyText;
    private CloudLibraryRepository cloud;
    private ListenerRegistration notificationsListener;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseAuthManager auth = new FirebaseAuthManager();
        if (!auth.isLoggedIn()) { goToLogin(); return; }
        cloud = new CloudLibraryRepository();
        setContentView(R.layout.activity_reader_notifications);
        ReaderTabNavigation.bind(this);
        emptyText = findViewById(R.id.textNotificationEmpty);
        ListView list = findViewById(R.id.listNotifications);
        adapter = new NotificationAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= documentIds.size() || readStates.get(position)) return;
            cloud.markNotificationRead(documentIds.get(position))
                    .addOnSuccessListener(unused -> Toast.makeText(this, "Đã đánh dấu thông báo là đã đọc.", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(error -> Toast.makeText(this,
                            "Không cập nhật được thông báo: " + safe(error.getMessage()), Toast.LENGTH_LONG).show());
        });
    }

    @Override protected void onStart() {
        super.onStart();
        if (cloud == null) return;
        notificationsListener = cloud.listenMyNotifications((snapshot, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                Toast.makeText(this, "Không tải được thông báo chung: " + safe(error.getMessage()), Toast.LENGTH_LONG).show();
                return;
            }
            rows.clear();
            documentIds.clear();
            readStates.clear();
            titles.clear();
            contents.clear();
            dates.clear();
            if (snapshot != null) {
                for (QueryDocumentSnapshot doc : snapshot) {
                    String title = doc.getString("title");
                    String content = doc.getString("content");
                    Boolean isRead = doc.getBoolean("isRead");
                    Long createdAt = doc.getLong("createdAt");
                    documentIds.add(doc.getId());
                    readStates.add(Boolean.TRUE.equals(isRead));
                    String date = createdAt == null ? "" :
                            android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", createdAt).toString();
                    titles.add(safe(title));
                    contents.add(safe(content));
                    dates.add(date);
                    rows.add(safe(title));
                }
            }
            adapter.notifyDataSetChanged();
            emptyText.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private class NotificationAdapter extends BaseAdapter {
        @Override public int getCount() { return titles.size(); }
        @Override public Object getItem(int position) { return titles.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView == null
                    ? LayoutInflater.from(ReaderNotificationsActivity.this)
                        .inflate(R.layout.row_reader_notification, parent, false)
                    : convertView;
            TextView title = row.findViewById(R.id.textNotificationTitle);
            TextView content = row.findViewById(R.id.textNotificationContent);
            TextView date = row.findViewById(R.id.textNotificationDate);
            TextView state = row.findViewById(R.id.textNotificationState);
            title.setText(titles.get(position));
            content.setText(contents.get(position));
            date.setText(dates.get(position));
            boolean read = readStates.get(position);
            state.setText(read ? "ĐÃ ĐỌC" : "CHƯA ĐỌC");
            state.setTextColor(getColor(read ? R.color.mylibrary_muted : R.color.mylibrary_teal));
            row.setAlpha(read ? 0.82f : 1f);
            return row;
        }
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "" : value;
    }

    private void goToLogin() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override protected void onStop() {
        if (notificationsListener != null) {
            notificationsListener.remove();
            notificationsListener = null;
        }
        super.onStop();
    }
}