package com.example.libraryonline.utils;

import android.text.InputType;
import android.view.MotionEvent;
import android.widget.EditText;

import com.example.libraryonline.R;

/** Adds a tappable eye icon to show or hide a password without losing the cursor position. */
public final class PasswordVisibilityToggle {
    private PasswordVisibilityToggle() {}

    public static void attach(EditText input) {
        if (input == null) return;
        input.setCompoundDrawablePadding(8);
        setIcon(input, false);
        input.setOnTouchListener((view, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) return false;
            android.graphics.drawable.Drawable eye = input.getCompoundDrawablesRelative()[2];
            if (eye == null) return false;
            int iconStart = input.getWidth() - input.getPaddingEnd() - eye.getBounds().width();
            if (event.getX() < iconStart) return false;

            int selection = input.getSelectionStart();
            boolean currentlyVisible = (input.getInputType()
                    & android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                    == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
            if (currentlyVisible) {
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            } else {
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            }
            setIcon(input, !currentlyVisible);
            if (selection >= 0 && selection <= input.length()) input.setSelection(selection);
            input.performClick();
            return true;
        });
    }

    private static void setIcon(EditText input, boolean visible) {
        android.graphics.drawable.Drawable[] drawables = input.getCompoundDrawablesRelative();
        android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(
                input.getContext(), visible ? R.drawable.ic_visibility_on : R.drawable.ic_visibility_off);
        input.setCompoundDrawablesRelativeWithIntrinsicBounds(drawables[0], drawables[1], icon, drawables[3]);
    }
}