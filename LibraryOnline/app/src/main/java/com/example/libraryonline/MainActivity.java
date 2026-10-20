package com.example.libraryonline;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.database.sqlite.SQLiteDatabase;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.ui.auth.LoginActivity;
import com.google.firebase.FirebaseApp;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "LibraryStartup";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep the Day 1 SQLite smoke test while moving into the Day 2 auth flow.
        DatabaseHelper databaseHelper = new DatabaseHelper(this);
        try (SQLiteDatabase db = databaseHelper.getWritableDatabase()) {
            Log.i(TAG, "SQLite opened: " + db.getPath());
        } catch (Exception e) {
            Log.e(TAG, "SQLite initialization failed", e);
        } finally {
            databaseHelper.close();
        }

        try {
            FirebaseApp.getInstance();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        } catch (IllegalStateException exception) {
            TextView status = new TextView(this);
            status.setText("LibraryOnline\nChưa khởi tạo được Firebase. Hãy kiểm tra google-services.json và cấu hình Firebase.");
            status.setTextSize(18);
            status.setPadding(32, 32, 32, 32);
            setContentView(status);
            Log.e(TAG, "Firebase initialization failed", exception);
        }
    }
}
