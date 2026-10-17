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
import com.example.libraryapp.dao.BookDao;
import com.example.libraryapp.model.Admin;
import com.example.libraryapp.model.Book;
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
    private ListenerRegistration loansListener;
    private final List<LoanItem> activeLoans = new ArrayList<>();
    private boolean showingLoans = false;
    private ListView requestList;
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
        requestList = findViewById(R.id.listAdminRequests);
        adapter = new RequestAdapter();
        requestList.setAdapter(adapter);
        findViewById(R.id.btnShowRequests).setOnClickListener(v -> showRequests());
        findViewById(R.id.btnShowLoans).setOnClickListener(v -> showActiveLoans());
        requestList.setOnItemClickListener((parent, view, position, id) -> {
            if (showingLoans && position < activeLoans.size()) processReturn(activeLoans.get(position));
        });
        findViewById(R.id.btnAdminLogout).setOnClickListener(v -> {
            auth.logout();
            goToLogin();
        });
    }

    @Override protected void onStart() {
        super.onStart();
        if (cloud == null) return;
        cloud.seedDemoBooksIfMissing()
                .continueWithTask(seedTask -> {
                    if (!seedTask.isSuccessful()) {
                        Exception error = seedTask.getException();
                        throw error == null ? new IllegalStateException("Không đồng bộ được dữ liệu sách mẫu.") : error;
                    }
                    BookDao bookDao = new BookDao(this);
                    try {
                        List<Book> localBooks = bookDao.getAll();
                        return cloud.syncLocalBooks(localBooks);
                    } finally {
                        bookDao.close();
                    }
                })
                .addOnFailureListener(error -> Toast.makeText(this,
                        "Không đồng bộ được danh mục sách SQLite lên Firestore: " + safe(error.getMessage()),
                        Toast.LENGTH_LONG).show());
        loansListener = cloud.listenActiveBorrowRecords((snapshot, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                Toast.makeText(this, "Không tải được phiếu mượn: " + safe(error.getMessage()), Toast.LENGTH_LONG).show();
                return;
            }
            activeLoans.clear();
            if (snapshot != null) {
                for (QueryDocumentSnapshot doc : snapshot) {
                    activeLoans.add(new LoanItem(
                            doc.getId(),
                            doc.getString("customerUid"),
                            doc.getString("bookTitle"),
                            doc.getLong("borrowedAt") == null ? 0L : doc.getLong("borrowedAt"),
                            doc.getLong("dueAt") == null ? 0L : doc.getLong("dueAt"),
                            doc.getString("status")));
                }
            }
            if (showingLoans) refreshList();
        });
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
                            doc.getLong("createdAt") == null ? 0L : doc.getLong("createdAt"),
                            doc.getString("requestType")));
                }
            }
            if (!showingLoans) refreshList();
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

    private void showRequests() {
        showingLoans = false;
        refreshList();
    }

    private void showActiveLoans() {
        showingLoans = true;
        refreshList();
    }

    private void refreshList() {
        if (adapter == null) return;
        adapter.notifyDataSetChanged();
        if (showingLoans) {
            countText.setText("Phiếu đang mượn: " + activeLoans.size());
            emptyText.setText("Hiện không có phiếu mượn đang hoạt động.");
            emptyText.setVisibility(activeLoans.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            countText.setText("Yêu cầu đang chờ: " + requests.size());
            emptyText.setText("Hiện không có yêu cầu mượn đang chờ.");
            emptyText.setVisibility(requests.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void processReturn(LoanItem item) {
        if ("awaiting_pickup".equalsIgnoreCase(item.status)) {
            Toast.makeText(this, "Phiếu đã duyệt nhưng độc giả chưa nhận sách. Vào mục Quét mã để đối chiếu thẻ và xác nhận giao sách.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận trả sách")
                .setMessage("Xác nhận đã nhận lại sách “" + safe(item.bookTitle)
                        + "” từ độc giả có UID " + safe(item.customerUid) + "?"
                        + "\nTrạng thái phiếu và tồn kho chỉ thay đổi khi giao dịch thành công.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận trả", (dialog, which) -> {
                    cloud.returnBorrowRecord(item.id, adminUid)
                            .addOnSuccessListener(unused -> Toast.makeText(this,
                                    "Đã xác nhận trả sách và cập nhật tồn kho.", Toast.LENGTH_LONG).show())
                            .addOnFailureListener(error -> Toast.makeText(this,
                                    "Không thể trả sách: " + safe(error.getMessage()), Toast.LENGTH_LONG).show());
                }).show();
    }

    private static class LoanItem {
        final String id, customerUid, bookTitle;
        final long borrowedAt, dueAt;
        final String status;
        LoanItem(String id, String customerUid, String bookTitle, long borrowedAt, long dueAt, String status) {
            this.id = id;
            this.customerUid = customerUid;
            this.bookTitle = bookTitle;
            this.borrowedAt = borrowedAt;
            this.dueAt = dueAt;
            this.status = status == null ? "" : status;
        }
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
        if (loansListener != null) {
            loansListener.remove();
            loansListener = null;
        }
        super.onStop();
    }

    private static class RequestItem {
        final String id, customerUid, bookId, bookTitle, customerName, customerEmail;
        final long createdAt;
        final String requestType;
        RequestItem(String id, String customerUid, String bookId, String bookTitle,
                    String customerName, String customerEmail, long createdAt, String requestType) {
            this.id = id;
            this.customerUid = customerUid;
            this.bookId = bookId;
            this.bookTitle = bookTitle;
            this.customerName = customerName;
            this.customerEmail = customerEmail;
            this.createdAt = createdAt;
            this.requestType = requestType;
        }
    }

    private class RequestAdapter extends BaseAdapter {
        @Override public int getCount() { return showingLoans ? activeLoans.size() : requests.size(); }
        @Override public Object getItem(int position) { return showingLoans ? activeLoans.get(position) : requests.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return showingLoans ? 1 : 0; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            if (showingLoans) {
                View row = LayoutInflater.from(AdminHomeActivity.this)
                        .inflate(R.layout.row_admin_borrow_request, parent, false);
                LoanItem item = activeLoans.get(position);
                ((TextView) row.findViewById(R.id.textAdminRequestTitle)).setText(safe(item.bookTitle));
                String borrowed = item.borrowedAt > 0
                        ? android.text.format.DateFormat.format("dd/MM/yyyy", item.borrowedAt).toString()
                        : "Chưa cập nhật";
                String due = item.dueAt > 0
                        ? android.text.format.DateFormat.format("dd/MM/yyyy", item.dueAt).toString()
                        : "Chưa có hạn trả";
                ((TextView) row.findViewById(R.id.textAdminRequestDetails)).setText(
                        "Trạng thái: ĐANG MƯỢN\nMã độc giả: " + safe(item.customerUid)
                                + "\nNgày mượn: " + borrowed + "\nHạn trả: " + due
                                + "\nChạm vào phiếu để xác nhận trả sách.");
                row.findViewById(R.id.btnApproveRequest).setVisibility(View.GONE);
                Button reject = row.findViewById(R.id.btnRejectRequest);
                reject.setText("Xác nhận trả");
                reject.setOnClickListener(v -> processReturn(item));
                return row;
            }
            View row = LayoutInflater.from(AdminHomeActivity.this)
                    .inflate(R.layout.row_admin_borrow_request, parent, false);
            RequestItem item = requests.get(position);
            ((TextView) row.findViewById(R.id.textAdminRequestTitle)).setText(safe(item.bookTitle));
            String date = item.createdAt > 0
                    ? android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", item.createdAt).toString()
                    : "Chưa cập nhật";
            String requestLabel = "preorder".equalsIgnoreCase(item.requestType)
                    ? "ĐẶT TRƯỚC (chờ có sách)" : "MƯỢN SÁCH";
            ((TextView) row.findViewById(R.id.textAdminRequestDetails)).setText(
                    "Loại yêu cầu: " + requestLabel + "\nĐộc giả: " + safe(item.customerName)
                            + "\nEmail: " + safe(item.customerEmail) + "\nNgày gửi: " + date);
            row.findViewById(R.id.btnApproveRequest).setVisibility(View.VISIBLE);
            row.findViewById(R.id.btnRejectRequest).setVisibility(View.VISIBLE);
            Button approve = row.findViewById(R.id.btnApproveRequest);
            Button reject = row.findViewById(R.id.btnRejectRequest);
            approve.setText("Duyệt");
            reject.setText("Từ chối");
            approve.setOnClickListener(v -> processRequest(item, true));
            reject.setOnClickListener(v -> processRequest(item, false));
            return row;
        }
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Chưa cập nhật" : value;
    }
}