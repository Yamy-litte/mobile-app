package com.example.libraryonline.ui.scanner;

import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.function.Consumer;

/**
 * Shared, single-shot camera scanner. Construct exactly once in Activity.onCreate()
 * before STARTED. Scanning never performs a borrow, return or inventory mutation;
 * it only supplies the decoded value to the requesting screen.
 */
public final class LibraryBarcodeScanner {
    private final AppCompatActivity activity;
    private final ActivityResultLauncher<ScanOptions> launcher;
    private Consumer<String> pending;
    private boolean inProgress;

    public LibraryBarcodeScanner(AppCompatActivity activity) {
        this.activity = activity;
        launcher = activity.registerForActivityResult(new ScanContract(), result -> {
            Consumer<String> callback = pending;
            pending = null;
            inProgress = false;
            if (result == null || result.getContents() == null) return;
            String value = result.getContents().trim();
            if (value.isEmpty()) {
                Toast.makeText(activity, "Barcode rỗng, vui lòng quét lại.",
                        Toast.LENGTH_LONG).show();
            } else if (callback != null && !activity.isFinishing() && !activity.isDestroyed()) {
                callback.accept(value);
            }
        });
    }

    /** Restrict to actual printed Code-128 and common library ISBN/QR formats. */
    public void scan(String prompt, Consumer<String> onDecoded) {
        if (inProgress) return;
        if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Toast.makeText(activity, "Thiết bị không có camera. Vui lòng nhập barcode thủ công.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (onDecoded == null) throw new IllegalArgumentException("Thiếu hàm nhận barcode.");
        pending = onDecoded;
        inProgress = true;
        try {
            launcher.launch(options(prompt));
        } catch (RuntimeException error) {
            inProgress = false;
            pending = null;
            Toast.makeText(activity, "Không mở được camera. Có thể nhập mã thủ công.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Options are testable without opening the camera. */
    static ScanOptions options(String prompt) {
        return new ScanOptions()
                .setCaptureActivity(LibraryBarcodeCaptureActivity.class)
                .setDesiredBarcodeFormats(ScanOptions.CODE_128, ScanOptions.CODE_39,
                        ScanOptions.CODE_93, ScanOptions.EAN_13, ScanOptions.EAN_8,
                        ScanOptions.QR_CODE)
                .setOrientationLocked(true)
                .setBeepEnabled(true)
                .setPrompt(prompt == null ? "Đưa mã vào khung để quét" : prompt);
    }
}
