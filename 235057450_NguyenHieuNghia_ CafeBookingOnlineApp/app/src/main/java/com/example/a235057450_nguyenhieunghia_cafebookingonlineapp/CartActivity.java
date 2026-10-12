package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.adapter.CartAdapter;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.cart.CartManager;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.CartItem;

import java.text.NumberFormat;
import java.util.Locale;

public class CartActivity extends AppCompatActivity implements CartAdapter.Listener {
    private CartManager cartManager;
    private CartAdapter adapter;
    private TextView textCartTotal;
    private TextView textCartEmpty;
    private ListView listCartItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        cartManager = CartManager.getInstance(this);
        listCartItems = findViewById(R.id.listCartItems);
        textCartTotal = findViewById(R.id.textCartTotal);
        textCartEmpty = findViewById(R.id.textCartEmpty);
        Button checkout = findViewById(R.id.buttonCheckout);
        Button continueShopping = findViewById(R.id.buttonContinueShopping);

        adapter = new CartAdapter(this, this);
        listCartItems.setAdapter(adapter);
        listCartItems.setEmptyView(textCartEmpty);

        checkout.setOnClickListener(v -> {
            if (cartManager.isEmpty()) {
                Toast.makeText(this, "Giỏ hàng đang trống.", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this,
                    "Checkout sẽ được kết nối sau khi hoàn thành đăng nhập Firebase.",
                    Toast.LENGTH_LONG).show();
        });

        continueShopping.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        refreshCart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cartManager != null && adapter != null) refreshCart();
    }

    private void refreshCart() {
        adapter.setItems(cartManager.getItems());
        textCartTotal.setText("Tổng tiền: " + formatMoney(cartManager.getTotalAmount()) + " VND");
        textCartEmpty.setText("Giỏ hàng đang trống.");
    }

    @Override
    public void onChangeQuantity(CartItem item) {
        EditText quantityInput = new EditText(this);
        quantityInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        quantityInput.setSingleLine(true);
        quantityInput.setText(String.valueOf(item.getQuantity()));
        quantityInput.setSelection(quantityInput.getText().length());
        quantityInput.setHint("Nhập số lượng");

        new AlertDialog.Builder(this)
                .setTitle("Cập nhật số lượng")
                .setMessage("Không được vượt quá tồn kho hiện tại.")
                .setView(quantityInput)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String value = quantityInput.getText().toString().trim();
                    try {
                        int quantity = Integer.parseInt(value);
                        if (cartManager.updateQuantity(item.getMenuId(), quantity)) {
                            refreshCart();
                            Toast.makeText(this, "Đã cập nhật số lượng.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this,
                                    "Số lượng không hợp lệ hoặc vượt quá tồn kho.",
                                    Toast.LENGTH_LONG).show();
                        }
                    } catch (NumberFormatException ex) {
                        Toast.makeText(this, "Vui lòng nhập số nguyên hợp lệ.",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    @Override
    public void onRemove(CartItem item) {
        if (cartManager.removeItem(item.getMenuId())) {
            refreshCart();
            Toast.makeText(this, "Đã xóa món khỏi giỏ hàng.", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatMoney(double amount) {
        return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(amount);
    }
}
