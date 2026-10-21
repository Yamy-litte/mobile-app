package com.example.libraryonline.data;
import android.os.Bundle;
import com.example.libraryonline.R;
import com.example.libraryonline.data.session.*;
import com.example.libraryonline.ui.admin.inventory.InventoryActivity;
import com.example.libraryonline.ui.reader.BookDetailActivity;
import com.example.libraryonline.ui.reader.ReaderMainActivity;
import com.google.android.gms.tasks.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class InventorySessionTest {
    static SessionGateway gateway=new SessionGateway(){
        public String currentUid(){return "reader";}
        public Task<SessionProfile> resolve(){return Tasks.forResult(new SessionProfile("reader",SessionProfile.Role.READER,"Reader","",null,null,false));}
        public AutoCloseable watch(Runnable r){return ()->{};}public void signOut(){}
    };
    public static class Screen extends InventoryActivity {
        @Override protected SessionGateway createSessionGateway(){return gateway;}
        @Override protected void onCreate(Bundle state){setTheme(R.style.Theme_LibraryOnline);super.onCreate(state);}
    }
    @Test public void readerOpeningInventoryIsRedirectedBeforeCatalogueAccess(){
        try(org.robolectric.android.controller.ActivityController<Screen> c=Robolectric.buildActivity(Screen.class).setup()){
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(c.get().isSessionReady());
            assertEquals(ReaderMainActivity.class.getName(),Shadows.shadowOf(c.get()).getNextStartedActivity().getComponent().getClassName());
        }
    }
    public static class Detail extends BookDetailActivity {
        @Override protected SessionGateway createSessionGateway(){return gateway;}
        @Override protected void onCreate(Bundle state){setTheme(R.style.Theme_LibraryOnline);super.onCreate(state);}
    }
    @Test public void missingDetailIdShowsEmptyStateWithoutCloudCall(){
        try(org.robolectric.android.controller.ActivityController<Detail> c=Robolectric.buildActivity(Detail.class).setup()){
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(android.view.View.GONE,c.get().findViewById(R.id.detailContent).getVisibility());
        }
    }
}
