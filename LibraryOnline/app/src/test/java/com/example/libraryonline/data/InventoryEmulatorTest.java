package com.example.libraryonline.data;

import android.os.Looper;
import com.example.libraryonline.data.inventory.*;
import com.example.libraryonline.data.model.BookTitle;
import com.google.android.gms.tasks.Task;
import com.google.firebase.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real Android Firestore SDK -> local emulators, never a live project. Opt-in via emulator exec. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class InventoryEmulatorTest {
    private FirebaseApp app;private FirebaseFirestore db;private InventoryRepository repo;
    private static final String PROJECT="demo-library-online";
    private <T>T await(Task<T> task) throws Exception {
        long end=System.currentTimeMillis()+30000;
        while(!task.isComplete() && System.currentTimeMillis()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(20);}
        if(!task.isComplete())throw new AssertionError("Emulator task timed out");
        if(!task.isSuccessful())throw task.getException();return task.getResult();
    }
    @Before public void setup() throws Exception {
        Assume.assumeTrue("Run with local Firestore + Auth emulators",System.getenv("FLOW2_ANDROID_EMULATOR_TEST")!=null);
        app=FirebaseApp.initializeApp(RuntimeEnvironment.getApplication(),new FirebaseOptions.Builder().setProjectId(PROJECT).setApplicationId("1:123:android:abc").setApiKey("fake-emulator-only-"+UUID.randomUUID()).build(),"inventory-"+UUID.randomUUID());
        FirebaseAuth auth=FirebaseAuth.getInstance(app);auth.useEmulator("127.0.0.1",9099);
        String uid=await(auth.signInAnonymously()).getUser().getUid();
        URL url=new URL("http://127.0.0.1:8088/v1/projects/"+PROJECT+"/databases/(default)/documents/Admins?documentId="+uid);
        HttpURLConnection connection=(HttpURLConnection)url.openConnection(Proxy.NO_PROXY);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization","Bearer owner");connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);
        connection.getOutputStream().write("{\"fields\":{\"adminStatus\":{\"stringValue\":\"Active\"}}}".getBytes(StandardCharsets.UTF_8));
        if(connection.getResponseCode()!=200)throw new AssertionError("Cannot seed emulator admin: "+connection.getResponseCode());connection.disconnect();
        db=FirebaseFirestore.getInstance(app);db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());db.useEmulator("127.0.0.1",8088);
        Map<String,Object> category=new HashMap<>();category.put("categoryId","flow2");category.put("name","Thể loại kiểm thử");category.put("isActive",true);
        await(db.collection("Categories").document("flow2").set(category));repo=new InventoryRepository(db);
    }
    @After public void close(){if(db!=null)db.terminate();if(app!=null)app.delete();}
    private BookTitle book(){BookTitle b=new BookTitle();b.bookTitleId="TEST-"+UUID.randomUUID();b.title="Emulator inventory";b.author="Test author";b.publisher="Publisher";b.categoryId="flow2";return b;}
    @Test public void actualRepositoryCreateRetryIntakeEditAndRetireAreAtomic() throws Exception {
        BookTitle b=book();StockIntake first=new StockIntake(2,"A1");await(repo.createTitle(b,first));await(repo.createTitle(b,first));
        assertEquals(Long.valueOf(2),await(db.collection("BookTitles").document(b.bookTitleId).get()).getLong("totalCopies"));
        StockIntake extra=new StockIntake(3,"A2");await(repo.addCopies(b.bookTitleId,extra));await(repo.addCopies(b.bookTitleId,extra));
        b.title="Updated metadata";b.totalCopies=0;b.availableCopies=0;await(repo.updateTitle(b));
        await(repo.retireCopy(first.copyId(0)));await(repo.retireCopy(first.copyId(0)));
        DocumentSnapshot parent=await(db.collection("BookTitles").document(b.bookTitleId).get());
        assertEquals(Long.valueOf(5),parent.getLong("totalCopies"));assertEquals(Long.valueOf(4),parent.getLong("availableCopies"));
        assertEquals("ThanhLy",await(db.collection("BookCopies").document(first.copyId(0)).get()).getString("copyStatus"));
        assertEquals(5,await(db.collection("BookCopies").whereEqualTo("bookTitleId",b.bookTitleId).get()).size());
    }
    @Test public void competingIntakesDoNotLoseCountsAndFiveCopiesFitRules() throws Exception {
        BookTitle b=book();StockIntake seed=new StockIntake(1,"A");await(repo.createTitle(b,seed));
        Task<Void> left=repo.addCopies(b.bookTitleId,new StockIntake(5,"B"));
        Task<Void> right=repo.addCopies(b.bookTitleId,new StockIntake(5,"C"));
        int successful=0;
        for(Task<Void> task:Arrays.asList(left,right)) {
            try {await(task);successful++;}catch(Exception rejected){
                Throwable cause=rejected;while(cause.getCause()!=null)cause=cause.getCause();
                assertTrue("Only a stock conflict is acceptable: "+rejected,cause instanceof IllegalArgumentException);
            }
        }
        assertTrue(successful>=1);
        DocumentSnapshot parent=await(db.collection("BookTitles").document(b.bookTitleId).get());
        QuerySnapshot copies=await(db.collection("BookCopies").whereEqualTo("bookTitleId",b.bookTitleId).get());
        assertEquals(1+5*successful,copies.size());assertEquals(Long.valueOf(copies.size()),parent.getLong("totalCopies"));
        Set<String> barcodes=new HashSet<>();for(DocumentSnapshot c:copies)assertTrue(barcodes.add(c.getString("barcode")));
    }
    @Test public void savedOperationWithoutAllocationRecoversAfterCommittedIntake() throws Exception {
        BookTitle b=book();await(repo.createTitle(b,new StockIntake(1,"A")));
        StockIntake original=new StockIntake("recover-"+UUID.randomUUID(),2,"B");
        StockIntake restored=new StockIntake(original.operationId,2,"B");
        await(repo.addCopies(b.bookTitleId,original));await(repo.addCopies(b.bookTitleId,restored));
        assertEquals(Long.valueOf(3),await(db.collection("BookTitles").document(b.bookTitleId).get()).getLong("totalCopies"));
        await(repo.hideCopy(original.copyId(0)));await(repo.hideCopy(original.copyId(0)));
        assertEquals(Long.valueOf(2),await(db.collection("BookTitles").document(b.bookTitleId).get()).getLong("availableCopies"));
    }
    @Test public void completedIntakeIsRecognizedBeforeInactiveCategoryAndRetirementStillWorks() throws Exception {
        BookTitle b=book();StockIntake intake=new StockIntake(2,"A");await(repo.createTitle(b,intake));
        await(db.collection("Categories").document("flow2").update("isActive",false));
        await(repo.addCopies(b.bookTitleId,intake));
        await(repo.retireCopy(intake.copyId(0)));
        assertEquals(Long.valueOf(2),await(db.collection("BookTitles").document(b.bookTitleId).get()).getLong("totalCopies"));
        assertEquals(Long.valueOf(1),await(db.collection("BookTitles").document(b.bookTitleId).get()).getLong("availableCopies"));
    }
    @Test public void reconciliationDistinguishesCommittedAndRejectedIntakeWithoutNewCopies() throws Exception {
        BookTitle b=book();StockIntake done=new StockIntake(1,"A");await(repo.createTitle(b,done));
        assertTrue(await(repo.reconcileIntake(b.bookTitleId,b,done)));
        StockIntake rejected=new StockIntake(1,"B");
        assertFalse(await(repo.reconcileIntake(b.bookTitleId,b,rejected)));
        assertEquals(Long.valueOf(1),await(db.collection("BookTitles").document(b.bookTitleId).get()).getLong("totalCopies"));
        assertEquals(1,await(db.collection("BookCopies").whereEqualTo("bookTitleId",b.bookTitleId).get()).size());
    }
    @Test public void restoredRepairCopyAndTitleKeepInventoryCountersConsistent() throws Exception {
        BookTitle b=book();
        StockIntake intake=new StockIntake(2,"A1");
        await(repo.createTitle(b,intake));
        String copyId=intake.copyId(0);
        await(repo.hideCopy(copyId));
        assertEquals(Long.valueOf(1),
                await(db.collection("BookTitles").document(b.bookTitleId).get()).getLong("availableCopies"));
        await(repo.hideTitle(b.bookTitleId));
        try {
            await(repo.restoreCopy(copyId));
            fail("Cannot restore a copy while its parent title is hidden");
        } catch(IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Hiện lại đầu sách"));
        }
        await(repo.restoreTitle(b.bookTitleId));
        await(repo.restoreTitle(b.bookTitleId));
        await(repo.restoreCopy(copyId));
        await(repo.restoreCopy(copyId));
        DocumentSnapshot title=await(db.collection("BookTitles").document(b.bookTitleId).get());
        DocumentSnapshot restored=await(db.collection("BookCopies").document(copyId).get());
        assertEquals(Long.valueOf(2),title.getLong("availableCopies"));
        assertEquals(Long.valueOf(2),title.getLong("totalCopies"));
        assertEquals(Boolean.FALSE,title.getBoolean("isHidden"));
        assertEquals(Boolean.FALSE,restored.getBoolean("isHidden"));
        assertEquals("SanSang",restored.getString("copyStatus"));
        assertEquals(intake.barcode(0),restored.getString("barcode"));
        await(repo.retireCopy(copyId));
        try {
            await(repo.restoreCopy(copyId));
            fail("Retired copies must never be restored via repair action");
        } catch(IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("thanh lý"));
        }
    }

    private void makeLegacyTitle(String id) throws Exception {
        URL url=new URL("http://127.0.0.1:8088/v1/projects/"+PROJECT+"/databases/(default)/documents:commit");
        HttpURLConnection connection=(HttpURLConnection)url.openConnection(Proxy.NO_PROXY);connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization","Bearer owner");connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);
        String body="{\"writes\":[{\"update\":{\"name\":\"projects/"+PROJECT+"/databases/(default)/documents/BookTitles/"+id+"\",\"fields\":{\"category\":{\"stringValue\":\"Legacy\"}}},\"updateMask\":{\"fieldPaths\":[\"category\"]}}]}";
        connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        assertEquals(200,connection.getResponseCode());connection.disconnect();
    }
    private void settle(com.example.libraryonline.ui.admin.inventory.InventoryViewModel model) throws Exception {
        long end=System.currentTimeMillis()+30000;
        while(model.busy && System.currentTimeMillis()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(20);}
        assertFalse("ViewModel operation timed out",model.busy);
    }
    @Test public void permanentRulesRejectionReconcilesAndUnlocksOtherInventoryWrites() throws Exception {
        BookTitle b=book();await(repo.createTitle(b,new StockIntake(1,"A")));makeLegacyTitle(b.bookTitleId);
        String uid=FirebaseAuth.getInstance(app).getCurrentUser().getUid();
        com.example.libraryonline.ui.admin.inventory.InventoryViewModel model=new com.example.libraryonline.ui.admin.inventory.InventoryViewModel(repo);
        model.attach(RuntimeEnvironment.getApplication(),uid);
        model.submit("intake",b.bookTitleId,b,new StockIntake(1,"B"),null,uid);settle(model);
        assertFalse("Definitively rejected intake must not block all later work",model.hasPending());
        assertEquals(Long.valueOf(1),await(db.collection("BookTitles").document(b.bookTitleId).get()).getLong("totalCopies"));
        BookTitle other=book();model.submit("create",other.bookTitleId,other,new StockIntake(1,"C"),null,uid);settle(model);
        assertFalse(model.hasPending());assertTrue(await(db.collection("BookTitles").document(other.bookTitleId).get()).exists());
    }
    @Test public void successfulIntakePublishesRestorableReceiptUsingActualCopyIds() throws Exception {
        String uid=FirebaseAuth.getInstance(app).getCurrentUser().getUid();
        com.example.libraryonline.ui.admin.inventory.InventoryViewModel model=new com.example.libraryonline.ui.admin.inventory.InventoryViewModel(repo);
        model.attach(RuntimeEnvironment.getApplication(),uid);
        BookTitle title=book();StockIntake intake=new StockIntake(2,"C1");
        model.submit("create",title.bookTitleId,title,intake,null,uid);settle(model);
        assertFalse(model.hasPending());assertNotNull(model.completionId);
        assertEquals(intake.operationId,model.completedIntake.operationId);
        android.os.Bundle saved=new android.os.Bundle();model.save(saved);
        com.example.libraryonline.ui.admin.inventory.InventoryViewModel restored=new com.example.libraryonline.ui.admin.inventory.InventoryViewModel(repo);restored.restore(saved);
        assertEquals(model.completionId,restored.completionId);
        for(int i=0;i<2;i++){
            DocumentSnapshot copy=await(db.collection("BookCopies").document(restored.completedIntake.copyId(i)).get());
            assertTrue(copy.exists());assertEquals(title.bookTitleId,copy.getString("bookTitleId"));
            assertEquals(intake.barcode(i),copy.getString("barcode"));
        }
    }

}
