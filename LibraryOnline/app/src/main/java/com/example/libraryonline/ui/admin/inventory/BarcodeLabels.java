package com.example.libraryonline.ui.admin.inventory;

import android.app.Activity;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.os.*;
import android.print.*;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;

import java.io.FileOutputStream;
import java.util.Collections;

/** Code 128 encoding is delegated to ZXing. Printing uses the Android system PDF/print flow. */
public final class BarcodeLabels {
    private BarcodeLabels() {}

    public static Bitmap encode(String value) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("Thiếu barcode.");
        BitMatrix matrix =
                new Code128Writer()
                        .encode(
                                value,
                                BarcodeFormat.CODE_128,
                                1200,
                                240,
                                Collections.singletonMap(EncodeHintType.MARGIN, 24));
        Bitmap result =
                Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.ARGB_8888);
        int[] pixels = new int[matrix.getWidth() * matrix.getHeight()];
        for (int y = 0; y < matrix.getHeight(); y++)
            for (int x = 0; x < matrix.getWidth(); x++)
                pixels[y * matrix.getWidth() + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
        result.setPixels(pixels, 0, matrix.getWidth(), 0, 0, matrix.getWidth(), matrix.getHeight());
        return result;
    }

    public static void print(Activity activity, String barcode) {
        Bitmap label = encode(barcode);
        PrintManager manager = (PrintManager) activity.getSystemService(Activity.PRINT_SERVICE);
        if (manager == null) throw new IllegalStateException("Thiết bị không hỗ trợ in.");
        manager.print(
                "Library Online — " + barcode,
                new PrintDocumentAdapter() {
                    @Override
                    public void onLayout(
                            PrintAttributes oldAttributes,
                            PrintAttributes newAttributes,
                            CancellationSignal cancellation,
                            LayoutResultCallback callback,
                            Bundle extras) {
                        if (cancellation.isCanceled()) {
                            callback.onLayoutCancelled();
                            return;
                        }
                        callback.onLayoutFinished(
                                new PrintDocumentInfo.Builder("nhan-sach.pdf")
                                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                        .setPageCount(1)
                                        .build(),
                                true);
                    }

                    @Override
                    public void onWrite(
                            PageRange[] pages,
                            ParcelFileDescriptor destination,
                            CancellationSignal cancellation,
                            WriteResultCallback callback) {
                        if (cancellation.isCanceled()) {
                            callback.onWriteCancelled();
                            return;
                        }
                        boolean include = false;
                        for (PageRange range : pages)
                            if (range.getStart() <= 0 && range.getEnd() >= 0) include = true;
                        if (!include) {
                            callback.onWriteFinished(new PageRange[0]);
                            return;
                        }
                        PdfDocument pdf = new PdfDocument();
                        try (FileOutputStream output =
                                new FileOutputStream(destination.getFileDescriptor())) {
                            PdfDocument.Page page =
                                    pdf.startPage(
                                            new PdfDocument.PageInfo.Builder(595, 842, 1).create());
                            Canvas canvas = page.getCanvas();
                            Paint paint = new Paint();
                            paint.setColor(Color.BLACK);
                            paint.setTextSize(12);
                            canvas.drawText("LIBRARY ONLINE", 32, 44, paint);
                            canvas.drawBitmap(label, null, new Rect(32, 60, 563, 166), null);
                            paint.setTextSize(10);
                            canvas.drawText(barcode, 32, 188, paint);
                            pdf.finishPage(page);
                            pdf.writeTo(output);
                            callback.onWriteFinished(new PageRange[] {new PageRange(0, 0)});
                        } catch (Exception error) {
                            callback.onWriteFailed("Không lưu được nhãn mã vạch.");
                        } finally {
                            pdf.close();
                        }
                    }
                },
                new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                        .build());
    }
}
