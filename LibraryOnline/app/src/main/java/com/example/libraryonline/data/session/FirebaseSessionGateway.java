package com.example.libraryonline.data.session;

import android.content.Context;
import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.local.dao.UserDao;
import com.example.libraryonline.data.local.dao.AdminDao;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Source;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server-only access checks; SQLite is written only after authorization succeeds. */
public final class FirebaseSessionGateway implements SessionGateway {
    private static final ExecutorService CACHE_IO=Executors.newSingleThreadExecutor();
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final Context context;

    public FirebaseSessionGateway(Context context) {
        this.context=context.getApplicationContext();
        FirebaseApp app=FirebaseApp.initializeApp(this.context);
        if (app==null) throw new IllegalStateException("Firebase chưa được cấu hình. Kiểm tra google-services.json.");
        auth=FirebaseAuth.getInstance(app);
        firestore=FirebaseFirestore.getInstance(app);
    }
    @Override public String currentUid() { FirebaseUser user=auth.getCurrentUser(); return user==null?null:user.getUid(); }
    @Override public void signOut() { auth.signOut(); }
    @Override public Task<SessionProfile> resolve() {
        FirebaseUser user=auth.getCurrentUser();
        if (user==null) return Tasks.forException(new SessionException(SessionException.Reason.SIGNED_OUT,"Vui lòng đăng nhập lại."));
        String uid=user.getUid(); String email=user.getEmail();
        // Refresh on foreground checks so disabled/revoked Auth sessions are not trusted indefinitely.
        return user.getIdToken(true).continueWithTask(token -> {
            if (!token.isSuccessful()) return Tasks.forException(token.getException());
            if (!uid.equals(currentUid())) return signedOut();
            Task<DocumentSnapshot> admin=firestore.collection("Admins").document(uid).get(Source.SERVER);
            Task<DocumentSnapshot> reader=firestore.collection("Users").document(uid).get(Source.SERVER);
            return Tasks.whenAll(admin,reader).continueWithTask(loaded -> {
                if (!admin.isSuccessful()) return Tasks.forException(admin.getException());
                if (!reader.isSuccessful()) return Tasks.forException(reader.getException());
                if (!uid.equals(currentUid())) return signedOut();
                SessionProfile profile=SessionPolicy.resolve(uid,email,admin.getResult().getData(),reader.getResult().getData());
                return Tasks.call(CACHE_IO,() -> {
                    if (!uid.equals(currentUid())) throw new SessionException(SessionException.Reason.SIGNED_OUT,"Phiên đăng nhập đã thay đổi.");
                    if (profile.user!=null) {
                        try (DatabaseHelper helper=new DatabaseHelper(context)) {
                            android.database.sqlite.SQLiteDatabase db=helper.getWritableDatabase();
                            db.beginTransaction();
                            try {
                                new UserDao(db).upsertFromCloud(profile.user);
                                if (profile.admin!=null) {
                                    AdminDao admins=new AdminDao(db);
                                    if (admins.update(profile.admin)==0) admins.insert(profile.admin);
                                }
                                db.setTransactionSuccessful();
                            } finally { db.endTransaction(); }
                        }
                    }
                    if (!uid.equals(currentUid())) throw new SessionException(SessionException.Reason.SIGNED_OUT,"Phiên đăng nhập đã thay đổi.");
                    return profile;
                });
            });
        });
    }
    private static Task<SessionProfile> signedOut() {
        return Tasks.forException(new SessionException(SessionException.Reason.SIGNED_OUT,"Phiên đăng nhập đã thay đổi."));
    }
    @Override public AutoCloseable watch(Runnable revalidate) {
        AtomicBoolean closed=new AtomicBoolean();
        String watchedUid=currentUid();
        FirebaseAuth.AuthStateListener listener=ignored -> { if (!closed.get() && !java.util.Objects.equals(watchedUid,currentUid())) revalidate.run(); };
        auth.addAuthStateListener(listener);
        ListenerRegistration admin=null,reader=null;
        if (watchedUid!=null) {
            admin=firestore.collection("Admins").document(watchedUid).addSnapshotListener((doc,error) -> {
                if (!closed.get() && (error!=null || (doc!=null && !doc.getMetadata().isFromCache()))) revalidate.run();
            });
            reader=firestore.collection("Users").document(watchedUid).addSnapshotListener((doc,error) -> {
                if (!closed.get() && (error!=null || (doc!=null && !doc.getMetadata().isFromCache()))) revalidate.run();
            });
        }
        ListenerRegistration a=admin,r=reader;
        return () -> { if (closed.compareAndSet(false,true)) { auth.removeAuthStateListener(listener); if (a!=null) a.remove(); if (r!=null) r.remove(); } };
    }
}
