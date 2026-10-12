package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
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

        setContentView(R.layout.activity_menu_detail);
        TextView name = findViewById(R.id.detailName);
        TextView category = findViewById(R.id.detailCategory);
        TextView price = findViewById(R.id.detailPrice);
        TextView description = findViewById(R.id.detailDescription);
        TextView availability = findViewById(R.id.detailAvailability);
        Button add = findViewById(R.id.detailAddButton);
        Button back = findViewById(R.id.detailBackButton);
        back.setOnClickListener(v -> finish());
        if (item == null) {
            name.setText("Không tìm thấy món");
            category.setText("Món có thể đã bị xóa khỏi thực đơn.");
            price.setVisibility(android.view.View.GONE);
            description.setVisibility(android.view.View.GONE);
            availability.setVisibility(android.view.View.GONE);
            add.setEnabled(false);
        } else {
            name.setText(item.getMenuName());
            CategoryDAO categoryDAO = new CategoryDAO(this);
            List<Category> categories = categoryDAO.getAll();
            categoryDAO.close();
            String categoryName = "Chưa phân loại";
            for (Category c : categories) {
                if (c.getCategoryId() == item.getCategoryId()) { categoryName = c.getCategoryName(); break; }
            }
            category.setText("Danh mục: " + categoryName);
            price.setText(currency.format(item.getPrice()) + " đ");
            String desc = item.getDescription();
            description.setText(desc == null || desc.trim().isEmpty() ? "Chưa có mô tả cho món này." : desc);
            availability.setText(item.isAvailable() ? "Đang phục vụ" : "Tạm hết");
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
    }

}
