package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.adapters;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.R;
import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.MenuItem;

import java.text.NumberFormat;

/**
 * Binds a MenuItem model to the reusable menu-card layout.
 * MenuActivity owns filtering and click actions; this class owns card binding.
 */
public final class MenuAdapter {
    private MenuAdapter() { }

    public static void bind(Context context, View card, MenuItem item,
                            NumberFormat currency,
                            View.OnClickListener onDetails,
                            View.OnClickListener onAddToCart) {
        TextView itemName = card.findViewById(R.id.itemName);
        TextView description = card.findViewById(R.id.itemDescription);
        TextView price = card.findViewById(R.id.itemPrice);
        Button detailsButton = card.findViewById(R.id.detailsButton);
        Button addToCartButton = card.findViewById(R.id.addToCartButton);

        itemName.setText(item.getMenuName() == null ? "" : item.getMenuName());
        String itemDescription = item.getDescription() == null ? "" : item.getDescription();
        description.setText(itemDescription);
        description.setVisibility(itemDescription.isEmpty() ? View.GONE : View.VISIBLE);
        price.setText(currency.format(item.getPrice()) + " đ");
        detailsButton.setOnClickListener(onDetails);
        addToCartButton.setOnClickListener(onAddToCart);
    }
}
