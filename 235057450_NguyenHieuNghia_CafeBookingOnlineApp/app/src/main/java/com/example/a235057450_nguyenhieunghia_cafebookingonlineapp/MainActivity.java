package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.data.DatabaseHelper;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.data.dao.CategoryDAO;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.data.dao.MenuDAO;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Category;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.MenuItem;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final int COLOR_BACKGROUND = Color.rgb(250, 247, 242);
    private static final int COLOR_PRIMARY = Color.rgb(116, 75, 48);
    private static final int COLOR_TEXT = Color.rgb(48, 39, 32);

    private DatabaseHelper databaseHelper;
    private CategoryDAO categoryDAO;
    private MenuDAO menuDAO;
    private LinearLayout categoryContainer;
    private LinearLayout menuContainer;
    private TextView cartButton;
    private final Map<Long, CartEntry> cart = new LinkedHashMap<>();
    private long selectedCategoryId = -1;
    private List<Category> categories = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        databaseHelper = new DatabaseHelper(this);
        categoryDAO = new CategoryDAO(databaseHelper);
        menuDAO = new MenuDAO(databaseHelper);

        categoryContainer = findViewById(R.id.categoryContainer);
        menuContainer = findViewById(R.id.menuContainer);
        cartButton = findViewById(R.id.cartButton);
        cartButton.setOnClickListener(v -> showCart());

        loadCategories();
        renderMenu();
        updateCartButton();
    }

    private void loadCategories() {
        categories = categoryDAO.getAllCategories();
        categoryContainer.removeAllViews();
        addCategoryButton("All", -1);
        for (Category category : categories) {
            addCategoryButton(category.getName(), category.getId());
        }
    }

    private void addCategoryButton(String name, long categoryId) {
        Button button = new Button(this);
        button.setText(name);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setMinHeight(dp(42));
        button.setPadding(dp(14), 0, dp(14), 0);
        boolean selected = selectedCategoryId == categoryId;
        button.setTextColor(selected ? Color.WHITE : COLOR_PRIMARY);
        button.setBackground(makeRounded(selected ? COLOR_PRIMARY : Color.WHITE, 22));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(42));
        params.setMargins(0, 0, dp(8), 0);
        categoryContainer.addView(button, params);
        button.setOnClickListener(v -> {
            selectedCategoryId = categoryId;
            loadCategories();
            renderMenu();
        });
    }

    private void renderMenu() {
        menuContainer.removeAllViews();
        List<MenuItem> items = selectedCategoryId == -1
                ? menuDAO.getAllMenuItems()
                : menuDAO.getMenuByCategory(selectedCategoryId);

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No drinks in this category yet.");
            empty.setTextColor(COLOR_TEXT);
            empty.setPadding(dp(16), dp(24), dp(16), dp(24));
            menuContainer.addView(empty);
            return;
        }

        for (MenuItem item : items) {
            menuContainer.addView(createMenuCard(item));
        }
    }

    private View createMenuCard(MenuItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(makeRounded(Color.WHITE, 18));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardParams);

        TextView icon = new TextView(this);
        icon.setText(item.getImageUrl() == null || item.getImageUrl().trim().isEmpty() ? "☕" : "🥤");
        icon.setTextSize(30);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(makeRounded(COLOR_BACKGROUND, 14));
        card.addView(icon, new LinearLayout.LayoutParams(dp(68), dp(68)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, dp(8), 0);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

        TextView name = new TextView(this);
        name.setText(item.getName());
        name.setTextColor(COLOR_TEXT);
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(name);

        TextView description = new TextView(this);
        description.setText(item.getDescription() == null ? "" : item.getDescription());
        description.setTextColor(Color.DKGRAY);
        description.setTextSize(12);
        description.setMaxLines(2);
        info.addView(description);

        TextView price = new TextView(this);
        price.setText(formatPrice(item.getPrice()));
        price.setTextColor(COLOR_PRIMARY);
        price.setTextSize(14);
        price.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        priceParams.topMargin = dp(5);
        info.addView(price, priceParams);
        card.addView(info, infoParams);

        TextView addButton = new TextView(this);
        addButton.setText(item.isAvailable() ? "+" : "×");
        addButton.setTextSize(24);
        addButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addButton.setTextColor(Color.WHITE);
        addButton.setGravity(Gravity.CENTER);
        addButton.setBackground(makeRounded(item.isAvailable() ? COLOR_PRIMARY : Color.GRAY, 30));
        card.addView(addButton, new LinearLayout.LayoutParams(dp(42), dp(42)));

        View.OnClickListener detailsClick = v -> showMenuDetails(item);
        card.setOnClickListener(detailsClick);
        addButton.setOnClickListener(v -> {
            if (item.isAvailable()) {
                addToCart(item);
            } else {
                Toast.makeText(this, "This item is currently unavailable.", Toast.LENGTH_SHORT).show();
            }
        });
        return card;
    }

    private void showMenuDetails(MenuItem item) {
        String description = item.getDescription() == null ? "" : item.getDescription();
        String availability = item.isAvailable() ? "\n\nAvailable to order" : "\n\nCurrently unavailable";
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(item.getName())
                .setMessage(description + "\n\nPrice: " + formatPrice(item.getPrice()) + availability)
                .setNegativeButton("Close", (dialog, which) -> dialog.dismiss());
        if (item.isAvailable()) {
            builder.setPositiveButton("Add to cart", (dialog, which) -> addToCart(item));
        }
        builder.show();
    }

    private void addToCart(MenuItem item) {
        if (!item.isAvailable()) {
            Toast.makeText(this, "This item is currently unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        CartEntry entry = cart.get(item.getId());
        if (entry == null) {
            cart.put(item.getId(), new CartEntry(item.getId(), item.getName(), item.getPrice()));
        } else {
            entry.quantity++;
        }
        updateCartButton();
        Toast.makeText(this, item.getName() + " added to cart", Toast.LENGTH_SHORT).show();
    }

    private void showCart() {
        if (cart.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Your cart")
                    .setMessage("Your cart is empty. Add a drink from the menu first.")
                    .setPositiveButton("Continue shopping", null)
                    .show();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        double total = 0;

        for (CartEntry entry : cart.values()) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView itemText = new TextView(this);
            itemText.setText(entry.name + "\n" + entry.quantity + " × " + formatPrice(entry.unitPrice));
            itemText.setTextColor(COLOR_TEXT);
            itemText.setTextSize(14);
            row.addView(itemText, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button minus = new Button(this);
            minus.setText("−");
            minus.setAllCaps(false);
            minus.setMinWidth(dp(40));
            minus.setOnClickListener(v -> {
                if (entry.quantity > 1) {
                    entry.quantity--;
                } else {
                    cart.remove(entry.menuId);
                }
                updateCartButton();
                showCart();
            });
            row.addView(minus, new LinearLayout.LayoutParams(dp(48), dp(44)));

            Button plus = new Button(this);
            plus.setText("+");
            plus.setAllCaps(false);
            plus.setMinWidth(dp(40));
            plus.setOnClickListener(v -> {
                entry.quantity++;
                updateCartButton();
                showCart();
            });
            row.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(44)));
            content.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(Color.LTGRAY);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
            dividerParams.setMargins(0, dp(6), 0, dp(6));
            content.addView(divider, dividerParams);
            total += entry.unitPrice * entry.quantity;
        }

        TextView totalText = new TextView(this);
        totalText.setText("Total: " + formatPrice(total));
        totalText.setTextColor(COLOR_PRIMARY);
        totalText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        totalText.setTextSize(18);
        totalText.setPadding(0, dp(10), 0, dp(4));
        content.addView(totalText);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle("Your cart (" + getCartQuantity() + " items)")
                .setView(scroll)
                .setNegativeButton("Continue shopping", null)
                .setPositiveButton("Clear cart", (dialog, which) -> {
                    cart.clear();
                    updateCartButton();
                    Toast.makeText(this, "Cart cleared", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void updateCartButton() {
        cartButton.setText("🛒 Cart (" + getCartQuantity() + ")  •  " + formatPrice(getCartTotal()));
    }

    private int getCartQuantity() {
        int quantity = 0;
        for (CartEntry entry : cart.values()) quantity += entry.quantity;
        return quantity;
    }

    private double getCartTotal() {
        double total = 0;
        for (CartEntry entry : cart.values()) total += entry.quantity * entry.unitPrice;
        return total;
    }

    private String formatPrice(double price) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        formatter.setMaximumFractionDigits(0);
        return formatter.format(price) + " ₫";
    }

    private GradientDrawable makeRounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) databaseHelper.close();
    }

    private static class CartEntry {
        final long menuId;
        final String name;
        final double unitPrice;
        int quantity = 1;

        CartEntry(long menuId, String name, double unitPrice) {
            this.menuId = menuId;
            this.name = name;
            this.unitPrice = unitPrice;
        }
    }
}
