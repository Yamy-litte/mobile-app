package com.example.libraryonline.session;

import android.os.Bundle;
import android.widget.Button;
import com.example.libraryonline.R;
import com.example.libraryonline.ui.auth.LoginActivity;
import com.example.libraryonline.data.session.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.FirebaseNetworkException;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class LoginLifecycleTest {
    static SessionGateTest.FakeGateway gateway;
    ActivityController<TestLogin> controller;
    public static class TestLogin extends LoginActivity {
        @Override protected SessionGateway createSessionGateway(){return gateway;}
        @Override protected void onCreate(Bundle state){setTheme(R.style.Theme_LibraryOnline);super.onCreate(state);}
    }
    @Before public void setup(){
        if(FirebaseApp.getApps(RuntimeEnvironment.getApplication()).isEmpty())FirebaseApp.initializeApp(RuntimeEnvironment.getApplication(),new FirebaseOptions.Builder().setApplicationId("1:123:android:test").setApiKey("demo-api-key").setProjectId("demo-library-online").build());
        gateway=new SessionGateTest.FakeGateway();
    }
    @After public void cleanup(){if(controller!=null)controller.pause().stop().destroy();for(FirebaseApp app:FirebaseApp.getApps(RuntimeEnvironment.getApplication()))app.delete();}
    private void idle(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
    @Test public void backgroundDuringRestoreDoesNotLeaveLoginDisabledOrAcceptStaleSuccess(){
        TaskCompletionSource<SessionProfile> old=new TaskCompletionSource<>();gateway.responses.add(old.getTask());
        controller=Robolectric.buildActivity(TestLogin.class).setup();idle();TestLogin a=controller.get();
        assertFalse(((Button)a.findViewById(R.id.buttonLogin)).isEnabled());
        controller.pause().stop();
        gateway.responses.add(Tasks.forException(new FirebaseNetworkException("offline")));
        controller.restart().resume();idle();
        assertTrue(((Button)a.findViewById(R.id.buttonLogin)).isEnabled());
        old.setResult(gateway.profile);idle();assertNull(Shadows.shadowOf(a).getNextStartedActivity());
        // Retrying a restored session must not demand an email/password again.
        a.findViewById(R.id.buttonLogin).performClick();idle();
        assertEquals(com.example.libraryonline.ui.reader.ReaderMainActivity.class.getName(),Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
    }
}
