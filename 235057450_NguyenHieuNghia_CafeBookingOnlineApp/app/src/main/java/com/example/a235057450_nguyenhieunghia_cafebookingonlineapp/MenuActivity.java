package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.CategoryDAO;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.dao.MenuDAO;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Category;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.MenuItem;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MenuActivity extends AppCompatActivity {
    private LinearLayout categoryContainer, menuContainer;
    private EditText searchInput;
    private final List<Category> categories = new ArrayList<>();
    private final List<MenuItem> allItems = new ArrayList<>();
    private long selectedCategoryId = -1L;
    private String keyword = "";
    private final NumberFormat currency = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        currency.setMaximumFractionDigits(0);
        buildScreen();
        loadData();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 24, 20, 24);
        root.setBackgroundColor(0xFFFAF7F2);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Thực đơn");
        title.setTextSize(26);
        title.setTextColor(0xFF744B30);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        root.addView(title, params());

        Button cartButton = new Button(this);
        cartButton.setText("Mở giỏ hàng");
        LinearLayout.LayoutParams cartParams = params();
        cartParams.topMargin = 8;
        root.addView(cartButton, cartParams);
        cartButton.setOnClickListener(v -> startActivity(new Intent(this, CartActivity.class)));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Tìm món theo tên hoặc mô tả");
        LinearLayout.LayoutParams searchParams = params();
        searchParams.topMargin = 12;
        root.addView(searchInput, searchParams);

        TextView categoryTitle = new TextView(this);
        categoryTitle.setText("Danh mục");
        categoryTitle.setTextSize(18);
        categoryTitle.setTypeface(null, 1);
        LinearLayout.LayoutParams ct = params();
        ct.topMargin = 12;
        root.addView(categoryTitle, ct);

        categoryContainer = new LinearLayout(this);
        categoryContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(categoryContainer, params());

        TextView menuTitle = new TextView(this);
        menuTitle.setText("Các món đang phục vụ");
        menuTitle.setTextSize(18);
        menuTitle.setTypeface(null, 1);
        LinearLayout.LayoutParams mt = params();
        mt.topMargin = 18;
        root.addView(menuTitle, mt);

        menuContainer = new LinearLayout(this);
        menuContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(menuContainer, params());

        Button back = new Button(this);
        back.setText("Quay lại");
        LinearLayout.LayoutParams bp = params();
        bp.topMargin = 16;
        root.addView(back, bp);
        back.setOnClickListener(v -> finish());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                keyword = s.toString().trim().toLowerCase(Locale.ROOT);
                renderMenu();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        setContentView(scroll);
    }

    private void loadData() {
        CategoryDAO categoryDAO = new CategoryDAO(this);
        MenuDAO menuDAO = new MenuDAO(this);
        categories.clear();
        categories.addAll(categoryDAO.getAll());
        allItems.clear();
        allItems.addAll(menuDAO.getAvailable());
        categoryDAO.close();
        menuDAO.close();
        renderCategories();
        renderMenu();
    }

    private void renderCategories() {
        categoryContainer.removeAllViews();
        addCategoryButton("Tất cả", -1L);
        for (Category category : categories) {
            addCategoryButton(category.getCategoryName(), category.getCategoryId());
        }
    }

    private void addCategoryButton(String label, long categoryId) {
        Button button = new Button(this);
        button.setText((selectedCategoryId == categoryId ? "✓ " : "") + label);
        button.setAllCaps(false);
        categoryContainer.addView(button, params());
        button.setOnClickListener(v -> {
            selectedCategoryId = categoryId;
            renderCategories();
            renderMenu();
        });
    }

    private void renderMenu() {
        if (menuContainer == null) return;
        menuContainer.removeAllViews();
        int shown = 0;
        for (MenuItem item : allItems) {
            if (selectedCategoryId != -1L && item.getCategoryId() != selectedCategoryId) continue;
            String name = item.getMenuName() == null ? "" : item.getMenuName();
            String description = item.getDescription() == null ? "" : item.getDescription();
            if (!keyword.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(keyword)
                    && !description.toLowerCase(Locale.ROOT).contains(keyword)) continue;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(14, 12, 14, 12);
            card.setBackgroundColor(0xFFFFFFFF);
            LinearLayout.LayoutParams cp = params();
            cp.topMargin = 10;
            menuContainer.addView(card, cp);

            TextView itemName = new TextView(this);
            itemName.setText(name);
            itemName.setTextSize(17);
            itemName.setTextColor(0xFF744B30);
            itemName.setTypeface(null, 1);
            card.addView(itemName, params());

            if (!description.isEmpty()) {
                TextView desc = new TextView(this);
                desc.setText(description);
                desc.setTextSize(14);
                card.addView(desc, params());
            }

            TextView price = new TextView(this);
            price.setText(currency.format(item.getPrice()) + " đ");
            price.setTextSize(16);
            price.setTypeface(null, 1);
            LinearLayout.LayoutParams pp = params();
            pp.topMargin = 6;
            card.addView(price, pp);

            Button addToCart = new Button(this);
            addToCart.setText("Thêm vào giỏ");
            LinearLayout.LayoutParams addParams = params();
            addParams.topMargin = 8;
            card.addView(addToCart, addParams);
            addToCart.setOnClickListener(v -> {
                SharedPreferences cart = getSharedPreferences(CartActivity.PREFS, MODE_PRIVATE);
                String key = "item_" + item.getMenuId();
                int current = cart.getInt(key, 0);
                cart.edit().putInt(key, current + 1).apply();
                Toast.makeText(this, "Đã thêm " + name + " vào giỏ hàng.", Toast.LENGTH_SHORT).show();
            });
            shown++;
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("Không tìm thấy món phù hợp.");
            empty.setPadding(4, 16, 4, 16);
            menuContainer.addView(empty, params());
        }
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}