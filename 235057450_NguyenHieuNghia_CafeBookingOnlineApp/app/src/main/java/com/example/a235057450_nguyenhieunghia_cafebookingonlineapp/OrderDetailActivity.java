package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.UserDAO;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.database.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.User;
import java.text.NumberFormat;
import java.util.Locale;

public class OrderDetailActivity extends AppCompatActivity {
    private long billId;
    private User customer;
    private LinearLayout itemsContainer;
    private TextView orderInfoText;
    private TextView orderTotalText;
    private final NumberFormat money = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_order_detail);
        money.setMaximumFractionDigits(0);

        Button back = findViewById(R.id.orderDetailBackButton);
        back.setOnClickListener(v -> finish());
        orderInfoText = findViewById(R.id.orderDetailInfoText);
        orderTotalText = findViewById(R.id.orderDetailTotalText);
        itemsContainer = findViewById(R.id.orderDetailItemsContainer);

        billId = getIntent().getLongExtra("BILL_ID", -1);
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (billId < 1 || firebaseUser == null || firebaseUser.getEmail() == null) {
            Toast.makeText(this, "Không thể mở chi tiết đơn hàng.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        UserDAO userDAO = new UserDAO(this);
        customer = userDAO.getByEmail(firebaseUser.getEmail());
        userDAO.close();
        if (customer == null) {
            Toast.makeText(this, "Không tìm thấy hồ sơ khách hàng.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        loadOrderDetails();
    }

    private void loadOrderDetails() {
        DatabaseHelper helper = new DatabaseHelper(this);
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor bill = db.query("Bill", new String[]{"BillId", "UserId", "DateOfBill", "Status", "TotalAmount"},
                "BillId = ? AND UserId = ?",
                new String[]{String.valueOf(billId), String.valueOf(customer.getUserId())},
                null, null, null);
        try {
            if (!bill.moveToFirst()) {
                Toast.makeText(this, "Không tìm thấy đơn hàng.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            String date = bill.getString(bill.getColumnIndexOrThrow("DateOfBill"));
            String status = bill.getString(bill.getColumnIndexOrThrow("Status"));
            double total = bill.getDouble(bill.getColumnIndexOrThrow("TotalAmount"));
            orderInfoText.setText("Đơn #" + billId + "\nNgày đặt: " + date + "\nTrạng thái: " + status);
            orderTotalText.setText("Tổng tiền: " + money.format(total) + " đ");
        } finally {
            bill.close();
        }

        Cursor items = db.rawQuery(
                "SELECT m.MenuName, d.Quantity, d.UnitPrice " +
                "FROM BillDetail d JOIN Menu m ON m.MenuId = d.MenuId " +
                "JOIN Bill b ON b.BillId = d.BillId " +
                "WHERE d.BillId = ? AND b.UserId = ? ORDER BY d.BillDetailId",
                new String[]{String.valueOf(billId), String.valueOf(customer.getUserId())});
        try {
            if (!items.moveToFirst()) {
                TextView empty = new TextView(this);
                empty.setText("Không có món hàng trong đơn này.");
                empty.setTextColor(getResources().getColor(R.color.coffee_muted));
                itemsContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            } else {
                do {
                    String name = items.getString(0);
                    int quantity = items.getInt(1);
                    double unitPrice = items.getDouble(2);
                    TextView row = new TextView(this);
                    row.setText(name + "  × " + quantity + "\n" +
                            money.format(unitPrice) + " đ / món     Thành tiền: " +
                            money.format(unitPrice * quantity) + " đ");
                    row.setTextColor(getResources().getColor(R.color.coffee_text));
                    row.setTextSize(15);
                    row.setPadding(0, 12, 0, 12);
                    itemsContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
                    View divider = new View(this);
                    divider.setBackgroundColor(getResources().getColor(R.color.coffee_border));
                    itemsContainer.addView(divider, new LinearLayout.LayoutParams(-1, 1));
                } while (items.moveToNext());
            }
        } finally {
            items.close();
            helper.close();
        }
    }
}