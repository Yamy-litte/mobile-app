package com.example.a235057450_nguyenhieunghia_cafebookingonlineapp;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.a235057450_nguyenhieunghia_cafebookingonlineapp.model.DatabaseHelper;

public class MainActivity extends AppCompatActivity {
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView databaseStatus = findViewById(R.id.databaseStatus);
        databaseHelper = new DatabaseHelper(getApplicationContext());

        try {
            // Opening the database creates the schema and inserts demo rows on first launch.
            SQLiteDatabase db = databaseHelper.getWritableDatabase();
            int users = countRows(db, DatabaseHelper.TABLE_USER);
            int categories = countRows(db, DatabaseHelper.TABLE_CATEGORY);
            int menuItems = countRows(db, DatabaseHelper.TABLE_MENU);
            int bills = countRows(db, DatabaseHelper.TABLE_BILL);
            int billDetails = countRows(db, DatabaseHelper.TABLE_BILL_DETAIL);

            databaseStatus.setText(
                    "SQLite: Kết nối thành công\n\n" +
                    "User: " + users + " bản ghi\n" +
                    "Category: " + categories + " bản ghi\n" +
                    "Menu: " + menuItems + " bản ghi\n" +
                    "Bill: " + bills + " bản ghi\n" +
                    "BillDetail: " + billDetails + " bản ghi\n\n" +
                    "Nếu mỗi bảng có 3 bản ghi, dữ liệu mẫu đã được tạo.");
        } catch (Exception e) {
            databaseStatus.setText("SQLite: Có lỗi\n" + e.getMessage());
        }
    }

    private int countRows(SQLiteDatabase db, String tableName) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + tableName, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    @Override
    protected void onDestroy() {
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        super.onDestroy();
    }
}
