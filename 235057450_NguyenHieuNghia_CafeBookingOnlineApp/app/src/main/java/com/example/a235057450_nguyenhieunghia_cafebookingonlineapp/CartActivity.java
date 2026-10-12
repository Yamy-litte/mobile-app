package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.MenuDAO;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.MenuItem;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

public class CartActivity extends AppCompatActivity {
    public static final String PREFS = "cafe_cart";
    private SharedPreferences cart;
    private LinearLayout itemsBox;
    private TextView totalText;
    private final NumberFormat money = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        cart = getSharedPreferences(PREFS, MODE_PRIVATE);
        money.setMaximumFractionDigits(0);
        setContentView(R.layout.activity_cart);
        itemsBox = findViewById(R.id.cartItemsBox);
        totalText = findViewById(R.id.cartTotalText);
        Button checkout = findViewById(R.id.checkoutButton);
        Button back = findViewById(R.id.continueShoppingButton);
        checkout.setOnClickListener(v -> {
            if (cart.getAll().isEmpty()) {
                Toast.makeText(this, "Giỏ hàng đang trống.", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, CheckoutActivity.class));
        });
        back.setOnClickListener(v -> finish());
        renderCart();
    }

    @Override protected void onResume() {
        super.onResume();
        if (cart != null && itemsBox != null) renderCart();
    }

    private void renderCart() {
        itemsBox.removeAllViews();
        double total = 0;
        MenuDAO dao = new MenuDAO(this);
        Map<String, ?> values = cart.getAll();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!entry.getKey().startsWith("item_")) continue;
            long id;
            int qty;
            try {
                id = Long.parseLong(entry.getKey().substring(5));
                qty = Integer.parseInt(String.valueOf(entry.getValue()));
            } catch (NumberFormatException ex) { continue; }
            if (qty <= 0) continue;
            MenuItem item = dao.getById(id);
            if (item == null) continue;
            double subtotal = item.getPrice() * qty;
            total += subtotal;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(12, 12, 12, 12);
            row.setBackgroundColor(0xFFFFFFFF);
            LinearLayout.LayoutParams rp = params();
            rp.topMargin = 10;
            itemsBox.addView(row, rp);
            TextView label = new TextView(this);
            label.setText(item.getMenuName() + "\n" + money.format(item.getPrice()) + " đ x " + qty
                    + " = " + money.format(subtotal) + " đ");
            label.setTextSize(16);
            row.addView(label, params());
            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(controls, params());
            Button minus = new Button(this); minus.setText("−");
            Button plus = new Button(this); plus.setText("+");
            Button remove = new Button(this); remove.setText("Xóa");
            controls.addView(minus, new LinearLayout.LayoutParams(0, -2, 1));
            controls.addView(plus, new LinearLayout.LayoutParams(0, -2, 1));
            controls.addView(remove, new LinearLayout.LayoutParams(0, -2, 1));
            minus.setOnClickListener(v -> changeQty(id, qty - 1));
            plus.setOnClickListener(v -> changeQty(id, qty + 1));
            remove.setOnClickListener(v -> { cart.edit().remove(entry.getKey()).apply(); renderCart(); });
        }
        dao.close();
        if (itemsBox.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Giỏ hàng đang trống. Hãy quay lại thực đơn để thêm món.");
            empty.setPadding(4, 20, 4, 20);
            itemsBox.addView(empty, params());
        }
        totalText.setText("Tổng cộng: " + money.format(total) + " đ");
    }

    private void changeQty(long id, int qty) {
        SharedPreferences.Editor edit = cart.edit();
        if (qty <= 0) edit.remove("item_" + id);
        else edit.putInt("item_" + id, qty);
        edit.apply();
        renderCart();
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(-1, -2);
    }
}