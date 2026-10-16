package com.example.libraryapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Welcome screen; restores the last role only when Remember me is enabled. */
public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "library_session";
    private static final String KEY_REMEMBER = "remember_login";
    private static final String KEY_ROLE = "account_role";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean remember = preferences.getBoolean(KEY_REMEMBER, false);
        String role = preferences.getString(KEY_ROLE, "reader");

        if (remember && user != null) {
            if ("admin".equals(role)) {
                if ("hDZ5s2jS35ah0J98pVKAM2QQNxx2".equals(user.getUid())
                        && "admin@gmail.com".equalsIgnoreCase(user.getEmail() == null ? "" : user.getEmail())) {
                    openHome(AdminHomeActivity.class, role);
                    return;
                }
                preferences.edit().putBoolean(KEY_REMEMBER, false).remove(KEY_ROLE).apply();
                FirebaseAuth.getInstance().signOut();
            } else {
                openHome(ReaderHomeActivity.class, "reader");
                return;
            }
        }

        setContentView(R.layout.activity_main);
        Button start = findViewById(R.id.btnStart);
        start.setOnClickListener(v -> startActivity(new Intent(this, RoleSelectionActivity.class)));
    }

    private void openHome(Class<?> destination, String role) {
        Intent intent = new Intent(this, destination);
        intent.putExtra("account_role", role);
        startActivity(intent);
        finish();
    }
}