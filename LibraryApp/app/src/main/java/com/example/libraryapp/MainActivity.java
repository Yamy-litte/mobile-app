package com.example.libraryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

/** Welcome screen aligned with the first MyLibrary Figma screen. */
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button start = findViewById(R.id.btnStart);
        start.setOnClickListener(v -> startActivity(new Intent(this, RoleSelectionActivity.class)));
    }
}