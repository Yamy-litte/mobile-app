package com.example.libraryonline.data.session;

import com.google.android.gms.tasks.Task;

/** Platform boundary: callers never receive credentials or authorize from a cache. */
public interface SessionGateway {
    String currentUid();
    Task<SessionProfile> resolve();
    AutoCloseable watch(Runnable revalidate);
    void signOut();
}
