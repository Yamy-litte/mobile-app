package com.example.libraryonline.data;

import static org.junit.Assert.*;

import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.example.libraryonline.R;
import com.example.libraryonline.data.local.dao.CategoryDao;
import com.example.libraryonline.ui.admin.inventory.InventoryForm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class InventoryRedesignTest {
    @Test
    public void editorOffersCoverPreviewAndUrlDraftSurvivesRecreation() {
        AppCompatActivity a = Robolectric.buildActivity(AppCompatActivity.class).get();
        a.setTheme(R.style.Theme_LibraryOnline);
        InventoryForm form =
                new InventoryForm(
                        a,
                        Arrays.asList(new CategoryDao.Category("history", "Lịch sử", true)),
                        null);
        int preview =
                a.getResources().getIdentifier("editorCoverPreview", "id", a.getPackageName());
        assertTrue("Full page editor needs a cover preview control", preview != 0);
        assertNotNull(form.view.findViewById(preview));
        ((EditText) form.view.findViewById(R.id.formCover))
                .setText("https://example.org/cover.jpg");
        InventoryForm restored =
                new InventoryForm(
                        a,
                        Arrays.asList(new CategoryDao.Category("history", "Lịch sử", true)),
                        null);
        restored.restoreDraft(form.saveDraft());
        assertEquals(
                "https://example.org/cover.jpg",
                ((EditText) restored.view.findViewById(R.id.formCover)).getText().toString());
    }

    @Test
    public void catalogueCardsShowCoverAndSeparateEditFromIntake() {
        AppCompatActivity a = Robolectric.buildActivity(AppCompatActivity.class).get();
        a.setTheme(R.style.Theme_LibraryOnline);
        View card = a.getLayoutInflater().inflate(R.layout.item_admin_inventory_book, null);
        for (String name :
                new String[] {"inventoryBookCover", "inventoryBookEdit", "inventoryBookIntake"}) {
            int id = a.getResources().getIdentifier(name, "id", a.getPackageName());
            assertTrue("Missing catalogue control: " + name, id != 0);
            assertNotNull(card.findViewById(id));
        }
    }
}
