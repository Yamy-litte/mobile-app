package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.MenuDAO;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.UserDAO;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.MenuItem;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.User;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

public class CheckoutActivity extends AppCompatActivity {
    private SharedPreferences cart;
    private TextView summary;
    private double total;
    private final NumberFormat money = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        cart = getSharedPreferences(CartActivity.PREFS, MODE_PRIVATE);
        money.setMaximumFractionDigits(0);
        buildScreen();
        showSummary();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(0xFFFAF7F2);
        TextView title = new TextView(this);
        title.setText("Xác nhận đơn hàng");
        title.setTextSize(25);
        title.setTextColor(0xFF744B30);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        root.addView(title, params());
        summary = new TextView(this);
        summary.setTextSize(16);
        LinearLayout.LayoutParams sp = params(); sp.topMargin = 20;
        root.addView(summary, sp);
        Button place = new Button(this);
        place.setText("Xác nhận đặt hàng");
        LinearLayout.LayoutParams pp = params(); pp.topMargin = 18;
        root.addView(place, pp);
        place.setOnClickListener(v -> placeOrder(place));
        Button back = new Button(this);
        back.setText("Quay lại giỏ hàng");
        root.addView(back, params());
        back.setOnClickListener(v -> finish());
        setContentView(root);
    }

    private void showSummary() {
        StringBuilder text = new StringBuilder();
        total = 0;
        MenuDAO dao = new MenuDAO(this);
        for (Map.Entry<String, ?> entry : cart.getAll().entrySet()) {
            if (!entry.getKey().startsWith("item_")) continue;
            try {
                long id = Long.parseLong(entry.getKey().substring(5));
                int qty = Integer.parseInt(String.valueOf(entry.getValue()));
                MenuItem item = dao.getById(id);
                if (item == null || qty <= 0) continue;
                double line = item.getPrice() * qty;
                total += line;
                text.append(item.getMenuName()).append(" x ").append(qty)
                    .append(" = ").append(money.format(line)).append(" đ\n");
            } catch (NumberFormatException ignored) { }
        }
        dao.close();
        if (text.length() == 0) text.append("Giỏ hàng đang trống.");
        text.append("\nTổng cộng: ").append(money.format(total)).append(" đ");
        summary.setText(text.toString());
    }

    private void placeOrder(Button button) {
        if (total <= 0 || cart.getAll().isEmpty()) {
            Toast.makeText(this, "Không thể đặt đơn hàng trống.", Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null || firebaseUser.getEmail() == null) {
            Toast.makeText(this, "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        UserDAO userDAO = new UserDAO(this);
        User user = userDAO.getByEmail(firebaseUser.getEmail());
        userDAO.close();
        if (user == null) {
            Toast.makeText(this, "Không tìm thấy hồ sơ khách hàng trong SQLite. Hãy đăng ký lại tài khoản.", Toast.LENGTH_LONG).show();
            return;
        }

        button.setEnabled(false);
        DatabaseHelper helper = new DatabaseHelper(this);
        SQLiteDatabase db = helper.getWritableDatabase();
        MenuDAO menuDAO = new MenuDAO(this);
        boolean success = false;
        long billId = -1;
        db.beginTransaction();
        try {
            ContentValues bill = new ContentValues();
            bill.put("UserId", user.getUserId());
            bill.put("DateOfBill", LocalDateTime.now().toString());
            bill.put("Status", "Awaiting Confirmation");
            bill.put("TotalAmount", total);
            billId = db.insertOrThrow("Bill", null, bill);

            double verifiedTotal = 0;
            for (Map.Entry<String, ?> entry : cart.getAll().entrySet()) {
                if (!entry.getKey().startsWith("item_")) continue;
                long menuId = Long.parseLong(entry.getKey().substring(5));
                int qty = Integer.parseInt(String.valueOf(entry.getValue()));
                if (qty <= 0) continue;
                MenuItem item = menuDAO.getById(menuId);
                if (item == null || !item.isAvailable()) {
                    throw new IllegalStateException("Một món đã ngừng phục vụ. Vui lòng kiểm tra giỏ hàng.");
                }
                double price = item.getPrice();
                verifiedTotal += price * qty;
                ContentValues detail = new ContentValues();
                detail.put("BillId", billId);
                detail.put("MenuId", menuId);
                detail.put("Quantity", qty);
                detail.put("UnitPrice", price);
                db.insertOrThrow("BillDetail", null, detail);
            }
            if (verifiedTotal <= 0) throw new IllegalStateException("Đơn hàng không có món hợp lệ.");
            ContentValues correctedBill = new ContentValues();
            correctedBill.put("TotalAmount", verifiedTotal);
            db.update("Bill", correctedBill, "BillId = ?", new String[]{String.valueOf(billId)});
            db.setTransactionSuccessful();
            success = true;
        } catch (Exception ex) {
            Toast.makeText(this, "Đặt hàng thất bại: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            db.endTransaction();
            menuDAO.close();
            helper.close();
            button.setEnabled(true);
        }
        if (success) {
            cart.edit().clear().apply();
            Toast.makeText(this, "Đặt hàng thành công! Mã đơn: " + billId, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(-1, -2);
    }
}