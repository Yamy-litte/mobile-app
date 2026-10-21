package com.example.libraryonline.data;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryonline.R;
import com.example.libraryonline.data.local.dao.CategoryDao;
import com.example.libraryonline.ui.admin.inventory.InventoryForm;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class InventoryUiTest {
    private AppCompatActivity activity;
    @Before public void setup(){activity=Robolectric.buildActivity(AppCompatActivity.class).get();activity.setTheme(R.style.Theme_LibraryOnline);}
    @Test public void noCategoryCannotCreateSyntheticCategory(){InventoryForm form=new InventoryForm(activity,Collections.emptyList(),null);assertThrows(IllegalArgumentException.class,()->form.read("BOOK"));}
    @Test public void categoryUsesIdAndMetadataDoesNotIncludeStockInputs(){InventoryForm f=new InventoryForm(activity,Arrays.asList(new CategoryDao.Category("lich-su","Lịch sử",true)),null);
        ((EditText)f.view.findViewById(R.id.formTitle)).setText("Sapiens");((EditText)f.view.findViewById(R.id.formAuthor)).setText("Harari");((EditText)f.view.findViewById(R.id.formPublisher)).setText("NXB");
        assertEquals("lich-su",f.read("BOOK").categoryId);assertEquals("BOOK",f.read("BOOK").bookTitleId);
        f.setEnabled(false);assertFalse(f.view.findViewById(R.id.formTitle).isEnabled());assertFalse(f.view.findViewById(R.id.formCategory).isEnabled());
    }
    @Test public void invalidUnsubmittedDraftSurvivesFormRecreation(){
        InventoryForm first=new InventoryForm(activity,Arrays.asList(new CategoryDao.Category("cat","Thể loại",true)),null);
        ((EditText)first.view.findViewById(R.id.formTitle)).setText("Đang nhập dở");
        ((EditText)first.view.findViewById(R.id.formYear)).setText("0");
        android.os.Bundle draft=first.saveDraft();
        InventoryForm restored=new InventoryForm(activity,Arrays.asList(new CategoryDao.Category("cat","Thể loại",true)),null);restored.restoreDraft(draft);
        assertEquals("Đang nhập dở",((EditText)restored.view.findViewById(R.id.formTitle)).getText().toString());
        assertEquals("0",((EditText)restored.view.findViewById(R.id.formYear)).getText().toString());
    }
    @Test public void categoryDraftSurvivesWhileCatalogueIsStillLoading(){
        InventoryForm first=new InventoryForm(activity,Arrays.asList(new CategoryDao.Category("cat","Thể loại",true)),null);
        android.os.Bundle saved=first.saveDraft();
        InventoryForm loading=new InventoryForm(activity,Collections.emptyList(),null);loading.restoreDraft(saved);
        InventoryForm ready=new InventoryForm(activity,Arrays.asList(new CategoryDao.Category("cat","Thể loại",true)),null);ready.restoreDraft(loading.saveDraft());
        assertEquals(0,((Spinner)ready.view.findViewById(R.id.formCategory)).getSelectedItemPosition());
    }
}
