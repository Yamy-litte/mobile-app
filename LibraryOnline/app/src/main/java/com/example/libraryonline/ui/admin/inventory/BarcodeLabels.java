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
        print(activity, Collections.singletonList(barcode));
    }

    public static void print(Activity activity, java.util.List<String> values) {
        if (values == null || values.isEmpty())
            throw new IllegalArgumentException("Chưa có barcode để in.");
        final java.util.List<String> barcodes = new java.util.ArrayList<>(values);
        final java.util.List<Bitmap> labels = new java.util.ArrayList<>();
        for (String value : barcodes) labels.add(encode(value));
        final int pageCount = (labels.size() + 4) / 5;
        PrintManager manager = (PrintManager) activity.getSystemService(Activity.PRINT_SERVICE);
        if (manager == null) throw new IllegalStateException("Thiết bị không hỗ trợ in.");
        manager.print(
                "Library Online — nhãn sách",
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
                                        .setPageCount(pageCount)
                                        .build(),
                                true);
                    }

                    @Override
                    public void onWrite(
                            PageRange[] ranges,
                            ParcelFileDescriptor destination,
                            CancellationSignal cancellation,
                            WriteResultCallback callback) {
                        PdfDocument pdf = new PdfDocument();
                        java.util.List<PageRange> written = new java.util.ArrayList<>();
                        try (FileOutputStream output =
                                new FileOutputStream(destination.getFileDescriptor())) {
                            for (int index = 0; index < pageCount; index++) {
                                if (cancellation.isCanceled()) {
                                    callback.onWriteCancelled();
                                    return;
                                }
                                boolean include = false;
                                for (PageRange range : ranges)
                                    if (range.getStart() <= index && range.getEnd() >= index)
                                        include = true;
                                if (!include) continue;
                                PdfDocument.Page page =
                                        pdf.startPage(
                                                new PdfDocument.PageInfo.Builder(
                                                                595, 842, index + 1)
                                                        .create());
                                Canvas canvas = page.getCanvas();
                                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                paint.setColor(Color.BLACK);
                                for (int i = index * 5;
                                        i < Math.min(labels.size(), index * 5 + 5);
                                        i++) {
                                    int top = 28 + (i % 5) * 156;
                                    paint.setTextSize(11);
                                    canvas.drawText("LIBRARY ONLINE", 32, top + 12, paint);
                                    canvas.drawBitmap(
                                            labels.get(i),
                                            null,
                                            new Rect(32, top + 20, 563, top + 110),
                                            null);
                                    paint.setTextSize(9);
                                    canvas.drawText(barcodes.get(i), 32, top + 128, paint);
                                }
                                pdf.finishPage(page);
                                written.add(new PageRange(index, index));
                            }
                            if (!written.isEmpty()) pdf.writeTo(output);
                            callback.onWriteFinished(written.toArray(new PageRange[0]));
                        } catch (Exception error) {
                            android.util.Log.e("BarcodeLabels", "Không thể tạo tài liệu nhãn.", error);
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
