package com.example.libraryapp;

import android.content.Intent;
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
import com.example.libraryapp.model.Admin;
import com.example.libraryapp.service.CloudLibraryRepository;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class AdminHomeActivity extends AppCompatActivity {
    private TextView emptyText, countText;
    private final List<RequestItem> requests = new ArrayList<>();
    private RequestAdapter adapter;
    private CloudLibraryRepository cloud;
    private ListenerRegistration requestsListener;
    private String adminUid;

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
        adminUid = auth.getCurrentUid();
        cloud = new CloudLibraryRepository();
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
    }

    @Override protected void onStart() {
        super.onStart();
        if (cloud == null) return;
        requestsListener = cloud.listenPendingReservations((snapshot, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                Toast.makeText(this, "Không tải được yêu cầu chung: " + safe(error.getMessage()), Toast.LENGTH_LONG).show();
                return;
            }
            requests.clear();
            if (snapshot != null) {
                for (QueryDocumentSnapshot doc : snapshot) {
                    requests.add(new RequestItem(
                            doc.getId(),
                            doc.getString("customerUid"),
                            doc.getString("bookId"),
                            doc.getString("bookTitle"),
                            doc.getString("customerName"),
                            doc.getString("customerEmail"),
                            doc.getLong("createdAt") == null ? 0L : doc.getLong("createdAt")));
                }
            }
            adapter.notifyDataSetChanged();
            countText.setText("Yêu cầu đang chờ: " + requests.size());
            emptyText.setVisibility(requests.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void processRequest(RequestItem item, boolean approve) {
        String action = approve ? "duyệt" : "từ chối";
        new AlertDialog.Builder(this)
                .setTitle(approve ? "Duyệt yêu cầu" : "Từ chối yêu cầu")
                .setMessage("Bạn muốn " + action + " yêu cầu mượn “" + safe(item.bookTitle) + "” của " + safe(item.customerName) + "?")
                .setNegativeButton("Quay lại", (d, w) -> d.dismiss())
                .setPositiveButton(approve ? "Duyệt" : "Từ chối", (d, w) -> {
                    cloud.reviewReservation(item.id, approve, adminUid)
                            .addOnSuccessListener(unused -> Toast.makeText(this,
                                    approve ? "Đã duyệt yêu cầu, cập nhật tồn kho và gửi thông báo trong ứng dụng."
                                            : "Đã từ chối yêu cầu và gửi thông báo trong ứng dụng.",
                                    Toast.LENGTH_LONG).show())
                            .addOnFailureListener(error -> Toast.makeText(this,
                                    "Không xử lý được yêu cầu: " + safe(error.getMessage()),
                                    Toast.LENGTH_LONG).show());
                }).show();
    }

    private void goToLogin() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override protected void onStop() {
        if (requestsListener != null) {
            requestsListener.remove();
            requestsListener = null;
        }
        super.onStop();
    }

    private static class RequestItem {
        final String id, customerUid, bookId, bookTitle, customerName, customerEmail;
        final long createdAt;
        RequestItem(String id, String customerUid, String bookId, String bookTitle,
                    String customerName, String customerEmail, long createdAt) {
            this.id = id;
            this.customerUid = customerUid;
            this.bookId = bookId;
            this.bookTitle = bookTitle;
            this.customerName = customerName;
            this.customerEmail = customerEmail;
            this.createdAt = createdAt;
        }
    }

    private class RequestAdapter extends BaseAdapter {
        @Override public int getCount() { return requests.size(); }
        @Override public Object getItem(int position) { return requests.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) row = LayoutInflater.from(AdminHomeActivity.this)
                    .inflate(R.layout.row_admin_borrow_request, parent, false);
            RequestItem item = requests.get(position);
            ((TextView) row.findViewById(R.id.textAdminRequestTitle)).setText(safe(item.bookTitle));
            String date = item.createdAt > 0
                    ? android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", item.createdAt).toString()
                    : "Chưa cập nhật";
            ((TextView) row.findViewById(R.id.textAdminRequestDetails)).setText(
                    "Độc giả: " + safe(item.customerName) + "\nEmail: " + safe(item.customerEmail)
                            + "\nNgày gửi: " + date);
            Button approve = row.findViewById(R.id.btnApproveRequest);
            Button reject = row.findViewById(R.id.btnRejectRequest);
            approve.setOnClickListener(v -> processRequest(item, true));
            reject.setOnClickListener(v -> processRequest(item, false));
            return row;
        }
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Chưa cập nhật" : value;
    }
}