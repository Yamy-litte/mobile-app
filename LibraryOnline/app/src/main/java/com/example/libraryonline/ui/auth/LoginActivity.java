package com.example.libraryonline.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.libraryonline.R;
import com.example.libraryonline.utils.InputValidator;
import com.example.libraryonline.work.ReservationExpiryWorker;
import com.example.libraryonline.utils.PasswordVisibilityToggle;
import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.TimeUnit;

public class LoginActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "mylibrary_login";
    private static final String KEY_REMEMBER_EMAIL = "remember_email";

    private FirebaseAuth auth;
    private com.example.libraryonline.data.session.SessionGateway sessions;
    private boolean busy, foreground;
    private long generation;
    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private CheckBox rememberLogin;
    private TextView registerLink;
    private TextView forgotPasswordLink;
    private TextView adminLoginLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scheduleReservationExpiryWork();
        setContentView(R.layout.activity_auth_login);

        auth = FirebaseAuth.getInstance();
        sessions = createSessionGateway();
        emailInput = findViewById(R.id.editEmail);
        passwordInput = findViewById(R.id.editPassword);
        PasswordVisibilityToggle.attach(passwordInput);
        loginButton = findViewById(R.id.buttonLogin);
        rememberLogin = findViewById(R.id.checkRememberLogin);
        registerLink = findViewById(R.id.textRegister);
        forgotPasswordLink = findViewById(R.id.textForgotPassword);
        adminLoginLink = findViewById(R.id.textAdminLogin);

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedEmail = preferences.getString(KEY_REMEMBER_EMAIL, "");
        if (!savedEmail.isEmpty()) {
            emailInput.setText(savedEmail);
            rememberLogin.setChecked(true);
        }

        loginButton.setOnClickListener(v -> login());
        registerLink.setOnClickListener(
                v -> startActivity(new Intent(this, RegisterActivity.class)));
        forgotPasswordLink.setOnClickListener(
                v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
        adminLoginLink.setOnClickListener(
                v -> startActivity(new Intent(this, AdminLoginActivity.class)));
    }


    private void scheduleReservationExpiryWork() {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ReservationExpiryWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
                "reservation-expiry-periodic", ExistingPeriodicWorkPolicy.KEEP, request);
    }

    private void login() {
        if (busy) return;
        if (sessions.currentUid() != null) {
            setLoading(true);
            resolveProfile();
            return;
        }
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

        SharedPreferences.Editor preferences =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        if (rememberLogin.isChecked()) {
            preferences.putString(KEY_REMEMBER_EMAIL, email);
        } else {
            preferences.remove(KEY_REMEMBER_EMAIL);
        }
        preferences.apply();

        setLoading(true);
        final long attempt = ++generation;
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(
                        task -> {
                            if (!foreground || attempt != generation) return;
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

    @Override
    protected void onStart() {
        super.onStart();
        foreground = true;
        if (sessions.currentUid() != null && !busy) {
            setLoading(true);
            resolveProfile();
        }
    }

    private void resolveProfile() {
        final long attempt = ++generation;
        sessions.resolve()
                .addOnCompleteListener(
                        task -> {
                            if (!foreground
                                    || attempt != generation
                                    || isFinishing()
                                    || isDestroyed()) return;
                            if (task.isSuccessful()) SessionNavigation.open(this, task.getResult());
                            else {
                                if (AuthErrors.rejectsSession(task.getException()))
                                    sessions.signOut();
                                setLoading(false);
                                showMessage(AuthErrors.message(task.getException()));
                            }
                        });
    }

    @Override
    protected void onStop() {
        foreground = false;
        generation++;
        setLoading(false);
        super.onStop();
    }

    private void setLoading(boolean loading) {
        busy = loading;
        registerLink.setEnabled(!loading);
        forgotPasswordLink.setEnabled(!loading);
        adminLoginLink.setEnabled(!loading);
        rememberLogin.setEnabled(!loading);

        emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        loginButton.setEnabled(!loading);
        loginButton.setText(
                loading
                        ? "Đang đăng nhập..."
                        : (sessions.currentUid() != null
                                ? "Thử lại phiên đăng nhập"
                                : "Đăng nhập"));
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
