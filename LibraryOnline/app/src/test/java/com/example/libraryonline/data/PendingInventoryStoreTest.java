package com.example.libraryonline.data;
import android.os.Bundle;
import com.example.libraryonline.data.inventory.StockIntake;
import com.example.libraryonline.ui.admin.inventory.PendingInventoryStore;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class PendingInventoryStoreTest {
    @Test public void pendingIdentitySurvivesNewStoreAndCannotBeOverwrittenByAnotherOperation(){
        PendingInventoryStore store=new PendingInventoryStore(RuntimeEnvironment.getApplication());
        String uid="admin-"+java.util.UUID.randomUUID();Bundle pending=new Bundle();pending.putString("journalId","first");pending.putString("inventoryOwner",uid);
        StockIntake intake=new StockIntake("op",2,"A");pending.putSerializable("inventoryIntake",intake);
        store.save(uid,pending);Bundle restored=new PendingInventoryStore(RuntimeEnvironment.getApplication()).load(uid);
        assertEquals(intake.copyId(0),((StockIntake)restored.getSerializable("inventoryIntake")).copyId(0));
        assertNull(store.load("other"));Bundle competing=new Bundle();competing.putString("journalId","second");
        assertThrows(IllegalStateException.class,()->store.save(uid,competing));
        assertFalse(store.clear(uid,"second"));assertNotNull(store.load(uid));
        assertTrue(store.clear(uid,"first"));assertNull(store.load(uid));
    }
    @Test public void onlyOneScreenCanExecuteAnOperationForTheSameUid(){
        PendingInventoryStore first=new PendingInventoryStore(RuntimeEnvironment.getApplication());PendingInventoryStore second=new PendingInventoryStore(RuntimeEnvironment.getApplication());
        String uid="claim-"+java.util.UUID.randomUUID();assertTrue(first.claim(uid));assertFalse(second.claim(uid));first.release(uid);assertTrue(second.claim(uid));second.release(uid);
    }
}
