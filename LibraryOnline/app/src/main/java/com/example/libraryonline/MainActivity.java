package com.example.libraryonline;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import android.database.sqlite.SQLiteDatabase;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FirebaseCheck";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DatabaseHelper databaseHelper = new DatabaseHelper(this);

        try (SQLiteDatabase db = databaseHelper.getWritableDatabase()) {
            Log.i("SQLiteCheck",
                    "SQLite mở thành công: " + db.getPath());

            try (android.database.Cursor cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master " +
                            "WHERE type = 'table' ORDER BY name",
                    null)) {

                while (cursor.moveToNext()) {
                    Log.i("SQLiteCheck",
                            "Table: " + cursor.getString(0));
                }
            }
        } catch (Exception e) {
            Log.e("SQLiteCheck", "SQLite khởi tạo thất bại", e);
        } finally {
            databaseHelper.close();
        }
        TextView statusText = findViewById(R.id.firebaseStatus);

        try {
            FirebaseApp firebaseApp = FirebaseApp.getInstance();
            Log.i(TAG, "Firebase initialized successfully: " + firebaseApp.getName());
            statusText.setText("LibraryOnline\nFirebase đã khởi tạo thành công.");
        } catch (IllegalStateException exception) {
            Log.e(TAG, "Firebase initialization failed", exception);
            statusText.setText("LibraryOnline\nChưa khởi tạo được Firebase. Kiểm tra cấu hình.");
        }
    }
}
