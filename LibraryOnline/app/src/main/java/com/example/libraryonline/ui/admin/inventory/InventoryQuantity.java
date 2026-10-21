package com.example.libraryonline.ui.admin.inventory;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import com.example.libraryonline.R;
import com.example.libraryonline.data.inventory.StockIntake;

/** Shared XML quantity/shelf inputs; draft is kept even when validation fails. */
public final class InventoryQuantity {
    private final View view;
    private final EditText quantity, shelf;

    public InventoryQuantity(View view, Bundle draft, Runnable changed) {
        this.view = view;
        quantity = view.findViewById(R.id.intakeQuantity);
        shelf = view.findViewById(R.id.intakeShelf);
        if (draft != null) {
            quantity.setText(draft.getString("quantity", "1"));
            shelf.setText(draft.getString("shelf", ""));
        }
        view.findViewById(R.id.intakeMinus).setOnClickListener(v -> step(-1));
        view.findViewById(R.id.intakePlus).setOnClickListener(v -> step(1));
        TextWatcher watcher =
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        changed.run();
                    }

                    public void afterTextChanged(Editable e) {}
                };
        quantity.addTextChangedListener(watcher);
        shelf.addTextChangedListener(watcher);
    }

    private void step(int delta) {
        int value = count();
        quantity.setText(Integer.toString(Math.max(1, Math.min(5, value + delta))));
    }

    public int count() {
        try {
            return Integer.parseInt(quantity.getText().toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public StockIntake read() {
        return new StockIntake(count(), shelf.getText().toString());
    }

    public Bundle save() {
        Bundle b = new Bundle();
        b.putString("quantity", quantity.getText().toString());
        b.putString("shelf", shelf.getText().toString());
        return b;
    }

    public void setEnabled(boolean enabled) {
        for (int id :
                new int[] {
                    R.id.intakeQuantity, R.id.intakeShelf, R.id.intakeMinus, R.id.intakePlus
                }) view.findViewById(id).setEnabled(enabled);
    }
}
