package com.example.libraryonline.data;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryonline.R;
import com.example.libraryonline.data.model.BookTitle;
import com.example.libraryonline.ui.reader.BookDetailView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class BookDetailTest {
    private AppCompatActivity activity;private BookDetailView view;
    @Before public void setup(){activity=Robolectric.buildActivity(AppCompatActivity.class).get();activity.setTheme(R.style.Theme_LibraryOnline);activity.setContentView(R.layout.activity_reader_book_detail);view=new BookDetailView(activity);}
    @Test public void bindsRealMetadataAndAvailability(){BookTitle b=new BookTitle();b.title="Sapiens";b.author="Harari";b.category="Lịch sử";b.description="Mô tả thật";b.totalCopies=4;b.availableCopies=2;view.bind(b);
        assertEquals("Sapiens",((TextView)activity.findViewById(R.id.detailTitle)).getText().toString());
        assertTrue(((TextView)activity.findViewById(R.id.detailAvailability)).getText().toString().contains("2"));
        assertEquals("Mô tả thật",((TextView)activity.findViewById(R.id.detailDescription)).getText().toString());
    }
    @Test public void missingOrHiddenBookDoesNotKeepOldDetails(){BookTitle b=new BookTitle();b.title="Private";b.isHidden=true;view.bind(b);assertEquals(View.GONE,activity.findViewById(R.id.detailContent).getVisibility());view.bind(null);assertEquals(View.GONE,activity.findViewById(R.id.detailContent).getVisibility());}
    @Test public void errorAfterCachedSuccessKeepsCacheAndRetry(){BookTitle b=new BookTitle();b.title="Cached";view.bind(b);view.error("Mất mạng — dữ liệu đã lưu");assertEquals(View.VISIBLE,activity.findViewById(R.id.detailContent).getVisibility());assertEquals(View.VISIBLE,activity.findViewById(R.id.detailRetry).getVisibility());}
    @Test public void lateCacheArrivalClearsEarlierMissingMessage(){view.bind(null);BookTitle b=new BookTitle();b.title="Loaded";view.bind(b);assertFalse(((TextView)activity.findViewById(R.id.detailStatus)).getText().toString().contains("không tồn tại"));}
}
