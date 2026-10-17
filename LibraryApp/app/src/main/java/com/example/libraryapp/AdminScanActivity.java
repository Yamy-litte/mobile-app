package com.example.libraryapp;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
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
    private String scannedReaderUid;
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
        borrowList.setOnItemClickListener((parent, view, position, id) -> {
            BorrowItem item = activeLoans.get(position);
            if ("awaiting_pickup".equalsIgnoreCase(item.status)) confirmPickup(item);
            else confirmReturn(item);
        });
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
                        String loanStatus = doc.getString("status");
                        if (!"borrowed".equalsIgnoreCase(loanStatus)
                                && !"awaiting_pickup".equalsIgnoreCase(loanStatus)) continue;
                        long dueAt = doc.getLong("dueAt") == null ? 0L : doc.getLong("dueAt");
                        activeLoans.add(new BorrowItem(doc.getId(), doc.getString("bookTitle"), dueAt, loanStatus));
                    }
                    for (BorrowItem item : activeLoans) {
                        String due = item.dueAt > 0
                                ? android.text.format.DateFormat.format("dd/MM/yyyy", item.dueAt).toString()
                                : "Chưa có hạn trả";
                        String action = "awaiting_pickup".equalsIgnoreCase(item.status)
                                ? "Chạm để kiểm tra phiếu và xác nhận giao sách"
                                : "Chạm để xác nhận trả sách";
                        rowLabels.add(item.title + "\nTrạng thái: "
                                + ("awaiting_pickup".equalsIgnoreCase(item.status) ? "Đã duyệt, chờ nhận" : "Đang mượn")
                                + "\nHạn trả: " + due + "\n" + action);
                    }
                    adapter.notifyDataSetChanged();
                    emptyText.setVisibility(activeLoans.isEmpty() ? View.VISIBLE : View.GONE);
                    emptyText.setText("Độc giả hiện không có sách đang mượn.");
                })
                .addOnFailureListener(error -> {
                    statusText.setText("Không tải được phiếu mượn: " + safe(error.getMessage()));
                });
    }

    private void confirmPickup(BorrowItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Kiểm tra phiếu và giao sách")
                .setMessage("Độc giả: " + safe(scannedReaderUid)
                        + "\nSách: " + safe(item.title)
                        + "\nHạn trả: " + (item.dueAt > 0
                        ? android.text.format.DateFormat.format("dd/MM/yyyy", item.dueAt).toString()
                        : "Chưa cập nhật")
                        + "\n\nChỉ xác nhận sau khi đã đối chiếu thông tin phiếu và giao sách thực tế.")
                .setNegativeButton("Quay lại", null)
                .setPositiveButton("Xác nhận đã giao", (dialog, which) -> {
                    statusText.setText("Đang xác nhận giao sách...");
                    cloud.confirmBorrowPickup(item.id, adminUid)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Đã xác nhận giao sách. Phiếu chuyển sang ĐANG MƯỢN.", Toast.LENGTH_LONG).show();
                                statusText.setText("Giao sách thành công.");
                                if (scannedReaderUid != null) loadActiveLoans(scannedReaderUid);
                            })
                            .addOnFailureListener(error -> statusText.setText(
                                    "Không thể xác nhận giao sách: " + safe(error.getMessage())));
                }).show();
    }

    private void confirmReturn(BorrowItem item) {
        android.view.View form = getLayoutInflater().inflate(R.layout.dialog_return_inspection, null);
        TextView summary = form.findViewById(R.id.textReturnInspectionSummary);
        CheckBox damagedBox = form.findViewById(R.id.checkBookDamaged);
        EditText damageDescription = form.findViewById(R.id.editDamageDescription);
        EditText lateFeeInput = form.findViewById(R.id.editLateFee);
        EditText damageFeeInput = form.findViewById(R.id.editDamageFee);
        CheckBox feeCollectedBox = form.findViewById(R.id.checkFeeCollected);
        long now = System.currentTimeMillis();
        long overdueDays = item.dueAt > 0L && now > item.dueAt
                ? (now - item.dueAt + 86400000L - 1L) / 86400000L : 0L;
        String due = item.dueAt > 0L
                ? android.text.format.DateFormat.format("dd/MM/yyyy", item.dueAt).toString()
                : "Chưa có hạn trả";
        summary.setText("Sách: " + safe(item.title)
                + "\nHạn trả: " + due
                + "\nNgày trả thực tế: " + android.text.format.DateFormat.format("dd/MM/yyyy", now)
                + "\nSố ngày trả trễ: " + overdueDays
                + "\n\nKiểm tra tình trạng sách, nhập mức phí theo quy định thư viện và đánh dấu đã thu nếu độc giả thanh toán.");
        damagedBox.setOnCheckedChangeListener((button, checked) -> {
            damageDescription.setEnabled(checked);
            damageFeeInput.setEnabled(checked);
            if (!checked) {
                damageDescription.setText("");
                damageFeeInput.setText("0");
            }
        });
        damageDescription.setEnabled(false);
        damageFeeInput.setEnabled(false);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Kiểm tra sách trả")
                .setView(form)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận trả sách", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String lateText = lateFeeInput.getText().toString().trim();
            String damageText = damageFeeInput.getText().toString().trim();
            long lateFee;
            long damageFee;
            try {
                lateFee = lateText.isEmpty() ? 0L : Long.parseLong(lateText);
                damageFee = damageText.isEmpty() ? 0L : Long.parseLong(damageText);
            } catch (NumberFormatException ex) {
                Toast.makeText(this, "Phí phải là số nguyên VNĐ hợp lệ.", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean damaged = damagedBox.isChecked();
            if (lateFee < 0L || damageFee < 0L) {
                Toast.makeText(this, "Phí không được âm.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!damaged && damageFee > 0L) {
                Toast.makeText(this, "Hãy đánh dấu sách hư hại trước khi nhập phí hư hại.", Toast.LENGTH_SHORT).show();
                return;
            }
            long total = lateFee + damageFee;
            if (total > 0L && !feeCollectedBox.isChecked()) {
                new AlertDialog.Builder(this)
                        .setTitle("Phí chưa được thu")
                        .setMessage("Tổng phí là " + total + " VNĐ. Xác nhận trả sách mà chưa thu phí sẽ ghi công nợ chưa thanh toán.")
                        .setNegativeButton("Quay lại", null)
                        .setPositiveButton("Ghi nhận chưa thanh toán", (d, which) ->
                                saveReturn(item, damaged, damageDescription.getText().toString(),
                                        lateFee, damageFee, false, dialog))
                        .show();
                return;
            }
            saveReturn(item, damaged, damageDescription.getText().toString(),
                    lateFee, damageFee, feeCollectedBox.isChecked(), dialog);
        }));
        dialog.show();
    }

    private void saveReturn(BorrowItem item, boolean damaged, String damageDescription,
                            long lateFee, long damageFee, boolean feeCollected,
                            AlertDialog dialog) {
        statusText.setText("Đang ghi nhận trả sách, tình trạng và phí...");
        cloud.returnBorrowRecord(item.id, adminUid, damaged, damageDescription,
                        lateFee, damageFee, feeCollected)
                .addOnSuccessListener(unused -> {
                    dialog.dismiss();
                    long total = lateFee + damageFee;
                    Toast.makeText(this, "Đã ghi nhận trả sách. Tổng phí: " + total + " VNĐ.", Toast.LENGTH_LONG).show();
                    statusText.setText("Trả sách thành công. Tổng phí: " + total + " VNĐ.");
                    if (scannedReaderUid != null) loadActiveLoans(scannedReaderUid);
                })
                .addOnFailureListener(error -> statusText.setText(
                        "Không thể xác nhận trả sách: " + safe(error.getMessage())));
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Chưa cập nhật" : value.trim();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static class BorrowItem {
        final String id;
        final String title;
        final long dueAt;
        final String status;
        BorrowItem(String id, String title, long dueAt, String status) {
            this.id = id;
            this.title = title == null ? "Sách chưa có tên" : title;
            this.dueAt = dueAt;
            this.status = status == null ? "" : status;
        }
    }
}
