package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Button;
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
import java.text.SimpleDateFormat;
import java.util.Date;
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
        setContentView(R.layout.activity_checkout);
        summary = findViewById(R.id.checkoutSummary);
        Button place = findViewById(R.id.placeOrderButton);
        Button back = findViewById(R.id.backToCartButton);
        place.setOnClickListener(v -> placeOrder(place));
        back.setOnClickListener(v -> finish());
        showSummary();
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
        boolean success = false;
        long billId = -1;
        db.beginTransaction();
        try {
            ContentValues bill = new ContentValues();
            bill.put("UserId", user.getUserId());
            bill.put("DateOfBill", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
            bill.put("Status", "Awaiting Confirmation");
            bill.put("TotalAmount", total);
            billId = db.insertOrThrow("Bill", null, bill);

            double verifiedTotal = 0;
            for (Map.Entry<String, ?> entry : cart.getAll().entrySet()) {
                if (!entry.getKey().startsWith("item_")) continue;
                long menuId = Long.parseLong(entry.getKey().substring(5));
                int qty = Integer.parseInt(String.valueOf(entry.getValue()));
                if (qty <= 0) continue;
                // Read through the same SQLiteDatabase connection used by this transaction.
                // Opening another SQLiteOpenHelper here can contend for the database lock.
                String menuName;
                double price;
                int available;
                try (Cursor cursor = db.query(DatabaseHelper.TABLE_MENU,
                        new String[]{"MenuName", "Price", "IsAvailable"},
                        "MenuId = ?", new String[]{String.valueOf(menuId)},
                        null, null, null)) {
                    if (!cursor.moveToFirst()) {
                        throw new IllegalStateException("Không tìm thấy món trong thực đơn (ID " + menuId + ").");
                    }
                    menuName = cursor.getString(cursor.getColumnIndexOrThrow("MenuName"));
                    price = cursor.getDouble(cursor.getColumnIndexOrThrow("Price"));
                    available = cursor.getInt(cursor.getColumnIndexOrThrow("IsAvailable"));
                }
                if (available != 1) {
                    throw new IllegalStateException("Món \"" + menuName + "\" đã ngừng phục vụ. Vui lòng kiểm tra giỏ hàng.");
                }
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