package com.example.libraryapp.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.AuthResult;
import com.google.android.gms.tasks.Task;

/**
 * Firebase Authentication wrapper.
 * Passwords are sent only to Firebase Authentication and are never written to SQLite.
 */
public class FirebaseAuthManager {
    private final FirebaseAuth auth;

    public FirebaseAuthManager() {
        auth = FirebaseAuth.getInstance();
    }

    public Task<AuthResult> register(String email, String password) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must contain at least 6 characters.");
        }
        return auth.createUserWithEmailAndPassword(email.trim(), password);
    }

    public Task<AuthResult> login(String email, String password) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        return auth.signInWithEmailAndPassword(email.trim(), password);
    }

    public Task<Void> sendPasswordResetEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required.");
        }
        return auth.sendPasswordResetEmail(email.trim());
    }

    public Task<Void> updatePassword(String newPassword) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("A signed-in user is required to change password.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must contain at least 6 characters.");
        }
        return user.updatePassword(newPassword);
    }

    public void logout() {
        auth.signOut();
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    public String getCurrentEmail() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getEmail();
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }
}
