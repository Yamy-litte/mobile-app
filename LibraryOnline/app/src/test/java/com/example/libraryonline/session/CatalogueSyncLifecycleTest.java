package com.example.libraryonline.session;

import com.example.libraryonline.data.local.db.DatabaseHelper;
import com.example.libraryonline.data.remote.firebase.BookCatalogueSyncManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class CatalogueSyncLifecycleTest {
    @Test
    public void lateFirebaseCallbackAfterCloseCannotSubmitWorkToShutDownExecutor() throws Exception {
        DatabaseHelper databaseHelper = new DatabaseHelper(RuntimeEnvironment.getApplication());
        Constructor<BookCatalogueSyncManager> constructor = BookCatalogueSyncManager.class
                .getDeclaredConstructor(DatabaseHelper.class,
                        com.google.firebase.firestore.FirebaseFirestore.class);
        constructor.setAccessible(true);
        // This test covers the manager's executor lifecycle, not Firebase SDK startup/shutdown.
        BookCatalogueSyncManager sync = constructor.newInstance(databaseHelper, null);
        try {
            Method submit = BookCatalogueSyncManager.class
                    .getDeclaredMethod("executeIfOpen", Runnable.class);
            submit.setAccessible(true);
            sync.close();
            sync.close();

            AtomicBoolean ran = new AtomicBoolean();
            // Equivalent to a Firebase success listener arriving after Activity.onStop.
            submit.invoke(sync, (Runnable) () -> ran.set(true));
            assertFalse(ran.get());
        } finally {
            sync.close();
            Field executorField = BookCatalogueSyncManager.class.getDeclaredField("executor");
            executorField.setAccessible(true);
            ExecutorService executor = (ExecutorService) executorField.get(sync);
            assertTrue("Catalogue executor did not finish closing SQLite",
                    executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
