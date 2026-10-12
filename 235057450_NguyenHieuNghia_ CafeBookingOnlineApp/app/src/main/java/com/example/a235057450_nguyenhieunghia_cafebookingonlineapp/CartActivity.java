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
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.BillDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.UserDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.BillDetail;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.CartItem;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CartActivity extends AppCompatActivity implements CartAdapter.Listener {
    private CartManager cartManager;
    private CartAdapter adapter;
    private TextView textCartTotal;
    private TextView textCartEmpty;
    private ListView listCartItems;
    private Button checkoutButton;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        cartManager = CartManager.getInstance(this);
        listCartItems = findViewById(R.id.listCartItems);
        textCartTotal = findViewById(R.id.textCartTotal);
        textCartEmpty = findViewById(R.id.textCartEmpty);
        checkoutButton = findViewById(R.id.buttonCheckout);
        Button continueShopping = findViewById(R.id.buttonContinueShopping);

        adapter = new CartAdapter(this, this);
        listCartItems.setAdapter(adapter);
        listCartItems.setEmptyView(textCartEmpty);

        checkoutButton.setOnClickListener(v -> checkout());

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

    private void checkout() {
        if (cartManager.isEmpty()) {
            Toast.makeText(this, "Giỏ hàng đang trống.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null || !firebaseUser.isEmailVerified()
                || firebaseUser.getEmail() == null) {
            Toast.makeText(this,
                    "Vui lòng đăng nhập bằng tài khoản đã xác minh email trước khi đặt hàng.",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AuthActivity.class));
            return;
        }

        List<CartItem> cartSnapshot = cartManager.getItems();
        List<BillDetail> billDetails = new ArrayList<>();
        for (CartItem cartItem : cartSnapshot) {
            BillDetail detail = new BillDetail();
            detail.setMenuId(cartItem.getMenuId());
            detail.setQuantity(cartItem.getQuantity());
            billDetails.add(detail);
        }

        checkoutButton.setEnabled(false);
        checkoutButton.setText("Đang tạo hóa đơn...");

        String email = firebaseUser.getEmail();
        databaseExecutor.execute(() -> {
            UserDao userDao = new UserDao(getApplicationContext());
            BillDao billDao = new BillDao(getApplicationContext());
            long billId = -1;
            double orderTotal = calculateSnapshotTotal(cartSnapshot);
            try {
                User localUser = userDao.getByEmail(email);
                long localUserId = localUser == null
                        ? userDao.saveProfile(email, "Customer")
                        : localUser.getUserId();
                if (localUserId > 0) {
                    billId = billDao.checkout((int) localUserId, billDetails);
                    if (billId > 0) {
                        com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Bill savedBill =
                                billDao.getBillById((int) billId, (int) localUserId);
                        if (savedBill != null) orderTotal = savedBill.getTotalAmount();
                    }
                }
            } finally {
                userDao.close();
                billDao.close();
            }

            final long resultBillId = billId;
            final double resultTotal = orderTotal;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                checkoutButton.setEnabled(true);
                checkoutButton.setText("Checkout");

                if (resultBillId > 0) {
                    cartManager.clear();
                    refreshCart();
                    new AlertDialog.Builder(this)
                            .setTitle("Đặt hàng thành công")
                            .setMessage("Mã hóa đơn: #" + resultBillId
                                    + "\nTrạng thái: awaiting confirmation"
                                    + "\nTổng tiền: " + formatMoney(resultTotal) + " VND"
                                    + "\n\nĐơn hàng đang chờ nhà hàng xác nhận.")
                            .setPositiveButton("Tiếp tục mua hàng", (dialog, which) -> {
                                Intent intent = new Intent(this, MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(intent);
                                finish();
                            })
                            .setCancelable(false)
                            .show();
                } else {
                    Toast.makeText(this,
                            "Không thể tạo hóa đơn. Có thể món đã hết hàng hoặc tồn kho đã thay đổi. Giỏ hàng vẫn được giữ lại.",
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private double calculateSnapshotTotal(List<CartItem> items) {
        double total = 0;
        for (CartItem item : items) total += item.getSubtotal();
        return total;
    }

    private void refreshCart() {
        adapter.setItems(cartManager.getItems());
        textCartTotal.setText("Tổng tiền: " + formatMoney(cartManager.getTotalAmount()) + " VND");
        textCartEmpty.setText("Giỏ hàng đang trống.");
        if (checkoutButton != null) checkoutButton.setEnabled(!cartManager.isEmpty());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        databaseExecutor.shutdown();
    }
}
