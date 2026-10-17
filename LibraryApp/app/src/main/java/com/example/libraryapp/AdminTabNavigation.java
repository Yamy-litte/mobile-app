package com.example.libraryapp;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;
import android.view.View;

/** Navigation for the five Admin tabs shown in the project Figma. */
public final class AdminTabNavigation {
    private AdminTabNavigation() {}

    public static void bind(Activity activity) {
        bindMessage(activity, R.id.adminTabHome, "Trang chủ", true);
        bindBooks(activity);
        bindMessage(activity, R.id.adminTabScan, "Quét mã", false);
        bindMessage(activity, R.id.adminTabLoans, "Phiếu mượn", true);
        bindMessage(activity, R.id.adminTabAccount, "Tài khoản", false);
    }

    private static void bindBooks(Activity activity) {\n        View tab = activity.findViewById(R.id.adminTabBooks);\n        if (tab != null) tab.setOnClickListener(v -> activity.startActivity(new Intent(activity, AdminBookInventoryActivity.class)));\n    }\n\n    private static void bindMessage(Activity activity, int id, String label, boolean currentScreen) {
        View tab = activity.findViewById(id);
        if (tab == null) return;
        tab.setOnClickListener(v -> {
            if (currentScreen) {
                if ("Trang chủ".equals(label)) {
                    Toast.makeText(activity, "Trang chủ Admin đang được hoàn thiện.", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            Toast.makeText(activity, "Màn hình " + label + " sẽ được kết nối ở bước tiếp theo.", Toast.LENGTH_SHORT).show();
        });
    }
}
