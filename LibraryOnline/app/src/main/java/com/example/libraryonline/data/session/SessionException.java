package com.example.libraryonline.data.session;

/** A rejected identity/profile, distinct from a transient transport failure. */
public final class SessionException extends RuntimeException {
    public enum Reason { SIGNED_OUT, MISSING_PROFILE, INVALID_PROFILE, BLOCKED, INACTIVE_ADMIN }
    public final Reason reason;
    public SessionException(Reason reason, String message) { super(message); this.reason = reason; }
}
