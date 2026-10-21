package com.example.libraryonline.data.session;

import com.example.libraryonline.data.model.User;
import com.example.libraryonline.data.model.Admin;

/** Authorization result obtained from server documents for the current Auth UID. */
public final class SessionProfile {
    public enum Role { ADMIN, READER }
    public final String uid, displayName, email;
    public final Role role;
    public final User user;
    public final Admin admin;
    public final boolean mustChangePassword;
    public SessionProfile(String uid, Role role, String displayName, String email,
                          User user, Admin admin, boolean mustChangePassword) {
        this.uid=uid; this.role=role; this.displayName=displayName; this.email=email;
        this.user=user; this.admin=admin; this.mustChangePassword=mustChangePassword;
    }
}
