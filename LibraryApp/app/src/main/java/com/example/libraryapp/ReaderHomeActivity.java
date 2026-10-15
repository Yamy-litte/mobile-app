package com.example.libraryapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryapp.auth.FirebaseAuthManager;

/** Temporary authenticated landing page; the Reader home UI will follow the remaining Figma screens. */
public class ReaderHomeActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!new FirebaseAuthManager().isLoggedIn()) { startActivity(new Intent(this, MainActivity.class)); finish(); return; }
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER);
        root.setPadding(32,32,32,32); root.setBackgroundColor(Color.rgb(255,249,240));
        TextView title=new TextView(this); title.setText("MyLibrary"); title.setTextSize(30); title.setTextColor(Color.rgb(28,96,88)); title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,120));
        TextView info=new TextView(this); info.setText("Đăng nhập thành công.\nGiao diện trang chủ sẽ được hoàn thiện theo Figma."); info.setTextSize(17); info.setTextColor(Color.rgb(36,57,53)); info.setGravity(Gravity.CENTER);
        root.addView(info,new LinearLayout.LayoutParams(-1,120));
        Button logout=new Button(this); logout.setText("Đăng xuất"); logout.setAllCaps(false); root.addView(logout);
        logout.setOnClickListener(v->{new FirebaseAuthManager().logout();startActivity(new Intent(this,MainActivity.class));finish();});
        setContentView(root);
    }
}