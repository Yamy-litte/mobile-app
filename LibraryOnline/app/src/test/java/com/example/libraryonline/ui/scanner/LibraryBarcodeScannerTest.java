package com.example.libraryonline.ui.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import com.google.zxing.client.android.Intents;
import com.journeyapps.barcodescanner.ScanOptions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Configuration tests do not need camera or Firebase. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class LibraryBarcodeScannerTest {
    @Test public void scansPrintedCode128IsbnAndQrUsingInternalCaptureActivity() {
        ScanOptions options = LibraryBarcodeScanner.options("Quét mã thẻ");
        assertEquals(LibraryBarcodeCaptureActivity.class, options.getCaptureActivity());
        Intent intent = options.createScanIntent(RuntimeEnvironment.getApplication());
        String formats = intent.getStringExtra(Intents.Scan.FORMATS);
        assertTrue(formats.contains(ScanOptions.CODE_128));
        assertTrue(formats.contains(ScanOptions.EAN_13));
        assertTrue(formats.contains(ScanOptions.QR_CODE));
        assertEquals("Quét mã thẻ", intent.getStringExtra(Intents.Scan.PROMPT_MESSAGE));
    }
}
