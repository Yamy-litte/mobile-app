package com.example.libraryonline.session;

import com.example.libraryonline.data.remote.firebase.BookCatalogueSyncManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class CatalogueSyncLifecycleTest {
    @Test public void lateFirebaseCallbackAfterCloseCannotSubmitWorkToShutDownExecutor() throws Exception {
        FirebaseApp app=FirebaseApp.initializeApp(RuntimeEnvironment.getApplication(),new FirebaseOptions.Builder().setApplicationId("1:123:android:test").setApiKey("demo-api-key").setProjectId("demo-library-online").build());
        try {
            BookCatalogueSyncManager sync=new BookCatalogueSyncManager(RuntimeEnvironment.getApplication());
            java.lang.reflect.Method submit=BookCatalogueSyncManager.class.getDeclaredMethod("executeIfOpen",Runnable.class);submit.setAccessible(true);
            sync.close();sync.close();
            java.util.concurrent.atomic.AtomicBoolean ran=new java.util.concurrent.atomic.AtomicBoolean();
            // Equivalent to a Firebase success listener arriving after Activity.onStop.
            submit.invoke(sync,(Runnable)() -> ran.set(true));
            assertFalse(ran.get());
        } finally { app.delete(); }
    }
}
