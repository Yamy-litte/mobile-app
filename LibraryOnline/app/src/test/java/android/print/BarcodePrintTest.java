package android.print;

// Framework callback constructors are package-private; keep this adapter test in their package.

import static org.junit.Assert.*;
import android.os.*;
import android.print.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.libraryonline.R;
import com.example.libraryonline.ui.admin.inventory.BarcodeLabels;
import java.io.File;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE,shadows={BarcodePrintTest.CapturePrintManager.class,BarcodePrintTest.RecordingPdf.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BarcodePrintTest {
    @Implements(PrintManager.class)
    public static class CapturePrintManager {
        static PrintDocumentAdapter adapter;
        @Implementation protected PrintJob print(String name,PrintDocumentAdapter document,PrintAttributes attributes){adapter=document;return null;}
    }
    // Robolectric has no PdfDocument native implementation: record real Canvas drawing,
    // rather than pretending this JVM test writes an Android PDF file.
    @org.robolectric.annotation.internal.DoNotInstrument
    @Implements(android.graphics.pdf.PdfDocument.class)
    public static class RecordingPdf {
        static final List<android.graphics.Bitmap> pages=new ArrayList<>();
        static boolean written,closed;
        @Implementation protected void __constructor__(){pages.clear();written=false;closed=false;}
        @Implementation protected android.graphics.pdf.PdfDocument.Page startPage(android.graphics.pdf.PdfDocument.PageInfo info){
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(info.getPageWidth(),info.getPageHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(android.graphics.Color.WHITE);pages.add(bitmap);
            return org.robolectric.util.ReflectionHelpers.callConstructor(android.graphics.pdf.PdfDocument.Page.class,
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.graphics.Canvas.class,new android.graphics.Canvas(bitmap)),
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.graphics.pdf.PdfDocument.PageInfo.class,info));
        }
        @Implementation protected void finishPage(android.graphics.pdf.PdfDocument.Page page){}
        @Implementation protected void writeTo(java.io.OutputStream output){written=true;}
        @Implementation protected void close(){closed=true;}
    }
    @Test public void batchPrintHonorsRequestedPagesAndRendersExpectedBarcode() throws Exception {
        try(var controller=Robolectric.buildActivity(AppCompatActivity.class).setup()) {
            AppCompatActivity activity=controller.get();activity.setTheme(R.style.Theme_LibraryOnline);
            BarcodeLabels.print(activity,Arrays.asList("BOOK-001","BOOK-002","BOOK-003","BOOK-004","BOOK-005","BOOK-006"));
            PrintDocumentAdapter adapter=CapturePrintManager.adapter;assertNotNull(adapter);
            final int[] pageCount={0};
            adapter.onLayout(null,new PrintAttributes.Builder().build(),new CancellationSignal(),new PrintDocumentAdapter.LayoutResultCallback(){
                @Override public void onLayoutFinished(PrintDocumentInfo info,boolean changed){pageCount[0]=info.getPageCount();}
            },new Bundle());
            assertEquals(2,pageCount[0]);
            File file=File.createTempFile("inventory-labels-",".pdf");
            final List<PageRange> written=new ArrayList<>();final List<CharSequence> errors=new ArrayList<>();
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_WRITE)){
                adapter.onWrite(new PageRange[]{new PageRange(1,1)},fd,new CancellationSignal(),new PrintDocumentAdapter.WriteResultCallback(){
                    @Override public void onWriteFinished(PageRange[] pages){written.addAll(Arrays.asList(pages));}
                    @Override public void onWriteFailed(CharSequence error){errors.add(error);for(var entry:org.robolectric.shadows.ShadowLog.getLogsForTag("BarcodeLabels"))if(entry.throwable!=null)throw new AssertionError(entry.throwable);}
                });
            }
            assertTrue(errors.toString(),errors.isEmpty());assertEquals(1,written.size());assertEquals(1,written.get(0).getStart());
            assertTrue(RecordingPdf.written);assertTrue(RecordingPdf.closed);assertEquals(1,RecordingPdf.pages.size());
            android.graphics.Bitmap barcode=android.graphics.Bitmap.createBitmap(RecordingPdf.pages.get(0),32,48,531,90);
            int[] pixels=new int[barcode.getWidth()*barcode.getHeight()];barcode.getPixels(pixels,0,barcode.getWidth(),0,0,barcode.getWidth(),barcode.getHeight());
            com.google.zxing.Result decoded=new com.google.zxing.oned.Code128Reader().decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(barcode.getWidth(),barcode.getHeight(),pixels))));
            assertEquals("BOOK-006",decoded.getText());
            assertTrue(file.delete());
        }
    }
}
