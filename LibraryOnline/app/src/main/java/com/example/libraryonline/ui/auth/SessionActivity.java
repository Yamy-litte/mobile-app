package com.example.libraryonline.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryonline.R;
import com.example.libraryonline.data.session.*;

/** Private screens authorize from the server on every foreground transition. */
public abstract class SessionActivity extends AppCompatActivity {
    private SessionGateway gateway;
    private Exception initializationError;
    private AutoCloseable watcher;
    private View shield;
    private boolean active, ready, navigating;
    private String authorizedUid;
    private long generation;
    private final java.util.List<android.app.Dialog> privateDialogs=new java.util.ArrayList<>();
    protected SessionGateway createSessionGateway() { return new FirebaseSessionGateway(this); }
    protected abstract SessionProfile.Role expectedRole();
    protected boolean allowPasswordChange() { return false; }
    protected abstract void onSessionAuthorized(SessionProfile profile);
    protected void onSessionStopped() {}
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try { gateway=createSessionGateway(); } catch (Exception error) { initializationError=error; }
    }
    @Override protected void onStart() {
        super.onStart(); active=true;
        ViewGroup content=findViewById(android.R.id.content);
        if(shield==null) { shield=getLayoutInflater().inflate(R.layout.view_session_gate,content,false);content.addView(shield); }
        shield.findViewById(R.id.buttonSessionRetry).setOnClickListener(v -> recheckSession());
        shield.findViewById(R.id.buttonSessionLogout).setOnClickListener(v -> logoutSession());
        recheckSession();
        if(gateway!=null && !navigating) watcher=gateway.watch(() -> runOnUiThread(this::recheckSession));
    }
    protected final void recheckSession() {
        if(!active || navigating)return;
        final long request=++generation;
        lockScreen();
        if(gateway==null) { showError(initializationError); return; }
        final String uid=gateway.currentUid();
        if(uid==null) { navigate(LoginActivity.class);return; }
        shield.findViewById(R.id.progressSession).setVisibility(View.VISIBLE);
        shield.findViewById(R.id.buttonSessionRetry).setVisibility(View.GONE);
        ((TextView)shield.findViewById(R.id.textSessionStatus)).setText("Đang xác minh phiên đăng nhập…");
        gateway.resolve().addOnCompleteListener(task -> {
            if(!active || navigating || request!=generation)return;
            if(!uid.equals(gateway.currentUid())) { recheckSession();return; }
            if(!task.isSuccessful()) {
                Exception error=task.getException();
                if(AuthErrors.rejectsSession(error)) {
                    Toast.makeText(this,AuthErrors.message(error),Toast.LENGTH_LONG).show();logoutSession();
                } else showError(error);
                return;
            }
            SessionProfile profile=task.getResult();
            if(profile.role!=expectedRole() || (profile.role==SessionProfile.Role.READER
                    && profile.mustChangePassword!=allowPasswordChange())) {
                navigate(SessionNavigation.target(profile));return;
            }
            authorizedUid=uid;ready=true;setPrivateVisibility(View.VISIBLE);shield.setVisibility(View.GONE);
            onSessionAuthorized(profile);
        });
    }
    protected final android.app.AlertDialog.Builder privateDialog() {
        return new android.app.AlertDialog.Builder(this) {
            @Override public android.app.AlertDialog show() {
                android.app.AlertDialog dialog=create();
                if(isSessionReady()) {privateDialogs.add(dialog);dialog.setOnDismissListener(d -> privateDialogs.remove(dialog));dialog.show();}
                return dialog;
            }
        };
    }
    protected final androidx.appcompat.app.AlertDialog.Builder privateSupportDialog() {
        return new androidx.appcompat.app.AlertDialog.Builder(this) {
            @Override public androidx.appcompat.app.AlertDialog show() {
                androidx.appcompat.app.AlertDialog dialog=create();
                if(isSessionReady()) {privateDialogs.add(dialog);dialog.setOnDismissListener(d -> privateDialogs.remove(dialog));dialog.show();}
                return dialog;
            }
        };
    }
    private void dismissPrivateDialogs() {
        for(android.app.Dialog dialog:new java.util.ArrayList<>(privateDialogs))dialog.dismiss();
        privateDialogs.clear();
    }
    private void lockScreen() {
        dismissPrivateDialogs();
        if(ready) { ready=false;onSessionStopped(); }
        setPrivateVisibility(View.INVISIBLE);
        shield.setVisibility(View.VISIBLE);
    }
    private void setPrivateVisibility(int visibility) {
        ViewGroup content=findViewById(android.R.id.content);
        for(int i=0;i<content.getChildCount();i++)if(content.getChildAt(i)!=shield)content.getChildAt(i).setVisibility(visibility);
    }
    private void showError(Exception error) {
        shield.findViewById(R.id.progressSession).setVisibility(View.GONE);
        shield.findViewById(R.id.buttonSessionRetry).setVisibility(View.VISIBLE);
        ((TextView)shield.findViewById(R.id.textSessionStatus)).setText(AuthErrors.message(error));
    }
    public final boolean isSessionReady() { return active && ready && gateway!=null && authorizedUid.equals(gateway.currentUid()); }
    protected final void logoutSession() {
        if(navigating)return;
        // Invalidate before signOut: Auth listeners may execute synchronously.
        navigating=true;generation++;dismissPrivateDialogs();if(ready){ready=false;onSessionStopped();}if(gateway!=null)gateway.signOut();
        SessionNavigation.open(this,LoginActivity.class);
    }
    private void navigate(Class<? extends android.app.Activity> target) {
        if(navigating)return;navigating=true;generation++;SessionNavigation.open(this,target);
    }
    @Override protected void onStop() {
        active=false;generation++;dismissPrivateDialogs();
        if(watcher!=null) { try {watcher.close();}catch(Exception ignored){} watcher=null; }
        if(ready) {ready=false;onSessionStopped();}
        if(shield!=null) {setPrivateVisibility(View.INVISIBLE);shield.setVisibility(View.VISIBLE);}
        super.onStop();
    }
}
