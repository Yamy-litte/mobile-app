package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.R;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.Menu;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** ListView adapter for available cafe menu items. */
public class MenuAdapter extends BaseAdapter {
    public interface OnMenuActionListener {
        void onViewDetails(Menu menu);
        void onAddToCart(Menu menu);
    }

    private final LayoutInflater inflater;
    private final OnMenuActionListener listener;
    private final List<Menu> menus = new ArrayList<>();

    public MenuAdapter(Context context, OnMenuActionListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setMenus(List<Menu> newMenus) {
        menus.clear();
        if (newMenus != null) {
            menus.addAll(newMenus);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return menus.size();
    }

    @Override
    public Menu getItem(int position) {
        return menus.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getMenuId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = inflater.inflate(R.layout.item_menu, parent, false);
        }

        Menu menu = getItem(position);
        TextView name = row.findViewById(R.id.textMenuName);
        TextView price = row.findViewById(R.id.textMenuPrice);
        TextView stock = row.findViewById(R.id.textMenuStock);
        Button details = row.findViewById(R.id.buttonDetails);
        Button add = row.findViewById(R.id.buttonAddCart);

        name.setText(menu.getMenuName());
        price.setText(NumberFormat.getNumberInstance(new Locale("vi", "VN"))
                .format(menu.getPrice()) + " VND");
        stock.setText("Còn " + menu.getStockQuantity() + " phần");
        details.setOnClickListener(v -> {
            if (listener != null) listener.onViewDetails(menu);
        });
        add.setOnClickListener(v -> {
            if (listener != null) listener.onAddToCart(menu);
        });
        return row;
    }
}
