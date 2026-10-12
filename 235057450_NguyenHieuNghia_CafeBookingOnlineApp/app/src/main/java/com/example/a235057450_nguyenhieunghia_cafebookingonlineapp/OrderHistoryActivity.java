package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.UserDAO;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.User;
import java.text.NumberFormat;
import java.util.Locale;

public class OrderHistoryActivity extends AppCompatActivity {
    private LinearLayout list;
    private User customer;
    private final NumberFormat money = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        money.setMaximumFractionDigits(0);
        FirebaseUser fb = FirebaseAuth.getInstance().getCurrentUser();
        if (fb == null || fb.getEmail() == null) { finish(); return; }
        UserDAO dao = new UserDAO(this);
        customer = dao.getByEmail(fb.getEmail());
        dao.close();
        if (customer == null) {
            Toast.makeText(this, "Không tìm thấy hồ sơ khách hàng.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_order_history);
        list = findViewById(R.id.orderList);
        Button back = findViewById(R.id.orderHistoryBackButton);
        back.setOnClickListener(v -> finish());
    }

    @Override protected void onResume() {
        super.onResume();
        if (list != null && customer != null) loadOrders();
    }

    private void loadOrders() {
        list.removeAllViews();
        DatabaseHelper helper = new DatabaseHelper(this);
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor orders = db.query("Bill", null, "UserId = ?",
                new String[]{String.valueOf(customer.getUserId())}, null, null,
                "DateOfBill DESC, BillId DESC");
        try {
            if (!orders.moveToFirst()) {
                TextView empty = new TextView(this);
                empty.setText("Bạn chưa có đơn hàng nào.");
                empty.setPadding(4, 20, 4, 20);
                list.addView(empty, params());
                return;
            }
            do {
                long id = orders.getLong(orders.getColumnIndexOrThrow("BillId"));
                String date = orders.getString(orders.getColumnIndexOrThrow("DateOfBill"));
                String status = orders.getString(orders.getColumnIndexOrThrow("Status"));
                double total = orders.getDouble(orders.getColumnIndexOrThrow("TotalAmount"));
                View card = LayoutInflater.from(this).inflate(R.layout.item_order_card, list, false);
                TextView info = card.findViewById(R.id.orderInfoText);
                info.setText("Đơn #" + id + "\nNgày đặt: " + date + "\nTrạng thái: " + status
                        + "\nTổng tiền: " + money.format(total) + " đ");
                Button viewDetails = card.findViewById(R.id.viewOrderDetailsButton);\n                viewDetails.setOnClickListener(v -> {\n                    android.content.Intent intent = new android.content.Intent(this, OrderDetailActivity.class);\n                    intent.putExtra("BILL_ID", id);\n                    startActivity(intent);\n                });\n                Button viewDetails = card.findViewById(R.id.viewOrderDetailsButton);
                viewDetails.setOnClickListener(v -> {
                    android.content.Intent intent = new android.content.Intent(this, OrderDetailActivity.class);
                    intent.putExtra("BILL_ID", id);
                    startActivity(intent);
                });
                list.addView(card);

                Cursor details = db.rawQuery(
                    "SELECT m.MenuName, d.Quantity, d.UnitPrice FROM BillDetail d " +
                    "JOIN Menu m ON m.MenuId = d.MenuId WHERE d.BillId = ? ORDER BY d.BillDetailId",
                    new String[]{String.valueOf(id)});
                StringBuilder detailText = new StringBuilder("Các món:\n");
                try {
                    while (details.moveToNext()) {
                        detailText.append("• ").append(details.getString(0))
                                .append(" x ").append(details.getInt(1))
                                .append(" = ").append(money.format(details.getDouble(2) * details.getInt(1)))
                                .append(" đ\n");
                    }
                } finally { details.close(); }
                TextView detail = card.findViewById(R.id.orderDetailsText);
                detail.setText(detailText.toString());

                Button cancel = card.findViewById(R.id.cancelOrderButton);
                if ("Awaiting Confirmation".equals(status)) {
                    cancel.setVisibility(View.VISIBLE);
                    cancel.setOnClickListener(v -> cancelOrder(id));
                }
            } while (orders.moveToNext());
        } finally {
            orders.close();
            helper.close();
        }
    }

    private void cancelOrder(long billId) {
        DatabaseHelper helper = new DatabaseHelper(this);
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("Status", "Cancelled");
        int changed = db.update("Bill", values, "BillId = ? AND UserId = ? AND Status = ?",
                new String[]{String.valueOf(billId), String.valueOf(customer.getUserId()), "Awaiting Confirmation"});
        helper.close();
        Toast.makeText(this, changed == 1 ? "Đã hủy đơn hàng." : "Đơn đã được xử lý, không thể hủy.", Toast.LENGTH_LONG).show();
        loadOrders();
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(-1, -2);
    }
}