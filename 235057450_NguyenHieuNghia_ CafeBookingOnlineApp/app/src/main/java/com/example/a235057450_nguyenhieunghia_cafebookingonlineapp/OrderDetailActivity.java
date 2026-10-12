package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.BillDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.BillDetailDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.UserDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.ReviewDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Review;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Bill;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.OrderLineItem;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OrderDetailActivity extends AppCompatActivity {
    public static final String EXTRA_BILL_ID = "bill_id";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView textOrderInfo;
    private LinearLayout layoutOrderLines;
    private Button cancelOrderButton;
    private Button reviewOrderButton;
    private int currentBillId;
    private int currentUserId;
    private String currentStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);
        textOrderInfo = findViewById(R.id.textOrderInfo);
        layoutOrderLines = findViewById(R.id.layoutOrderLines);
        Button back = findViewById(R.id.buttonOrderDetailBack);
        back.setOnClickListener(v -> finish());
        cancelOrderButton = findViewById(R.id.buttonCancelOrder);
        cancelOrderButton.setVisibility(View.GONE);
        cancelOrderButton.setOnClickListener(v -> confirmCancelOrder());
        reviewOrderButton = findViewById(R.id.buttonReviewOrder);
        reviewOrderButton.setVisibility(View.GONE);
        reviewOrderButton.setOnClickListener(v -> showReviewDialog());

        int billId = getIntent().getIntExtra(EXTRA_BILL_ID, -1);
        currentBillId = billId;
        if (billId <= 0) {
            Toast.makeText(this, "Mã đơn hàng không hợp lệ.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loadOrder(billId);
    }

    private void loadOrder(int billId) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null || !firebaseUser.isEmailVerified()
                || firebaseUser.getEmail() == null) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        textOrderInfo.setText("Đang tải chi tiết đơn hàng...");
        executor.execute(() -> {
            Bill bill = null;
            List<OrderLineItem> lines = java.util.Collections.emptyList();
            String error = null;
            UserDao userDao = new UserDao(getApplicationContext());
            BillDao billDao = new BillDao(getApplicationContext());
            BillDetailDao detailDao = new BillDetailDao(getApplicationContext());
            try {
                User user = userDao.getByEmail(firebaseUser.getEmail());
                if (user == null || !"Customer".equals(user.getRole())) {
                    error = "Không tìm thấy hồ sơ khách hàng.";
                } else {
                    currentUserId = user.getUserId();
                    bill = billDao.getBillById(billId, user.getUserId());
                    if (bill == null) {
                        error = "Không tìm thấy đơn hàng hoặc bạn không có quyền xem đơn này.";
                    } else {
                        lines = detailDao.getOrderLineItemsForCustomerBill(billId, user.getUserId());
                    }
                }
            } catch (RuntimeException ex) {
                error = "Không thể tải chi tiết đơn hàng.";
            } finally {
                userDao.close();
                billDao.close();
                detailDao.close();
            }

            Bill resultBill = bill;
            List<OrderLineItem> resultLines = lines;
            String resultError = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (resultError != null) {
                    textOrderInfo.setText(resultError);
                    return;
                }

                NumberFormat money = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
                currentStatus = resultBill.getStatus();
                textOrderInfo.setText("Đơn hàng #" + resultBill.getBillId()
                        + "\nNgày đặt: " + resultBill.getDateOfBill()
                        + "\nTrạng thái: " + resultBill.getStatus()
                        + "\nTổng tiền: " + money.format(resultBill.getTotalAmount()) + " VND");
                cancelOrderButton.setVisibility(
                        "awaiting confirmation".equalsIgnoreCase(currentStatus)
                                ? View.VISIBLE : View.GONE);
                reviewOrderButton.setVisibility(
                        "cancelled".equalsIgnoreCase(currentStatus) ? View.GONE : View.VISIBLE);
                layoutOrderLines.removeAllViews();
                for (OrderLineItem line : resultLines) {
                    TextView row = new TextView(this);
                    row.setText(line.getMenuName() + "  x" + line.getQuantity()
                            + "\nĐơn giá: " + money.format(line.getUnitPrice()) + " VND"
                            + "   |   Thành tiền: " + money.format(line.getSubtotal()) + " VND");
                    row.setTextSize(15);
                    row.setTextColor(0xFF444444);
                    row.setPadding(12, 16, 12, 16);
                    layoutOrderLines.addView(row);
                    View divider = new View(this);
                    divider.setBackgroundColor(0xFFD8D0C8);
                    layoutOrderLines.addView(divider, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1));
                }
                if (resultLines.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText("Đơn hàng chưa có món chi tiết.");
                    empty.setPadding(12, 16, 12, 16);
                    layoutOrderLines.addView(empty);
                }
            });
        });
    }

    private void showReviewDialog() {
        if (currentBillId <= 0 || currentUserId <= 0) {
            Toast.makeText(this, "Chưa tải xong thông tin đơn hàng.", Toast.LENGTH_SHORT).show();
            return;
        }

        ReviewDao dao = new ReviewDao(getApplicationContext());
        Review existing;
        try {
            existing = dao.getForBill(currentBillId, currentUserId);
        } finally {
            dao.close();
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding / 2, padding, 0);

        TextView prompt = new TextView(this);
        prompt.setText("Bạn đánh giá trải nghiệm đơn hàng này thế nào?");
        prompt.setTextSize(15);
        form.addView(prompt);

        RatingBar ratingBar = new RatingBar(this);
        ratingBar.setNumStars(5);
        ratingBar.setStepSize(1f);
        ratingBar.setRating(existing == null ? 5f : existing.getRating());
        form.addView(ratingBar);

        EditText commentInput = new EditText(this);
        commentInput.setHint("Viết nhận xét (tối đa 1000 ký tự)");
        commentInput.setMinLines(3);
        commentInput.setGravity(android.view.Gravity.TOP);
        commentInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (existing != null) commentInput.setText(existing.getComment());
        form.addView(commentInput);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Đánh giá đơn hàng #" + currentBillId
                        : "Sửa đánh giá đơn hàng #" + currentBillId)
                .setView(form)
                .setNegativeButton("Để sau", null)
                .setPositiveButton("Lưu đánh giá", (dialog, which) -> {
                    String comment = commentInput.getText().toString().trim();
                    if (comment.isEmpty() || comment.length() > 1000) {
                        Toast.makeText(this, "Nhận xét không được để trống và tối đa 1000 ký tự.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    ReviewDao saveDao = new ReviewDao(getApplicationContext());
                    boolean saved;
                    try {
                        saved = saveDao.saveForBill(currentBillId, currentUserId,
                                Math.round(ratingBar.getRating()), comment);
                    } finally {
                        saveDao.close();
                    }
                    if (saved) {
                        reviewOrderButton.setText("Sửa đánh giá");
                        Toast.makeText(this, "Đã lưu đánh giá.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Không thể lưu đánh giá cho đơn hàng này.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void confirmCancelOrder() {
        if (!"awaiting confirmation".equalsIgnoreCase(currentStatus)) {
            Toast.makeText(this, "Đơn hàng này không thể hủy.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Hủy đơn hàng")
                .setMessage("Bạn có chắc muốn hủy đơn hàng #" + currentBillId + "?")
                .setNegativeButton("Không", null)
                .setPositiveButton("Hủy đơn", (dialog, which) -> cancelOrder())
                .show();
    }

    private void cancelOrder() {
        cancelOrderButton.setEnabled(false);
        executor.execute(() -> {
            BillDao billDao = new BillDao(getApplicationContext());
            String result;
            try {
                result = billDao.cancelOrder(currentBillId, currentUserId);
            } finally {
                billDao.close();
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                cancelOrderButton.setEnabled(true);
                if ("CANCELLED".equals(result)) {
                    Toast.makeText(this, "Đã hủy đơn hàng. Tồn kho đã được hoàn lại.",
                            Toast.LENGTH_LONG).show();
                    finish();
                } else if ("NOT_CANCELLABLE".equals(result)) {
                    Toast.makeText(this,
                            "Chỉ đơn đang chờ xác nhận mới được hủy.",
                            Toast.LENGTH_LONG).show();
                    loadOrder(currentBillId);
                } else if ("NOT_FOUND".equals(result)) {
                    Toast.makeText(this, "Không tìm thấy đơn hàng của bạn.",
                            Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    Toast.makeText(this, "Hủy đơn thất bại. Vui lòng thử lại.",
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}