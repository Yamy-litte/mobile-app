package com.example.libraryonline.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;
import com.example.libraryonline.utils.PasswordVisibilityToggle;
import com.example.libraryonline.utils.InputValidator;
import com.google.firebase.auth.FirebaseAuth;

public class AdminLoginActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private com.example.libraryonline.data.session.SessionGateway sessions;
    private boolean busy, foreground;
    private long generation;
    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private TextView backLink;
    private android.widget.CheckBox rememberEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        auth = FirebaseAuth.getInstance();
        sessions = createSessionGateway();
        emailInput = findViewById(R.id.editAdminEmail);
        passwordInput = findViewById(R.id.editAdminPassword);
        PasswordVisibilityToggle.attach(passwordInput);
        loginButton = findViewById(R.id.buttonAdminLogin);
        backLink = findViewById(R.id.textBackToReaderLogin);
        rememberEmail=findViewById(R.id.checkAdminLogin);
        String saved=getSharedPreferences("mylibrary_login",MODE_PRIVATE).getString("admin_email","");
        emailInput.setText(saved);rememberEmail.setChecked(!saved.isEmpty());

        loginButton.setOnClickListener(v -> loginAsAdmin());
        backLink.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void loginAsAdmin() {
        if (busy) return;
        if(sessions.currentUid()!=null) {setLoading(true);resolveProfile();return;}
        String email = InputValidator.normalizeEmail(emailInput.getText().toString());
        String password = passwordInput.getText().toString();

        if (!InputValidator.isValidEmail(email)) {
            emailInput.setError("Email không đúng định dạng");
            emailInput.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            passwordInput.setError("Vui lòng nhập mật khẩu");
            passwordInput.requestFocus();
            return;
        }

        android.content.SharedPreferences.Editor saved=getSharedPreferences("mylibrary_login",MODE_PRIVATE).edit();
        if(rememberEmail.isChecked())saved.putString("admin_email",email);else saved.remove("admin_email");
        saved.apply();
        setLoading(true);
        final long attempt=++generation;
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if(!foreground || attempt!=generation)return;
                    if (!task.isSuccessful() || auth.getCurrentUser() == null) {
                        setLoading(false);
                        showMessage(AuthErrors.message(task.getException()));
                        return;
                    }

                    resolveProfile();
                });
    }


    protected com.example.libraryonline.data.session.SessionGateway createSessionGateway() {
        return new com.example.libraryonline.data.session.FirebaseSessionGateway(this);
    }
    @Override protected void onStart() {
        super.onStart();foreground=true;
        if (sessions.currentUid()!=null && !busy) { setLoading(true); resolveProfile(); }
    }
    private void resolveProfile() {
        final long attempt=++generation;
        sessions.resolve().addOnCompleteListener(task -> {
            if(!foreground || attempt!=generation || isFinishing() || isDestroyed())return;
            if(task.isSuccessful()) SessionNavigation.open(this,task.getResult());
            else {
                if(AuthErrors.rejectsSession(task.getException())) sessions.signOut();
                setLoading(false);
                showMessage(AuthErrors.message(task.getException()));
            }
        });
    }

    @Override protected void onStop() {
        foreground=false;generation++;setLoading(false);super.onStop();
    }

    private void setLoading(boolean loading) {
        busy=loading;
        backLink.setEnabled(!loading);
        rememberEmail.setEnabled(!loading);

        emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        loginButton.setEnabled(!loading);
        loginButton.setText(loading ? "Đang xác minh..." : (sessions.currentUid()!=null ? "Thử lại phiên đăng nhập" : "Đăng nhập Admin"));
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
