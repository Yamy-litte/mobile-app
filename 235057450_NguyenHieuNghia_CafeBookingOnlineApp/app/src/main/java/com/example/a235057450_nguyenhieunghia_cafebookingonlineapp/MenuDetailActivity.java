package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
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
import java.util.List;
import java.util.Locale;

public class MenuDetailActivity extends AppCompatActivity {
    public static final String EXTRA_MENU_ID = "menu_id";
    private final NumberFormat currency = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        currency.setMaximumFractionDigits(0);
        long menuId = getIntent().getLongExtra(EXTRA_MENU_ID, -1L);
        MenuDAO dao = new MenuDAO(this);
        MenuItem item = dao.getById(menuId);
        dao.close();

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 24, 22, 24);
        root.setBackgroundColor(0xFFFAF7F2);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Chi tiết món");
        title.setTextSize(25);
        title.setTextColor(0xFF744B30);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        root.addView(title, params());

        if (item == null) {
            TextView error = new TextView(this);
            error.setText("Không tìm thấy món ăn. Món có thể đã bị xóa.");
            error.setTextSize(16);
            error.setPadding(0, 24, 0, 24);
            root.addView(error, params());
        } else {
            TextView name = new TextView(this);
            name.setText(item.getMenuName());
            name.setTextSize(23);
            name.setTextColor(0xFF744B30);
            name.setTypeface(null, 1);
            LinearLayout.LayoutParams np = params(); np.topMargin = 20;
            root.addView(name, np);

            CategoryDAO categoryDAO = new CategoryDAO(this);
            List<Category> categories = categoryDAO.getAll();
            categoryDAO.close();
            String categoryName = "Chưa phân loại";
            for (Category category : categories) {
                if (category.getCategoryId() == item.getCategoryId()) {
                    categoryName = category.getCategoryName();
                    break;
                }
            }

            TextView category = new TextView(this);
            category.setText("Danh mục: " + categoryName);
            category.setTextSize(15);
            root.addView(category, params());

            TextView price = new TextView(this);
            price.setText("Giá: " + currency.format(item.getPrice()) + " đ");
            price.setTextSize(20);
            price.setTextColor(0xFF8B4513);
            price.setTypeface(null, 1);
            LinearLayout.LayoutParams pp = params(); pp.topMargin = 12;
            root.addView(price, pp);

            TextView description = new TextView(this);
            String desc = item.getDescription();
            description.setText(desc == null || desc.trim().isEmpty()
                    ? "Chưa có mô tả cho món này." : desc);
            description.setTextSize(16);
            LinearLayout.LayoutParams dp = params(); dp.topMargin = 12;
            root.addView(description, dp);

            TextView availability = new TextView(this);
            availability.setText(item.isAvailable() ? "Trạng thái: Đang phục vụ" : "Trạng thái: Tạm hết");
            availability.setTextSize(15);
            LinearLayout.LayoutParams ap = params(); ap.topMargin = 12;
            root.addView(availability, ap);

            Button add = new Button(this);
            add.setText("Thêm vào giỏ");
            LinearLayout.LayoutParams addParams = params(); addParams.topMargin = 20;
            root.addView(add, addParams);
            add.setEnabled(item.isAvailable());
            add.setOnClickListener(v -> {
                SharedPreferences cart = getSharedPreferences(CartActivity.PREFS, MODE_PRIVATE);
                String key = "item_" + item.getMenuId();
                int current = cart.getInt(key, 0);
                if (current >= 99) {
                    Toast.makeText(this, "Mỗi món chỉ được thêm tối đa 99 phần.", Toast.LENGTH_LONG).show();
                    return;
                }
                cart.edit().putInt(key, current + 1).apply();
                Toast.makeText(this, "Đã thêm " + item.getMenuName() + " vào giỏ hàng.", Toast.LENGTH_SHORT).show();
            });
        }

        Button back = new Button(this);
        back.setText("Quay lại thực đơn");
        LinearLayout.LayoutParams bp = params(); bp.topMargin = 10;
        root.addView(back, bp);
        back.setOnClickListener(v -> finish());
        setContentView(scroll);
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
