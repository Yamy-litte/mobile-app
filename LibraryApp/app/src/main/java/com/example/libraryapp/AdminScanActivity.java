package com.example.libraryapp;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.util.ArrayList;
import java.util.List;

/** Admin workflow: scan a Reader's Code 128 library card, inspect active loans, then confirm returns. */
public class AdminScanActivity extends AppCompatActivity {
    private CloudLibraryRepository cloud;
    private String adminUid;
    private TextView statusText, readerText, emptyText;
    private Button scanButton;
    private ListView borrowList;
    private final List<BorrowItem> activeLoans = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private final List<String> rowLabels = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FirebaseAuthManager auth = new FirebaseAuthManager();
        if (!auth.isLoggedIn()) { goToLogin(); return; }
        AdminDao dao = new AdminDao(this);
        Admin admin = dao.getByFirebaseUid(auth.getCurrentUid());
        dao.close();
        if (admin == null || !"active".equalsIgnoreCase(admin.getStatus())) {
            auth.logout();
            goToLogin();
            return;
        }
        adminUid = auth.getCurrentUid();
        cloud = new CloudLibraryRepository();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_admin_scan);
        AdminTabNavigation.bind(this);
        statusText = findViewById(R.id.textScanStatus);
        readerText = findViewById(R.id.textScannedReader);
        emptyText = findViewById(R.id.textReaderLoansEmpty);
        scanButton = findViewById(R.id.btnStartCardScan);
        borrowList = findViewById(R.id.listReaderActiveLoans);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rowLabels);
        borrowList.setAdapter(adapter);
        scanButton.setOnClickListener(v -> startCardScan());
        borrowList.setOnItemClickListener((parent, view, position, id) -> confirmReturn(activeLoans.get(position)));
        findViewById(R.id.btnScanBackHome).setOnClickListener(v -> finish());
    }

    private void startCardScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setCaptureActivity(PortraitCaptureActivity.class);
        integrator.setPrompt("Quét barcode trên thẻ thư viện số của độc giả");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(true);
        integrator.setBarcodeImageEnabled(false);
        integrator.setCameraId(0);
        integrator.setTimeout(30000L);
        integrator.initiateScan();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result == null) return;
        if (result.getContents() == null) {
            statusText.setText("Đã hủy quét mã. Chưa có dữ liệu nào thay đổi.");
            return;
        }
        lookupReader(result.getContents().trim());
    }

    private void lookupReader(String cardNumber) {
        statusText.setText("Đã đọc barcode: [" + cardNumber + "]. Đang đối chiếu mã thẻ...");
        cloud.findReaderByCardNumber(cardNumber)
                .addOnSuccessListener(snapshot -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (snapshot.isEmpty()) {
                        readerText.setText("Không tìm thấy độc giả có mã thẻ: " + cardNumber
                                + "\nĐộc giả cần mở thẻ thư viện số trên thiết bị của mình để đồng bộ mã thẻ.");
                        activeLoans.clear(); rowLabels.clear(); adapter.notifyDataSetChanged();
                        emptyText.setVisibility(View.VISIBLE);
                        emptyText.setText("Không tìm thấy hồ sơ độc giả.");
                        statusText.setText("Không tìm thấy thẻ.");
                        return;
                    }
                    if (snapshot.size() > 1) {
                        statusText.setText("Mã thẻ trùng lặp. Dừng xử lý để tránh cập nhật nhầm độc giả.");
                        return;
                    }
                    DocumentSnapshot reader = snapshot.getDocuments().get(0);
                    String uid = reader.getId();
                    scannedReaderUid = uid;
                    String name = reader.getString("fullName");
                    String email = reader.getString("email");
                    String state = reader.getString("status");
                    if (state != null && !"active".equalsIgnoreCase(state)) {
                        statusText.setText("Thẻ hoặc tài khoản độc giả không hoạt động. Không thể xử lý trả sách.");
                        readerText.setText("Độc giả: " + safe(name) + "\nMã thẻ: " + cardNumber);
                        return;
                    }
                    readerText.setText("Độc giả: " + safe(name) + "\nEmail: " + safe(email)
                            + "\nMã thẻ: " + cardNumber);
                    statusText.setText("Đã nhận diện độc giả. Chọn một phiếu đang mượn để xác nhận trả.");
                    loadActiveLoans(uid);
                })
                .addOnFailureListener(error -> statusText.setText(
                        "Không tra cứu được thẻ: " + safe(error.getMessage())));
    }

    private void loadActiveLoans(String customerUid) {
        cloud.getReaderBorrowRecords(customerUid)
                .addOnSuccessListener(snapshot -> {
                    activeLoans.clear();
                    rowLabels.clear();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        if (!"borrowed".equalsIgnoreCase(doc.getString("status"))) continue;
                        long dueAt = doc.getLong("dueAt") == null ? 0L : doc.getLong("dueAt");
                        activeLoans.add(new BorrowItem(doc.getId(), doc.getString("bookTitle"), dueAt));
                    }
                    for (BorrowItem item : activeLoans) {
                        String due = item.dueAt > 0
                                ? android.text.format.DateFormat.format("dd/MM/yyyy", item.dueAt).toString()
                                : "Chưa có hạn trả";
                        rowLabels.add(item.title + "\nHạn trả: " + due + "\nChạm để xác nhận trả sách");
                    }
                    adapter.notifyDataSetChanged();
                    emptyText.setVisibility(activeLoans.isEmpty() ? View.VISIBLE : View.GONE);
                    emptyText.setText("Độc giả hiện không có sách đang mượn.");
                })
                .addOnFailureListener(error -> {
                    statusText.setText("Không tải được phiếu mượn: " + safe(error.getMessage()));
                });
    }

    private void confirmReturn(BorrowItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận trả sách")
                .setMessage("Xác nhận độc giả đã trả “" + safe(item.title)
                        + "”? Chỉ khi xác nhận thành công, trạng thái phiếu và tồn kho mới được cập nhật.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận trả", (dialog, which) -> {
                    statusText.setText("Đang ghi nhận trả sách...");
                    cloud.returnBorrowRecord(item.id, adminUid)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Đã xác nhận trả sách và cập nhật tồn kho.", Toast.LENGTH_LONG).show();
                                statusText.setText("Trả sách thành công.");
                                // Reload the currently selected Reader's active loans from the known item context.
                                if (scannedReaderUid != null) loadActiveLoans(scannedReaderUid);
                            })
                            .addOnFailureListener(error -> {
                                statusText.setText("Không thể xác nhận trả sách: " + safe(error.getMessage()));
                            });
                }).show();
    }

    private String scannedReaderUid;

    private void goToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Chưa cập nhật" : value;
    }

    private static class BorrowItem {
        final String id;
        final String title;
        final long dueAt;
        BorrowItem(String id, String title, long dueAt) {
            this.id = id;
            this.title = title == null ? "Sách chưa có tên" : title;
            this.dueAt = dueAt;
        }
    }
}
