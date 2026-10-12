package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.adapter.MenuAdapter;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.cart.CartManager;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.MenuDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Menu;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements MenuAdapter.OnMenuActionListener {
    private MenuDao menuDao;
    private CartManager cartManager;
    private MenuAdapter menuAdapter;
    private EditText editSearchMenu;
    private ListView listMenu;
    private TextView textEmptyMenu;
    private TextView textCartSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        menuDao = new MenuDao(this);
        cartManager = CartManager.getInstance(this);
        editSearchMenu = findViewById(R.id.editSearchMenu);
        listMenu = findViewById(R.id.listMenu);
        textEmptyMenu = findViewById(R.id.textEmptyMenu);
        textCartSummary = findViewById(R.id.textCartSummary);
        Button openCart = findViewById(R.id.buttonOpenCart);
        openCart.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, CartActivity.class)));

        Button openReviews = findViewById(R.id.buttonReviews);
        openReviews.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, ReviewsActivity.class)));

        Button openOrderHistory = findViewById(R.id.buttonOrderHistory);
        openOrderHistory.setOnClickListener(v -> {
            if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
                startActivity(new android.content.Intent(this, AuthActivity.class));
            } else {
                startActivity(new android.content.Intent(this, OrderHistoryActivity.class));
            }
        });

        menuAdapter = new MenuAdapter(this, this);
        listMenu.setAdapter(menuAdapter);
        listMenu.setEmptyView(textEmptyMenu);

        loadMenus("");
        updateCartSummary();

        editSearchMenu.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadMenus(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Reload from SQLite whenever this screen becomes visible again.
        // Checkout/cancellation may have changed StockQuantity while this
        // Activity was in the background; otherwise ListView keeps stale Menu objects.
        if (menuDao != null && menuAdapter != null && editSearchMenu != null) {
            loadMenus(editSearchMenu.getText().toString());
        }

        if (cartManager != null && textCartSummary != null) {
            updateCartSummary();
        }
    }

    private void loadMenus(String keyword) {
        List<Menu> menus = keyword == null || keyword.trim().isEmpty()
                ? menuDao.getAvailableMenus()
                : menuDao.searchAvailableMenus(keyword);
        menuAdapter.setMenus(menus);
        textEmptyMenu.setVisibility(menus.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onViewDetails(Menu menu) {
        if (menu == null) return;
        String details = "Tên món: " + menu.getMenuName()
                + "\nGiá: " + formatMoney(menu.getPrice()) + " VND"
                + "\nSố lượng còn: " + menu.getStockQuantity()
                + "\nMã danh mục: " + menu.getCategoryId();
        new AlertDialog.Builder(this)
                .setTitle("Chi tiết món")
                .setMessage(details)
                .setPositiveButton("Đóng", null)
                .setNeutralButton("Thêm giỏ", (dialog, which) -> addToCart(menu))
                .show();
    }

    @Override
    public void onAddToCart(Menu menu) {
        if (menu == null) return;
        addToCart(menu);
    }

    private void addToCart(Menu menu) {
        if (cartManager.addItem(menu.getMenuId(), 1)) {
            updateCartSummary();
            Toast.makeText(this, "Đã thêm " + menu.getMenuName() + " vào giỏ hàng.",
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Không thể thêm món. Hãy kiểm tra tồn kho.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void updateCartSummary() {
        textCartSummary.setText("Giỏ hàng: " + cartManager.getItemCount()
                + " món | Tổng: " + formatMoney(cartManager.getTotalAmount()) + " VND");
    }

    private String formatMoney(double amount) {
        return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(amount);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (menuDao != null) menuDao.close();
        // CartManager is shared across screens; do not close it here.
    }
}
