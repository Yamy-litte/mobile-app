package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.BillDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.BillDetailDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.UserDao;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);
        textOrderInfo = findViewById(R.id.textOrderInfo);
        layoutOrderLines = findViewById(R.id.layoutOrderLines);
        Button back = findViewById(R.id.buttonOrderDetailBack);
        back.setOnClickListener(v -> finish());

        int billId = getIntent().getIntExtra(EXTRA_BILL_ID, -1);
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
                textOrderInfo.setText("Đơn hàng #" + resultBill.getBillId()
                        + "\nNgày đặt: " + resultBill.getDateOfBill()
                        + "\nTrạng thái: " + resultBill.getStatus()
                        + "\nTổng tiền: " + money.format(resultBill.getTotalAmount()) + " VND");
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}