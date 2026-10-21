package com.example.libraryonline.data;
import com.example.libraryonline.ui.admin.BarcodeLabels;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.Code128Reader;
import android.graphics.Bitmap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class BarcodeLabelsTest {
    @Test public void generatedCode128DecodesToExactBookBarcode() throws Exception {
        String value="BOOK-550E8400-E29B-41D4-A716-446655440000-001";
        Bitmap bitmap=BarcodeLabels.encode(value);int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
        Result result=new Code128Reader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(bitmap.getWidth(),bitmap.getHeight(),pixels))));
        assertEquals(value,result.getText());
    }
}
