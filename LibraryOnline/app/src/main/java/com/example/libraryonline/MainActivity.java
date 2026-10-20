package com.example.libraryonline;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FirebaseCheck";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
