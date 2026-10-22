package com.example.libraryonline.ui.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryonline.R;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE,qualifiers="w320dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AdminHomeViewTest {
    public static class Screen extends AppCompatActivity {
        @Override protected void onCreate(Bundle state) {
            setTheme(R.style.Theme_LibraryOnline);super.onCreate(state);setContentView(R.layout.activity_admin_main);
        }
    }
    private ActivityController<Screen> controller;
    private Screen screen;
    @Before public void start(){controller=Robolectric.buildActivity(Screen.class).setup();screen=controller.get();}
    @After public void stop(){controller.pause().stop().destroy();}
    @Test public void homeDoesNotDisplayMadeUpCountsBeforeLoading() throws Exception {
        AdminHomeView home=new AdminHomeView(screen,AdminHomeView.HOME);
        assertEquals(View.VISIBLE,screen.findViewById(R.id.adminOverview).getVisibility());
        assertEquals(View.GONE,screen.findViewById(R.id.adminRequests).getVisibility());
        for(int id:new int[]{R.id.textAdminBookCount,R.id.textAdminReaderCount,R.id.textAdminCopyCount,R.id.textAdminRegistrationCount})
            assertEquals("—",((TextView)screen.findViewById(id)).getText().toString());
        View root=screen.findViewById(R.id.adminRoot);
        root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,320,800);
        assertTrue(screen.findViewById(R.id.navAdminAccount).getHeight()>=48);
        assertEquals(320,root.getWidth());
        String output=System.getenv("ADMIN_HOME_PREVIEW");
        if(output!=null) {
            ((TextView)screen.findViewById(R.id.textAdminSession)).setText("Admin Lan");
            writePreview(root,output);
            // Optional visual review: existing screens under the same app theme, day and night.
            for(boolean night:new boolean[]{false,true}) {
                RuntimeEnvironment.setQualifiers("w320dp-h800dp"+(night?"-night":"-notnight")+"-mdpi");
                screen.getTheme().applyStyle(R.style.Theme_LibraryOnline,true);
                int[] layouts={R.layout.activity_admin_main,R.layout.activity_auth_login,R.layout.activity_reader_main};
                String[] names={"admin","login","reader"};
                for(int i=0;i<layouts.length;i++) {
                    screen.setContentView(layouts[i]);
                    View content=((android.view.ViewGroup)screen.findViewById(android.R.id.content)).getChildAt(0);
                    writePreview(content,output.replace(".png","-"+names[i]+(night?"-night":"-day")+".png"));
                }
            }
        }
    }
    private void writePreview(View root,String path) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
        root.layout(0,0,320,800);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(320,800,android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(bitmap));
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(path)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}
    }
    @Test public void shortcutsAndTabsOpenRealPanelsAndKeepSelectionConsistent() {
        AdminHomeView home=new AdminHomeView(screen,AdminHomeView.HOME);
        screen.findViewById(R.id.actionAdminRegistrations).performClick();
        assertEquals(AdminHomeView.REQUESTS,home.selectedTab());assertTrue(screen.findViewById(R.id.navAdminRequests).isSelected());
        assertEquals(View.VISIBLE,screen.findViewById(R.id.adminRequests).getVisibility());
        screen.findViewById(R.id.buttonAdminAccount).performClick();
        assertEquals(AdminHomeView.ACCOUNT,home.selectedTab());assertEquals(View.VISIBLE,screen.findViewById(R.id.buttonAdminLogout).getVisibility());
        assertNotNull(screen.findViewById(R.id.textAdminProfileDob));
        assertNotNull(screen.findViewById(R.id.textAdminProfilePhone));
        assertNotNull(screen.findViewById(R.id.textAdminProfileEmail));
        assertNotNull(screen.findViewById(R.id.buttonAdminEditProfile));
        assertNotNull(screen.findViewById(R.id.buttonAdminChangePassword));
        screen.findViewById(R.id.navAdminHome).performClick();assertEquals(AdminHomeView.HOME,home.selectedTab());
        assertFalse(screen.findViewById(R.id.navAdminAccount).isSelected());
    }
    @Test public void selectedPanelCanBeRestoredAndInvalidStateFallsBackToHome() {
        AdminHomeView home=new AdminHomeView(screen,AdminHomeView.ACCOUNT);
        assertEquals(View.VISIBLE,screen.findViewById(R.id.adminAccount).getVisibility());
        home.show(999);assertEquals(AdminHomeView.HOME,home.selectedTab());
    }
}
