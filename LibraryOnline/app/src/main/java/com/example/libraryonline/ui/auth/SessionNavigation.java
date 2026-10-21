package com.example.libraryonline.ui.auth;

import android.app.Activity;
import android.content.Intent;

import com.example.libraryonline.data.session.SessionProfile;
import com.example.libraryonline.ui.admin.AdminMainActivity;
import com.example.libraryonline.ui.reader.ReaderMainActivity;

/** Auth transitions replace the task so Back cannot reopen another user's private screen. */
public final class SessionNavigation {
    private SessionNavigation() {}

    public static Class<? extends Activity> target(SessionProfile profile) {
        if (profile.role == SessionProfile.Role.ADMIN) return AdminMainActivity.class;
        return profile.mustChangePassword ? ChangePasswordActivity.class : ReaderMainActivity.class;
    }

    public static void open(Activity activity, Class<? extends Activity> target) {
        activity.startActivity(
                new Intent(activity, target)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity.finish();
    }

    public static void open(Activity activity, SessionProfile profile) {
        open(activity, target(profile));
    }
}
