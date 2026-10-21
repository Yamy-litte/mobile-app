package com.example.libraryonline.session;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.example.libraryonline.R;
import com.example.libraryonline.data.session.*;
import com.example.libraryonline.ui.auth.SessionActivity;
import com.example.libraryonline.ui.auth.LoginActivity;
import com.example.libraryonline.ui.auth.ChangePasswordActivity;
import com.example.libraryonline.ui.reader.ReaderMainActivity;
import com.google.android.gms.tasks.*;
import com.google.firebase.FirebaseNetworkException;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class SessionGateTest {
    static FakeGateway gateway;
    ActivityController<TestAdminScreen> controller;
    public static class TestAdminScreen extends SessionActivity {
        int privateStarts;
        android.app.AlertDialog openDialog() {return privateDialog().setMessage("private approval").show();}
        @Override protected SessionGateway createSessionGateway() { return gateway; }
        @Override protected SessionProfile.Role expectedRole() { return SessionProfile.Role.ADMIN; }
        @Override protected void onCreate(Bundle state) { setTheme(R.style.Theme_LibraryOnline); super.onCreate(state); TextView v=new TextView(this); v.setText("private-admin"); setContentView(v); }
        @Override protected void onSessionAuthorized(SessionProfile profile) { privateStarts++; }
    }
    static final class FakeGateway implements SessionGateway {
        String uid="reader"; SessionProfile profile=profile(false); Runnable changed;
        final Queue<Task<SessionProfile>> responses=new ArrayDeque<>();
        public String currentUid(){return uid;}
        public Task<SessionProfile> resolve(){return responses.isEmpty()?Tasks.forResult(profile):responses.remove();}
        public AutoCloseable watch(Runnable r){changed=r; return () -> { if(changed==r) changed=null; };}
        public void signOut(){uid=null;if(changed!=null)changed.run();}
    }
    static SessionProfile profile(boolean password) { return new SessionProfile("reader",SessionProfile.Role.READER,"Lan","reader@example.com",null,null,password); }
    @Before public void setUp(){gateway=new FakeGateway();}
    @After public void tearDown(){if(controller!=null)controller.pause().stop().destroy();}
    private TestAdminScreen start(){controller=Robolectric.buildActivity(TestAdminScreen.class).setup(); idle(); return controller.get();}
    private void idle(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
    @Test public void readerCannotStartAnyPrivateAdminWork() {
        TestAdminScreen a=start(); assertEquals(0,a.privateStarts);
        Intent route=Shadows.shadowOf(a).getNextStartedActivity();
        assertEquals(ReaderMainActivity.class.getName(),route.getComponent().getClassName());
        assertTrue((route.getFlags()&Intent.FLAG_ACTIVITY_CLEAR_TASK)!=0);
    }
    @Test public void signedOutSessionRoutesToLoginAndNeverStartsPrivateWork() {
        gateway.uid=null;TestAdminScreen a=start();assertEquals(0,a.privateStarts);
        assertEquals(LoginActivity.class.getName(),Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
    }
    @Test public void firstLoginCannotSkipThePasswordChangeScreen() {
        gateway.profile=profile(true);TestAdminScreen a=start();assertEquals(0,a.privateStarts);
        assertEquals(ChangePasswordActivity.class.getName(),Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
    }
    @Test public void aNetworkFailureOffersRetryWithoutSigningOut() {
        gateway.responses.add(Tasks.forException(new FirebaseNetworkException("offline")));
        TestAdminScreen a=start();assertEquals(0,a.privateStarts);assertEquals("reader",gateway.uid);
        assertEquals(View.VISIBLE,a.findViewById(R.id.buttonSessionRetry).getVisibility());
        assertNotNull(a.findViewById(R.id.textSessionStatus));
    }
    @Test public void aDelayedSuccessCannotReopenAdminAfterLogout() {
        TaskCompletionSource<SessionProfile> pending=new TaskCompletionSource<>();gateway.responses.add(pending.getTask());
        gateway.uid="admin";gateway.profile=new SessionProfile("admin",SessionProfile.Role.ADMIN,"Admin","admin@example.com",null,null,false);
        TestAdminScreen a=start();gateway.signOut();idle();pending.setResult(gateway.profile);idle();
        assertEquals(0,a.privateStarts);
        Intent route=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(LoginActivity.class.getName(),route.getComponent().getClassName());
        assertNull(Shadows.shadowOf(a).getNextStartedActivity());
    }
    @Test public void onlyAnAuthorizedAdminCanSeeThePrivateScreen() {
        gateway.uid="admin";gateway.profile=new SessionProfile("admin",SessionProfile.Role.ADMIN,"Admin","admin@example.com",null,null,false);
        TestAdminScreen a=start();assertEquals(1,a.privateStarts);assertTrue(a.isSessionReady());
        assertEquals(View.GONE,a.findViewById(R.id.sessionGate).getVisibility());
    }

    @Test public void lockedAndMissingProfilesSignOutBeforeAnyPrivateWork() {
        gateway.responses.add(Tasks.forException(new SessionException(SessionException.Reason.BLOCKED,"locked")));
        TestAdminScreen a=start();assertEquals(0,a.privateStarts);assertNull(gateway.uid);
        assertEquals(LoginActivity.class.getName(),Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
    }
    @Test public void backgroundingStopsPrivateAccessAndForegroundChecksAgain() {
        gateway.uid="admin";gateway.profile=new SessionProfile("admin",SessionProfile.Role.ADMIN,"Admin","admin@example.com",null,null,false);
        TestAdminScreen a=start();assertTrue(a.isSessionReady());
        controller.pause().stop();assertFalse(a.isSessionReady());
        gateway.responses.add(Tasks.forException(new FirebaseNetworkException("offline")));
        controller.restart().resume();idle();assertFalse(a.isSessionReady());assertEquals(1,a.privateStarts);
    }

    @Test public void privateDialogsCloseWhenTheSessionNeedsRevalidation() {
        gateway.uid="admin";gateway.profile=new SessionProfile("admin",SessionProfile.Role.ADMIN,"Admin","admin@example.com",null,null,false);
        TestAdminScreen a=start();android.app.AlertDialog dialog=a.openDialog();assertTrue(dialog.isShowing());
        gateway.responses.add(Tasks.forException(new FirebaseNetworkException("offline")));gateway.changed.run();idle();
        assertFalse(dialog.isShowing());assertFalse(a.isSessionReady());
    }
}
