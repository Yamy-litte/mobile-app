package com.example.libraryonline.ui.scanner;

import com.example.libraryonline.R;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

/** Single shared scanner screen: all presentation lives in res/layout. */
public final class LibraryBarcodeCaptureActivity extends CaptureActivity {
    @Override
    protected DecoratedBarcodeView initializeContent() {
        setContentView(R.layout.activity_library_barcode_scan);
        return findViewById(R.id.zxing_barcode_scanner);
    }
}
