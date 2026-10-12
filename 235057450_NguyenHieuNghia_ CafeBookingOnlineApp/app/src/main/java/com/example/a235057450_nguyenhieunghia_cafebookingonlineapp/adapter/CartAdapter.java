package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.R;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.CartItem;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adapter for the cart screen. */
public class CartAdapter extends BaseAdapter {
    public interface Listener {
        void onChangeQuantity(CartItem item);
        void onRemove(CartItem item);
    }

    private final LayoutInflater inflater;
    private final Listener listener;
    private final List<CartItem> items = new ArrayList<>();

    public CartAdapter(Context context, Listener listener) {
        inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setItems(List<CartItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public CartItem getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return getItem(position).getMenuId(); }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) row = inflater.inflate(R.layout.item_cart, parent, false);

        CartItem item = getItem(position);
        TextView name = row.findViewById(R.id.textCartItemName);
        TextView detail = row.findViewById(R.id.textCartItemDetail);
        Button change = row.findViewById(R.id.buttonChangeQuantity);
        Button remove = row.findViewById(R.id.buttonRemoveCartItem);

        name.setText(item.getMenuName());
        NumberFormat money = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        detail.setText(money.format(item.getUnitPrice()) + " VND × "
                + item.getQuantity() + " = " + money.format(item.getSubtotal()) + " VND");

        change.setOnClickListener(v -> {
            if (listener != null) listener.onChangeQuantity(item);
        });
        remove.setOnClickListener(v -> {
            if (listener != null) listener.onRemove(item);
        });
        return row;
    }
}
