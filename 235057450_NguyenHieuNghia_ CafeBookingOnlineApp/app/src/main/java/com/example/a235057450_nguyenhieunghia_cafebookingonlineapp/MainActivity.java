package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.adapter.MenuAdapter;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.cart.CartManager;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.MenuDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.DishReviewDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.UserDao;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Menu;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.DishReview;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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
        Button logout = findViewById(R.id.buttonLogout);
        logout.setOnClickListener(v -> {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
            cartManager.clear();
            startActivity(new android.content.Intent(this, AuthActivity.class));
            finish();
        });

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
        StringBuilder details = new StringBuilder("Tên món: ")
                .append(menu.getMenuName())
                .append("\nGiá: ").append(formatMoney(menu.getPrice())).append(" VND")
                .append("\nSố lượng còn: ").append(menu.getStockQuantity())
                .append("\nMã danh mục: ").append(menu.getCategoryId());

        DishReviewDao reviewDao = new DishReviewDao(getApplicationContext());
        List<DishReview> reviews;
        try {
            reviews = reviewDao.getRecentForMenu(menu.getMenuId());
        } finally {
            reviewDao.close();
        }
        details.append("\n\nĐánh giá món gần đây:");
        if (reviews.isEmpty()) {
            details.append("\nChưa có đánh giá.");
        } else {
            for (DishReview review : reviews) {
                details.append("\n").append(stars(review.getRating())).append("  ")
                        .append(displayName(review.getCustomerEmail())).append(": ")
                        .append(review.getComment());
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Chi tiết món")
                .setMessage(details.toString())
                .setPositiveButton("Đóng", null)
                .setNeutralButton("Thêm giỏ", (dialog, which) -> addToCart(menu))
                .setNegativeButton("Đánh giá món", (dialog, which) -> showDishReviewDialog(menu))
                .show();
    }

    @Override
    public void onAddToCart(Menu menu) {
        if (menu == null) return;
        addToCart(menu);
    }

    private void showDishReviewDialog(Menu menu) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null || !firebaseUser.isEmailVerified()
                || firebaseUser.getEmail() == null) {
            Toast.makeText(this, "Đăng nhập bằng email đã xác minh để đánh giá món.",
                    Toast.LENGTH_LONG).show();
            startActivity(new android.content.Intent(this, AuthActivity.class));
            return;
        }

        UserDao userDao = new UserDao(getApplicationContext());
        User user;
        try {
            user = userDao.getByEmail(firebaseUser.getEmail());
            if (user == null) {
                long id = userDao.saveProfile(firebaseUser.getEmail(), "Customer");
                user = id > 0 ? userDao.getById((int) id) : null;
            }
        } finally {
            userDao.close();
        }
        if (user == null || !"Customer".equals(user.getRole())) {
            Toast.makeText(this, "Chỉ tài khoản khách hàng mới được đánh giá món.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        final int reviewUserId = user.getUserId();

        DishReviewDao reviewDao = new DishReviewDao(getApplicationContext());
        DishReview existing;
        try {
            existing = reviewDao.getForMenu(menu.getMenuId(), reviewUserId);
        } finally {
            reviewDao.close();
        }

        android.widget.LinearLayout form = new android.widget.LinearLayout(this);
        form.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding / 2, padding, 0);

        TextView prompt = new TextView(this);
        prompt.setText("Đánh giá " + menu.getMenuName());
        prompt.setTextSize(15);
        form.addView(prompt);

        RatingBar ratingBar = new RatingBar(this);
        ratingBar.setNumStars(5);
        ratingBar.setStepSize(1f);
        ratingBar.setRating(existing == null ? 5f : existing.getRating());
        form.addView(ratingBar);

        EditText commentInput = new EditText(this);
        commentInput.setHint("Nhận xét món ăn (tối đa 1000 ký tự)");
        commentInput.setMinLines(3);
        commentInput.setGravity(android.view.Gravity.TOP);
        commentInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (existing != null) commentInput.setText(existing.getComment());
        form.addView(commentInput);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Đánh giá món" : "Sửa đánh giá món")
                .setView(form)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu đánh giá", (dialog, which) -> {
                    String comment = commentInput.getText().toString().trim();
                    if (comment.isEmpty() || comment.length() > 1000) {
                        Toast.makeText(this, "Nhận xét không được để trống và tối đa 1000 ký tự.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    DishReviewDao saveDao = new DishReviewDao(getApplicationContext());
                    boolean saved;
                    try {
                        saved = saveDao.saveForMenu(menu.getMenuId(), reviewUserId,
                                Math.round(ratingBar.getRating()), comment);
                    } finally {
                        saveDao.close();
                    }
                    Toast.makeText(this, saved ? "Đã lưu đánh giá món."
                                    : "Không thể lưu đánh giá món.",
                            Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private String stars(int rating) {
        StringBuilder value = new StringBuilder();
        for (int i = 1; i <= 5; i++) value.append(i <= rating ? "★" : "☆");
        return value.toString();
    }

    private String displayName(String email) {
        if (email == null || email.trim().isEmpty()) return "Khách hàng";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
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
