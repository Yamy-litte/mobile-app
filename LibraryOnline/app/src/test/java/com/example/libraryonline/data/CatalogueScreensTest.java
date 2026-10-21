package com.example.libraryonline.data;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryonline.R;
import com.example.libraryonline.data.model.BookTitle;
import com.example.libraryonline.ui.reader.BookDetailView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE,qualifiers="w320dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CatalogueScreensTest {
    public static class Screen extends AppCompatActivity {protected void onCreate(Bundle s){setTheme(R.style.Theme_LibraryOnline);super.onCreate(s);}}
    @Test public void detailAndInventoryFitNarrowViewportAndUseSharedBackground() throws Exception {
        try(org.robolectric.android.controller.ActivityController<Screen> controller=Robolectric.buildActivity(Screen.class).setup()) {
            Screen activity=controller.get();activity.setContentView(R.layout.activity_reader_book_detail);
            BookTitle book=new BookTitle();book.title="Sapiens: Lược sử loài người";book.author="Yuval Noah Harari";book.category="Lịch sử";book.publisher="Harper";book.publishYear=2015;book.isbn="9780062315007";book.description="Hành trình nhìn lại lịch sử loài người từ thời đồ đá đến thế giới hiện đại.";book.totalCopies=4;book.availableCopies=3;
            BookDetailView detail=new BookDetailView(activity);detail.bind(book);detail.status("Đã nhận cập nhật từ thư viện");
            render(activity.findViewById(R.id.detailRoot),"reader-detail.png");
            assertTrue(activity.findViewById(R.id.detailTitle).getWidth()<=280);
            activity.setContentView(R.layout.activity_admin_inventory);
            activity.findViewById(R.id.inventoryRetry).setVisibility(View.GONE);
            ((TextView)activity.findViewById(R.id.inventoryStatus)).setText("Đã nhận danh mục Firebase");
            LinearLayout content=activity.findViewById(R.id.inventoryContent);
            View row=activity.getLayoutInflater().inflate(R.layout.item_admin_inventory_book,content,false);
            ((TextView)row.findViewById(R.id.inventoryBookTitle)).setText(book.title);
            ((TextView)row.findViewById(R.id.inventoryBookMeta)).setText(book.author+" · "+book.category);
            ((TextView)row.findViewById(R.id.inventoryBookCount)).setText("3 sẵn sàng / 4 đã nhập");content.addView(row);
            render(activity.findViewById(R.id.inventoryRoot),"admin-inventory.png");
            assertTrue(activity.findViewById(R.id.inventoryAdd).getRight()<=288);
        }
    }
    private void render(View root,String name) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,320,800);
        assertEquals(320,root.getWidth());
        String output=System.getenv("FLOW2_PREVIEW_DIR");if(output==null)return;
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(320,800,android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(image));
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(output,name))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}
    }
}
