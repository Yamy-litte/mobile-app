package com.example.libraryonline.session;

import com.example.libraryonline.data.remote.firebase.BookCatalogueSyncManager;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class CatalogueSyncLifecycleTest {
    @Test public void lateFirebaseCallbackAfterCloseCannotSubmitWorkToShutDownExecutor() throws Exception {
        FirebaseApp app=FirebaseApp.initializeApp(RuntimeEnvironment.getApplication(),new FirebaseOptions.Builder().setApplicationId("1:123:android:test").setApiKey("demo-api-key").setProjectId("demo-library-online").build());
        FirebaseFirestore firestore = FirebaseFirestore.getInstance(app);
        BookCatalogueSyncManager sync = null;
        try {
            sync=new BookCatalogueSyncManager(RuntimeEnvironment.getApplication());
            java.lang.reflect.Method submit=BookCatalogueSyncManager.class.getDeclaredMethod("executeIfOpen",Runnable.class);submit.setAccessible(true);
            sync.close();sync.close();
            java.util.concurrent.atomic.AtomicBoolean ran=new java.util.concurrent.atomic.AtomicBoolean();
            // Equivalent to a Firebase success listener arriving after Activity.onStop.
            submit.invoke(sync,(Runnable)() -> ran.set(true));
            assertFalse(ran.get());
        } finally {
            if (sync != null) sync.close();
            // Firestore owns a background AsyncQueue. Stop it before deleting the FirebaseApp
            // so it cannot race later Robolectric tests while closing its SQLite persistence.
            Tasks.await(firestore.terminate());
            app.delete();
        }
    }
}
