package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
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
        setContentView(R.layout.activity_menu);
        categoryContainer = findViewById(R.id.categoryContainer);
        menuContainer = findViewById(R.id.menuContainer);
        searchInput = findViewById(R.id.searchInput);
        Button cartButton = findViewById(R.id.cartButton);
        cartButton.setOnClickListener(v -> startActivity(new Intent(this, CartActivity.class)));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                keyword = s.toString().trim().toLowerCase(Locale.ROOT);
                renderMenu();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        loadData();
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
        button.setTextColor(getColor(selectedCategoryId == categoryId ? R.color.white : R.color.coffee_primary));
        button.setBackgroundResource(selectedCategoryId == categoryId
                ? R.drawable.bg_primary_button : R.drawable.bg_input);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = 8;
        categoryContainer.addView(button, p);
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
        LayoutInflater inflater = LayoutInflater.from(this);
        for (MenuItem item : allItems) {
            if (selectedCategoryId != -1L && item.getCategoryId() != selectedCategoryId) continue;
            String name = item.getMenuName() == null ? "" : item.getMenuName();
            String description = item.getDescription() == null ? "" : item.getDescription();
            if (!keyword.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(keyword)
                    && !description.toLowerCase(Locale.ROOT).contains(keyword)) continue;

            View card = inflater.inflate(R.layout.item_menu_card, menuContainer, false);
            TextView itemName = card.findViewById(R.id.itemName);
            TextView desc = card.findViewById(R.id.itemDescription);
            TextView price = card.findViewById(R.id.itemPrice);
            Button detailsButton = card.findViewById(R.id.detailsButton);
            Button addToCart = card.findViewById(R.id.addToCartButton);
            itemName.setText(name);
            desc.setText(description);
            desc.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
            price.setText(currency.format(item.getPrice()) + " đ");
            detailsButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, MenuDetailActivity.class);
                intent.putExtra(MenuDetailActivity.EXTRA_MENU_ID, item.getMenuId());
                startActivity(intent);
            });
            addToCart.setOnClickListener(v -> {
                SharedPreferences cart = getSharedPreferences(CartActivity.PREFS, MODE_PRIVATE);
                String key = "item_" + item.getMenuId();
                int current = cart.getInt(key, 0);
                if (current >= 99) {
                    Toast.makeText(this, "Số lượng tối đa là 99.", Toast.LENGTH_SHORT).show();
                    return;
                }
                cart.edit().putInt(key, current + 1).apply();
                Toast.makeText(this, "Đã thêm " + name + " vào giỏ hàng.", Toast.LENGTH_SHORT).show();
            });
            menuContainer.addView(card);
            shown++;
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("Không tìm thấy món phù hợp.");
            empty.setTextColor(getColor(R.color.coffee_muted));
            empty.setPadding(8, 22, 8, 22);
            menuContainer.addView(empty);
        }
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}